package com.mmalyil.smartdocai

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File

/**
 * Standalone helper to convert an image URI -> Excel file (XLSX) using OCR.
 * No AI involved.
 */
object ImageToExcelProcessor {

    private data class OcrCell(
        val text: String,
        val x: Int,
        val y: Int,
        val w: Int,
        val h: Int
    )

    // Column band across X axis
    private data class ColBand(
        var minX: Int,
        var maxX: Int
    )

    /**
     * Main entry: pass a Context + Uri of the image, get back an XLSX file
     * saved under externalFilesDir/exports.
     */
    suspend fun processImageUriToExcel(
        context: Context,
        uri: Uri
    ): File = withContext(Dispatchers.IO) {
        // 0) Decode bitmap ourselves so we can pre-process
        val original = context.contentResolver.openInputStream(uri)?.use { input ->
            android.graphics.BitmapFactory.decodeStream(input)
        } ?: throw IllegalStateException("Unable to decode image")

        // Ensure a minimum width (e.g. 1200px) so small images get upscaled
        val prepped = ensureMinSizeAndContrast(original, minWidth = 1200)

        // 1) Run ML Kit OCR on the preprocessed bitmap
        val image = InputImage.fromBitmap(prepped, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val result = recognizer.process(image).await()

        // ... (rest of your existing code: build cells, groupElementsIntoTable, write XLSX)


        val cells = mutableListOf<OcrCell>()
        result.textBlocks.forEach { block ->
            block.lines.forEach { line ->
                line.elements.forEach { el ->
                    val box = el.boundingBox
                    if (box != null) {
                        cells += OcrCell(
                            text = el.text ?: "",
                            x = box.left,
                            y = box.top,
                            w = box.width(),
                            h = box.height()
                        )
                    }
                }
            }
        }

        val rows = groupElementsIntoTable(cells)

// Clean-up: merge split decimals like "7,876." + "75"
        val cleanedRows = rows.map { mergeSplitDecimals(it.toMutableList()) }

        if (cleanedRows.isEmpty() || cleanedRows.all { it.isEmpty() }) {
            throw IllegalStateException("No table-like structure detected in image")
        }

        // 2) Write rows -> XLSX using Apache POI
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Extracted Table")

        cleanedRows.forEachIndexed { rIdx, row ->
            val r = sheet.createRow(rIdx)
            row.forEachIndexed { cIdx, value ->
                r.createCell(cIdx).setCellValue(value)
            }
        }

        val maxCols = cleanedRows.maxOfOrNull { it.size } ?: 0
        repeat(kotlin.math.min(maxCols, 20)) { col ->
            runCatching { sheet.autoSizeColumn(col) }
        }

        val outDir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val file = File(outDir, "SmartDocAI_ImageTable_${System.currentTimeMillis()}.xlsx")

        file.outputStream().use { workbook.write(it) }
        workbook.close()

        file
    }

    private fun mergeSplitDecimals(row: MutableList<String>): List<String> {
        var i = 0
        while (i < row.size - 1) {
            val left  = row[i].trim()
            val right = row[i + 1].trim()

            if (looksLikeIntegerWithTrailingDot(left) && looksLikeShortDecimalTail(right)) {
                // e.g. "7,876." + "75" -> "7,876.75"
                val merged = left.removeSuffix(".") + "." + right
                row[i] = merged
                row[i + 1] = ""   // leave an empty cell instead of the tail
            }

            i++
        }
        return row
    }

    private fun looksLikeIntegerWithTrailingDot(s: String): Boolean {
        // 1,234.   987.   12,45,678. (we’re generous with commas)
        if (!s.endsWith(".")) return false
        val core = s.dropLast(1)
        if (core.isEmpty()) return false
        // Digits and commas only
        if (!core.matches(Regex("^[0-9,]+$"))) return false
        // At least one digit
        return core.any { it.isDigit() }
    }

    private fun looksLikeShortDecimalTail(s: String): Boolean {
        // 1–3 digits only (covers .5, .50, .750 etc.)
        return s.matches(Regex("^[0-9]{1,3}$"))
    }



    private fun ensureMinSizeAndContrast(
        src: android.graphics.Bitmap,
        minWidth: Int
    ): android.graphics.Bitmap {
        // Scale up if needed
        val scale = if (src.width < minWidth) {
            minWidth.toFloat() / src.width
        } else 1f

        val scaled = if (scale > 1f) {
            val newW = (src.width * scale).toInt()
            val newH = (src.height * scale).toInt()
            android.graphics.Bitmap.createScaledBitmap(src, newW, newH, true)
        } else {
            src
        }

        // Optional: slight contrast boost to help OCR
        val cm = android.graphics.ColorMatrix().apply {
            // contrast 1.2f, brightness 0f
            val c = 1.2f
            set(floatArrayOf(
                c, 0f, 0f, 0f, 0f,
                0f, c, 0f, 0f, 0f,
                0f, 0f, c, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
        }

        val out = android.graphics.Bitmap.createBitmap(
            scaled.width, scaled.height,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(out)
        val paint = android.graphics.Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        return out
    }

    /**
     * Group OCR cells into a table with globally aligned columns.
     * 1) Group words into rows by Y (using dynamic tolerance)
     * 2) Build smart column "bands" from all X centers
     * 3) For each row, snap each cell into nearest column band
     */
    private fun groupElementsIntoTable(
        elements: List<OcrCell>
    ): List<List<String>> {
        if (elements.isEmpty()) return emptyList()

        // --- 1) Group OCR cells into logical rows (Y-based) ---
        data class OcrRow(val y: Int, val cells: MutableList<OcrCell> = mutableListOf())

        val avgHeight = elements.map { it.h }.average().takeIf { it > 0 } ?: 20.0
        val rowTolerance = kotlin.math.max((avgHeight * 0.6).toInt(), 10)

        val rowList = mutableListOf<OcrRow>()
        elements.sortedBy { it.y }.forEach { cell ->
            val row = rowList.find { kotlin.math.abs(it.y - cell.y) <= rowTolerance }
                ?: OcrRow(cell.y).also { rowList += it }
            row.cells += cell
        }
        if (rowList.isEmpty()) return emptyList()

        // --- 2) LOCAL helpers: header detection & header-based bands ---

        // Strong header keywords (your broker tables + generic)
        val anchorKeywords = listOf(
            "contract", "transaction", "date", "source", "exchange",
            "buy", "sell", "qty", "quantity", "price", "amount",
            "brokerage", "charges", "gst", "stt", "summary"
        )

        fun scoreHeaderRow(idx: Int, row: OcrRow): Int {
            if (row.cells.size < 4) return 0   // too few columns for a real table
            val text = row.cells.sortedBy { it.x }
                .joinToString(" ") { it.text.trim().lowercase() }

            if (text.length < 5) return 0

            val digits = text.count { it.isDigit() }
            val letters = text.count { it.isLetter() }
            val digitRatio = if (letters + digits == 0) 1.0 else digits.toDouble() / (letters + digits)

            val hitAnchors = anchorKeywords.count { it in text }

            var score = 0
            if (hitAnchors >= 1) score += 3
            if (hitAnchors >= 3) score += 3
            if (digitRatio < 0.4) score += 2            // mostly letters → header-ish
            if (idx <= 15) score += 2                   // near top of page
            if (row.cells.size >= 8) score += 2         // wide row = lots of columns

            return score
        }

        fun findHeaderRowIndex(rows: List<OcrRow>): Int? {
            var bestIdx: Int? = null
            var bestScore = 0
            rows.forEachIndexed { idx, row ->
                val score = scoreHeaderRow(idx, row)
                if (score > bestScore) {
                    bestScore = score
                    bestIdx = idx
                }
            }
            // Only accept header if it’s “strong enough”
            return if (bestScore >= 6) bestIdx else null
        }

        fun buildHeaderBandsFromHeaders(headerCells: List<OcrCell>): List<ColBand> {
            if (headerCells.isEmpty()) return emptyList()
            val centers = headerCells.map { it.x + it.w / 2 }.sorted()
            if (centers.size <= 1) {
                val c = centers.first()
                return listOf(ColBand(c, c))
            }

            val gaps = centers.zipWithNext { a, b -> b - a }.filter { it > 0 }
            val medianGap = gaps.medianInt().coerceAtLeast(20)
            // small threshold to MERGE fragments of one header label,
            // but keep neighbouring columns separated
            val mergeThreshold = (medianGap * 0.6).toInt().coerceAtLeast(25)

            val bands = mutableListOf<ColBand>()
            var current = ColBand(centers.first(), centers.first())

            for (i in 1 until centers.size) {
                val c = centers[i]
                val gap = c - centers[i - 1]
                if (gap <= mergeThreshold) {
                    current.minX = minOf(current.minX, c)
                    current.maxX = maxOf(current.maxX, c)
                } else {
                    bands += current
                    current = ColBand(c, c)
                }
            }
            bands += current
            return bands
        }

        // --- 3) Detect the first REAL header row (table start) ---
        val headerIndex: Int? = findHeaderRowIndex(rowList)

        // --- 4) Build global column bands ---
        val bands: List<ColBand> = if (headerIndex != null) {
            val headerCells = rowList[headerIndex].cells
            val hb = buildHeaderBandsFromHeaders(headerCells)
            if (hb.size >= 3) hb else buildSmartColumnBands(elements)
        } else {
            // No clear header → old behaviour
            buildSmartColumnBands(elements)
        }

        if (bands.isEmpty() || bands.size == 1) {
            // Fallback: each row is single concatenated cell
            return rowList.sortedBy { it.y }.map { row ->
                listOf(row.cells.sortedBy { it.x }.joinToString(" ") { it.text.trim() })
            }
        }

        val numCols = bands.size

        // Snap a single row into nearest bands
        fun snapRow(row: OcrRow): List<String> {
            val buckets = Array(numCols) { mutableListOf<OcrCell>() }

            row.cells.forEach { cell ->
                val centerX = cell.x + cell.w / 2
                var bestIdx = 0
                var bestDist = Int.MAX_VALUE
                bands.forEachIndexed { idx, band ->
                    val bandCenter = (band.minX + band.maxX) / 2
                    val dist = kotlin.math.abs(centerX - bandCenter)
                    if (dist < bestDist) {
                        bestDist = dist
                        bestIdx = idx
                    }
                }
                buckets[bestIdx] += cell
            }

            return List(numCols) { col ->
                buckets[col]
                    .sortedBy { it.x }
                    .joinToString(" ") { it.text.trim() }
            }
        }

        // --- 5) Build final table: skip metadata above header, keep header + data ---
        val startIdx = headerIndex ?: 0
        val table = mutableListOf<List<String>>()

        for (i in startIdx until rowList.size) {
            table += snapRow(rowList[i])
        }

        return table
    }
    // --- helpers ---

    private fun List<Int>.medianInt(): Int {
        if (isEmpty()) return 0
        val sorted = this.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            ((sorted[mid - 1] + sorted[mid]) / 2.0).toInt()
        } else {
            sorted[mid]
        }
    }



    /**
     * Build smarter global column bands:
     * - merge all nearby TEXT columns into one wide band
     * - keep NUMERIC columns separate
     */
    private fun buildSmartColumnBands(cells: List<OcrCell>): List<ColBand> {
        if (cells.isEmpty()) return emptyList()

        val centers = cells.map { it.x + it.w / 2 }.sorted()
        if (centers.size <= 1) return listOf(ColBand(centers.first(), centers.first()))

        // 1. raw bands based on a simple gap threshold
        val gaps = centers.zipWithNext { a, b -> b - a }
        val medianGap = gaps.filter { it > 0 }.medianInt().coerceAtLeast(20)
        val gapThreshold = (medianGap * 1.2).toInt().coerceAtLeast(40)

        val rawBands = mutableListOf<ColBand>()
        var current = ColBand(centers.first(), centers.first())

        for (i in 1 until centers.size) {
            val c = centers[i]
            val gap = c - centers[i - 1]
            if (gap > gapThreshold) {
                rawBands += current
                current = ColBand(c, c)
            } else {
                current.minX = minOf(current.minX, c)
                current.maxX = maxOf(current.maxX, c)
            }
        }
        rawBands += current

        // 2. merge TEXT-heavy bands → big semantic columns
        val merged = mutableListOf<ColBand>()
        var active = rawBands.first()

        fun isNumericBand(band: ColBand): Boolean {
            // If most cells in this X-range are digits, treat as numeric
            val bandCells = cells.filter {
                val center = it.x + it.w / 2
                center >= band.minX && center <= band.maxX
            }
            val numericCount = bandCells.count {
                it.text.trim().matches(Regex("^[-+]?([0-9]*\\.[0-9]+|[0-9]+)$"))
            }
            return bandCells.isNotEmpty() &&
                    numericCount >= (bandCells.size * 0.6)
        }

        for (i in 1 until rawBands.size) {
            val next = rawBands[i]

            val activeIsNum = isNumericBand(active)
            val nextIsNum = isNumericBand(next)

            val gap = next.minX - active.maxX

            if (!activeIsNum && !nextIsNum && gap < 240) {
                // MERGE TEXT COLUMNS (like Vendor / Description words)
                active.maxX = next.maxX
            } else {
                merged += active
                active = next
            }
        }
        merged += active

        return merged
    }




}