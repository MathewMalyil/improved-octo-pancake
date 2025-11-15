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

    /**
     * Main entry: pass a Context + Uri of the image, get back an XLSX file
     * saved under externalFilesDir/exports.
     */
    suspend fun processImageUriToExcel(
        context: Context,
        uri: Uri
    ): File = withContext(Dispatchers.IO) {
        // 1) Run ML Kit OCR on the image
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val result = recognizer.process(image).await()

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

        if (rows.isEmpty() || rows.all { it.isEmpty() }) {
            throw IllegalStateException("No table-like structure detected in image")
        }

        // 2) Write rows -> XLSX using Apache POI
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Extracted Table")

        rows.forEachIndexed { rIdx, row ->
            val r = sheet.createRow(rIdx)
            row.forEachIndexed { cIdx, value ->
                r.createCell(cIdx).setCellValue(value)
            }
        }

        val maxCols = rows.maxOfOrNull { it.size } ?: 0
        repeat(kotlin.math.min(maxCols, 20)) { col ->
            runCatching { sheet.autoSizeColumn(col) }
        }

        val outDir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val file = File(outDir, "SmartDocAI_ImageTable_${System.currentTimeMillis()}.xlsx")

        file.outputStream().use { workbook.write(it) }
        workbook.close()

        file
    }

    /**
     * Simple y-based grouping into rows, then x-based ordering into columns.
     */
    /**
     * Group OCR cells into a table with globally aligned columns.
     * 1) Group words into rows by Y (using dynamic tolerance)
     * 2) Build column "bands" from all X centers
     * 3) For each row, snap each cell into nearest column band
     */
    private fun groupElementsIntoTable(
        elements: List<OcrCell>
    ): List<List<String>> {
        if (elements.isEmpty()) return emptyList()

        // --- 1) Group into logical rows (Y-based) ---
        data class OcrRow(val y: Int, val cells: MutableList<OcrCell> = mutableListOf())

        val avgHeight = elements.map { it.h }.average().takeIf { it > 0 } ?: 20.0
        val rowTolerance = kotlin.math.max((avgHeight * 0.6).toInt(), 10) // dynamic

        val rowList = mutableListOf<OcrRow>()
        elements.sortedBy { it.y }.forEach { cell ->
            val row = rowList.find { kotlin.math.abs(it.y - cell.y) <= rowTolerance }
                ?: OcrRow(cell.y).also { rowList += it }
            row.cells += cell
        }

        // If we somehow got no rows, bail out
        if (rowList.isEmpty()) return emptyList()

        // --- 2) Build global column bands from X centers ---
        val centers = elements.map { it.x + it.w / 2 }.sorted()
        if (centers.size <= 1) {
            // Only one "column", just one cell per row
            return rowList.sortedBy { it.y }.map { row ->
                listOf(row.cells.sortedBy { it.x }.joinToString(" ") { it.text.trim() })
            }
        }

        // Compute gaps between consecutive centers
        val gaps = centers.zipWithNext { a, b -> b - a }.filter { it > 0 }
        val medianGap = gaps.medianInt().takeIf { it > 0 } ?: gaps.average().toInt().coerceAtLeast(10)
        val gapThreshold = (medianGap * 1.5).toInt().coerceAtLeast(medianGap + 2)

        // Build bands: whenever gap > threshold, start a new column
        data class ColBand(var minX: Int, var maxX: Int)
        val bands = mutableListOf<ColBand>()

        var currentBand = ColBand(
            minX = centers.first(),
            maxX = centers.first()
        )
        for (i in 1 until centers.size) {
            val c = centers[i]
            val gap = c - centers[i - 1]
            if (gap > gapThreshold) {
                bands += currentBand
                currentBand = ColBand(minX = c, maxX = c)
            } else {
                if (c < currentBand.minX) currentBand.minX = c
                if (c > currentBand.maxX) currentBand.maxX = c
            }
        }
        bands += currentBand

        if (bands.size == 1) {
            // Only one column band -> treat each row as a single cell with concatenated text
            return rowList.sortedBy { it.y }.map { row ->
                listOf(row.cells.sortedBy { it.x }.joinToString(" ") { it.text.trim() })
            }
        }

        // --- 3) For each row, snap cells into nearest band ---
        val numCols = bands.size
        val table = mutableListOf<List<String>>()

        rowList.sortedBy { it.y }.forEach { row ->
            val rowCells = MutableList(numCols) { "" }

            row.cells.forEach { cell ->
                val centerX = cell.x + cell.w / 2
                // Find nearest band
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

                // Append text into that column (keep multiple words together)
                val existing = rowCells[bestIdx]
                rowCells[bestIdx] = if (existing.isBlank()) {
                    cell.text.trim()
                } else {
                    "$existing ${cell.text.trim()}"
                }
            }

            table += rowCells
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
}