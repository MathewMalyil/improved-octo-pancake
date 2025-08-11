package com.mmalyil.smartdocai.util



import android.content.Context
import android.net.Uri
import java.io.BufferedReader
import java.io.InputStreamReader
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFShape
import org.apache.poi.xslf.usermodel.XSLFTextShape
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.lang.StringBuilder
import org.apache.poi.xwpf.usermodel.XWPFDocument

import android.util.Log


object DocumentUtils {

    private const val TAG = "SmartDocAI-I/O"

    fun extractTextFromDocx(context: Context, uri: Uri): String {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                XWPFDocument(input).use { doc ->
                    buildString {
                        for (p in doc.paragraphs) {
                            val t = p.text?.trim().orEmpty()
                            if (t.isNotEmpty()) append(t).append('\n')
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
                        ppt.slides.forEach { slide ->
                            slide.shapes.forEach { shape ->
                                if (shape is XSLFTextShape) {
                                    val t = shape.text?.trim().orEmpty()
                                    if (t.isNotEmpty()) append(t).append('\n')
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
                    buildString {
                        for (sheet in wb) {
                            for (row in sheet) {
                                var wrote = false
                                for (cell in row) {
                                    val v = cell.toString().trim()
                                    if (v.isNotEmpty()) {
                                        append(v).append(" | ")
                                        wrote = true
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
                        var line = r.readLine()
                        while (line != null) {
                            append(line).append('\n')
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