package com.mmalyil.smartdocai


import android.content.Context
import android.net.Uri
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xslf.usermodel.XMLSlideShow
import java.io.InputStream

import java.lang.Exception
import org.apache.poi.xwpf.usermodel.XWPFDocument // ✅ for DOCX
import java.io.BufferedReader
import java.io.InputStreamReader
import android.widget.Toast
import java.io.IOException
import java.io.Serializable
import com.mmalyil.smartdocai.model.ScannedFile


object DocumentUtils {

    fun extractTextFromDocx(context: Context, uri: Uri): String {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val document = org.apache.poi.xwpf.usermodel.XWPFDocument(inputStream)
            val text = document.paragraphs.joinToString("\n") { it.text }
            document.close()
            text
        } catch (e: Exception) {
            "Failed to read DOCX: ${e.message}"
        }
    }

    fun extractTextFromPptx(context: Context, uri: Uri): String {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val ppt = XMLSlideShow(inputStream)
            val slides = ppt.slides

            val text = slides.joinToString("\n--- Slide ---\n") { slide ->
                slide.shapes
                    .filterIsInstance<org.apache.poi.xslf.usermodel.XSLFTextShape>()
                    .joinToString("\n") { it.text }
            }

            ppt.close()
            text
        } catch (e: Exception) {
            "Failed to read PPTX: ${e.message}"
        }
    }
    fun extractTextFromXlsx(context: Context, uri: Uri): String {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val workbook = WorkbookFactory.create(inputStream)
            val text = buildString {
                for (sheetIndex in 0 until workbook.numberOfSheets) {
                    val sheet = workbook.getSheetAt(sheetIndex)
                    append("Sheet: ${sheet.sheetName}\n")
                    for (row in sheet) {
                        for (cell in row) {
                            append(cell.toString() + "\t")
                        }
                        append("\n")
                    }
                    append("\n")
                }
            }
            workbook.close()
            text
        } catch (e: Exception) {
            "Failed to read XLSX: ${e.message}"
        }
    }

    fun extractTextFromCsv(context: Context, uri: Uri): String {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val reader = BufferedReader(InputStreamReader(inputStream))
            val text = reader.readLines().joinToString("\n")
            reader.close()
            text
        } catch (e: Exception) {
            "Failed to read CSV: ${e.message}"
        }
    }
}


// Note: The DocumentUtils object provides methods to extract text from various document formats.
// It handles DOCX, PPTX, XLSX, and CSV files using Apache POI and standard Java I/O.
// Each method returns the extracted text or an error message if the operation fails
