package com.mmalyil.smartdocai.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Locale

// DOCX
import org.apache.poi.xwpf.usermodel.XWPFDocument

// PPTX
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFShape
import org.apache.poi.xslf.usermodel.XSLFTextShape

// XLSX
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet

object DocumentUtils {

    private const val TAG = "SmartDocAI-I/O"

    private const val MAX_PPTX_SIZE_BYTES = 30L * 1024 * 1024 // 30 MB cap

    // ------------------------------------------------------------------ DOCX
    fun extractTextFromDocx(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                XWPFDocument(input).use { doc ->
                    buildString {
                        for (p in doc.paragraphs) {
                            val t = (p.text ?: "").trim()
                            if (t.isNotEmpty()) {
                                append(t)
                                append('\n')
                            }
                        }
                    }
                }
            } ?: "Failed to read DOCX: null input stream"
        } catch (e: Exception) {
            Log.e(TAG, "DOCX error", e)
            "Failed to read DOCX: ${e.message}"
        }
    }

    // ------------------------------------------------------------------ PPTX


    fun extractTextFromPptx(context: Context, uri: Uri): String {
        return try {
            val resolver = context.contentResolver

            // 1) Optional size cap to avoid OOM on giant PPTX with images
            var sizeBytes = -1L
            if ("content".equals(uri.scheme, ignoreCase = true)) {
                try {
                    resolver.query(
                        uri,
                        arrayOf(OpenableColumns.SIZE),
                        null,
                        null,
                        null
                    )?.use { c ->
                        val idx = c.getColumnIndex(OpenableColumns.SIZE)
                        if (idx >= 0 && c.moveToFirst()) {
                            sizeBytes = c.getLong(idx)
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Could not read PPTX size for uri=$uri", t)
                }
            }

            if (sizeBytes > 0 && sizeBytes > MAX_PPTX_SIZE_BYTES) {
                val mb = sizeBytes / (1024 * 1024)
                return "Failed to read PPTX: file is too large (${mb} MB)."
            }

            // 2) Open & parse with POI, catching any diagram/graphic weirdness per-slide
            resolver.openInputStream(uri)?.use { input ->
                XMLSlideShow(input).use { ppt ->
                    buildString {
                        for (slide in ppt.slides) {
                            try {
                                for (shape in slide.shapes) {
                                    if (shape is XSLFTextShape) {
                                        val t = (shape.text ?: "").trim()
                                        if (t.isNotEmpty()) {
                                            append(t)
                                            append('\n')
                                        }
                                    }
                                }
                            } catch (t: Throwable) {
                                // Skip problematic slide/diagram, don’t crash
                                Log.w(TAG, "Skipping problematic PPTX slide", t)
                            }
                        }
                    }
                }
            } ?: "Failed to read PPTX: null input stream"
        } catch (t: Throwable) {   // 👈 catches OutOfMemoryError, NoClassDefFoundError, etc.
            Log.e(TAG, "PPTX error", t)
            "Failed to read PPTX: ${t.message ?: "unknown error"}"
        }
    }


        // ... DOCX/XLSX/CSV functions as you already have ...


    // ------------------------------------------------------------------ XLSX (no DataFormatter)
    fun extractTextFromXlsx(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                XSSFWorkbook(input).use { wb ->
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

                    buildString {
                        val sheetCount = wb.numberOfSheets
                        for (s in 0 until sheetCount) {
                            val sheet: Sheet = wb.getSheetAt(s) ?: continue
                            append("Sheet: ").append(sheet.sheetName).append('\n')

                            val firstRow = sheet.firstRowNum
                            val lastRow = sheet.lastRowNum

                            for (r in firstRow..lastRow) {
                                val row: Row? = sheet.getRow(r)
                                if (row == null) continue

                                var wrote = false
                                val lastCell = row.lastCellNum.toInt().coerceAtLeast(0)

                                for (c in 0 until lastCell) {
                                    val cell: Cell? = row.getCell(c)
                                    if (cell != null) {
                                        val v = formatCellBasic(cell, dateFormat).trim()
                                        if (v.isNotEmpty()) {
                                            append(v)
                                            append(" | ")
                                            wrote = true
                                        }
                                    }
                                }

                                if (wrote) append('\n')
                            }

                            append('\n')
                        }
                    }
                }
            } ?: "Failed to read XLSX: null input stream"
        } catch (t: Throwable) {   // 👈 catches NoClassDefFoundError too, just in case
            Log.e(TAG, "XLSX error", t)
            "Failed to read XLSX: ${t.message ?: "unknown error"}"
        }
    }

    private fun formatCellBasic(cell: Cell, dateFormat: SimpleDateFormat): String {
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue ?: ""
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.NUMERIC ->
                if (DateUtil.isCellDateFormatted(cell)) {
                    dateFormat.format(cell.dateCellValue)
                } else {
                    val num = cell.numericCellValue
                    if (num % 1.0 == 0.0) num.toLong().toString() else num.toString()
                }
            CellType.FORMULA -> {
                // Simplest: whatever POI gives as a string representation
                cell.toString()
            }
            CellType.BLANK, CellType._NONE, CellType.ERROR -> ""
            else -> ""
        }
    }

    // ------------------------------------------------------------------ CSV
    fun extractTextFromCsv(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedReader(InputStreamReader(input)).use { r ->
                    buildString {
                        var line: String? = r.readLine()
                        while (line != null) {
                            append(line)
                            append('\n')
                            line = r.readLine()
                        }
                    }
                }
            } ?: "Failed to read CSV: null input stream"
        } catch (e: Exception) {
            Log.e(TAG, "CSV error", e)
            "Failed to read CSV: ${e.message}"
        }
    }
}