package com.mmalyil.smartdocai.util

import android.content.Context
import android.net.Uri
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
            context.contentResolver.openInputStream(uri)?.use { input ->
                XMLSlideShow(input).use { ppt ->
                    buildString {
                        ppt.slides.forEachIndexed { index, slide ->
                            append("Slide ${index + 1}\n")

                            try {
                                // NOTE: slide.shapes is where POI will build shapes/diagrams
                                val shapes: List<XSLFShape> = slide.shapes

                                for (shape in shapes) {
                                    if (shape is XSLFTextShape) {
                                        val t = (shape.text ?: "").trim()
                                        if (t.isNotEmpty()) {
                                            append(t)
                                            append('\n')
                                        }
                                    }
                                }
                            } catch (e: NoClassDefFoundError) {
                                // Some SmartArt / diagrams rely on POI classes that
                                // aren’t safe/available on Android – skip this slide.
                                Log.w(TAG, "Skipping unsupported PPTX slide (diagram/SmartArt)", e)
                                append("[Slide contains unsupported diagrams; text skipped]\n")
                            } catch (e: Throwable) {
                                Log.w(TAG, "Error while reading PPTX slide", e)
                            }

                            append('\n')
                        }
                    }
                }
            } ?: "Failed to read PPTX: null input stream"
        } catch (t: Throwable) {  // catches NoClassDefFoundError as well
            Log.e(TAG, "PPTX error", t)
            "Failed to read PPTX: ${t.message ?: "unknown error"}"
        }
    }

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