package com.mmalyil.smartdocai.util

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

// DOCX
import org.apache.poi.xwpf.usermodel.XWPFDocument

// PPTX
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextShape

// XLSX
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Cell

object DocumentUtils {

    private const val TAG = "SmartDocAI-I/O"

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

    fun extractTextFromPptx(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                XMLSlideShow(input).use { ppt ->
                    buildString {
                        for (slide in ppt.slides) {
                            for (shape in slide.shapes) {
                                if (shape is XSLFTextShape) {
                                    val t = (shape.text ?: "").trim()
                                    if (t.isNotEmpty()) {
                                        append(t)
                                        append('\n')
                                    }
                                }
                            }
                        }
                    }
                }
            } ?: "Failed to read PPTX: null input stream"
        } catch (e: Exception) {
            Log.e(TAG, "PPTX error", e)
            "Failed to read PPTX: ${e.message}"
        }
    }

    fun extractTextFromXlsx(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                XSSFWorkbook(input).use { wb ->
                    val fmt = DataFormatter()
                    buildString {
                        val sheetCount = wb.numberOfSheets
                        for (s in 0 until sheetCount) {
                            val sheet: Sheet = wb.getSheetAt(s) ?: continue
                            val firstRow = sheet.firstRowNum
                            val lastRow = sheet.lastRowNum
                            for (r in firstRow..lastRow) {
                                val row: Row? = sheet.getRow(r)
                                if (row == null) continue
                                var wrote = false
                                val lastCell = (row.lastCellNum.toInt().coerceAtLeast(0))
                                for (c in 0 until lastCell) {
                                    val cell: Cell? = row.getCell(c)
                                    if (cell != null) {
                                        val v = fmt.formatCellValue(cell).trim()
                                        if (v.isNotEmpty()) {
                                            append(v)
                                            append(" | ")
                                            wrote = true
                                        }
                                    }
                                }
                                if (wrote) append('\n')
                            }
                        }
                    }
                }
            } ?: "Failed to read XLSX: null input stream"
        } catch (e: Exception) {
            Log.e(TAG, "XLSX error", e)
            "Failed to read XLSX: ${e.message}"
        }
    }

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