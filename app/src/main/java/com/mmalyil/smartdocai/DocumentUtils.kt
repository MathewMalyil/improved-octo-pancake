package com.mmalyil.smartdocai


import android.content.Context
import android.net.Uri
import org.apache.poi.xslf.usermodel.XMLSlideShow
import java.io.InputStream

import java.lang.Exception
import org.apache.poi.xwpf.usermodel.XWPFDocument // ✅ for DOCX







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
}