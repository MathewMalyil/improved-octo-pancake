package com.mmalyil.smartdocai

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.net.Uri
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.Toast

import java.time.LocalDate

import android.content.Intent


import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import android.content.Context
import com.itextpdf.text.Document
import com.itextpdf.text.Paragraph
import com.itextpdf.text.pdf.PdfWriter









// SPDX-License-Identifier: Apache-2.0
// SPDX-FileCopyrightText: 2023 Mert Malyil <

// This is the main activity for the SmartDocAI application
// It allows users to select PDF, DOCX, or PPTX files, extract text, and analyze it using AI models
// It also provides options to export the results as text or PDF files and share them via other apps
// It uses Retrofit for network requests, PDFBox for PDF text extraction, and Apache POI for DOCX and PPTX

// MainActivity.kt



class MainActivity : AppCompatActivity() {

    private lateinit var selectPdfButton: Button
    private lateinit var analyzeButton: Button
    private lateinit var modelGroup: RadioGroup
    private lateinit var pdfTextDisplay: TextView
    private lateinit var aiResponseDisplay: TextView
    private lateinit var promptInput: EditText
    private var extractedText = ""
    private lateinit var exportShareButton: Button

    companion object {
        const val REQUEST_CODE_PICK = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        // Bind views
        selectPdfButton = findViewById(R.id.selectPdfButton)
        analyzeButton = findViewById(R.id.analyzeButton)
        modelGroup = findViewById(R.id.modelSelection)
        pdfTextDisplay = findViewById(R.id.pdfTextDisplay)
        aiResponseDisplay = findViewById(R.id.aiResponseDisplay)
        promptInput = findViewById(R.id.promptInput)

        // Reset usage count daily
        val prefs = getSharedPreferences("usagePrefs", MODE_PRIVATE)
        val today = LocalDate.now().toString()
        if (prefs.getString("lastUsedDate", "") != today) {
            prefs.edit().putString("lastUsedDate", today).putInt("freeUsageCount", 0).apply()
        }

        // File picker
        selectPdfButton.setOnClickListener {
            openFilePicker()
        }

        // Analyze
        analyzeButton.setOnClickListener {
            val prompt = promptInput.text.toString().trim()
            if (prompt.isEmpty() || extractedText.isEmpty()) {
                showToast("Please select a file and enter prompt")
                return@setOnClickListener
            }

            val selectedModelId = modelGroup.checkedRadioButtonId

            val (modelName, useMistral) = when (selectedModelId) {
                R.id.gpt4Radio -> "gpt-4" to false
                R.id.mistralRadio -> "mistral" to true
                else -> "gpt-3.5-turbo" to false
            }

            if (modelName == "gpt-3.5-turbo" && !hasFreeQuota()) {
                showToast("Free usage limit reached. Upgrade to use more.")
                return@setOnClickListener
            }

            increaseUsageCount()

            val messages = listOf(
                Message("system", "You are a helpful assistant."),
                Message("user", "Document:\n$extractedText"),
                Message("user", prompt)
            )

            val request = ChatRequest(
                model = modelName,
                messages = messages,
                temperature = 0.7
            )

            val service = RetrofitClient.getService(useMistral)

            analyzeWithAI(request, service)
        }


        // Export/Share
        exportShareButton = findViewById(R.id.btnExportShare)
        exportShareButton.setOnClickListener {
            if (extractedText.isEmpty()) {
                showToast("No text to share")
                return@setOnClickListener
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, extractedText)
                putExtra(Intent.EXTRA_SUBJECT, "Document Analysis Result")
            }
            startActivity(Intent.createChooser(shareIntent, "Share Document Analysis"))
        }


        // Export as file
        val btnExportTxt = findViewById<Button>(R.id.btnExportTxt)
        val btnExportPdf = findViewById<Button>(R.id.btnExportPdf)

        btnExportTxt.setOnClickListener {
            if (extractedText.isNotBlank()) {
                exportAsTxt(extractedText)
            } else {
                Toast.makeText(
                    this,
                    "Please extract text from a document first",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        btnExportPdf.setOnClickListener {
            if (extractedText.isNotBlank()) {
                exportAsPdf(extractedText)
            } else {
                Toast.makeText(
                    this,
                    "Please extract text from a document first",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES, arrayOf(
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                )
            )
        }
        filePickerLauncher.launch(intent)
    }

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                val uri = result.data?.data ?: return@registerForActivityResult
                val type = contentResolver.getType(uri)

                when {
                    type?.contains("pdf") == true -> extractTextFromPdf(uri)
                    type?.contains("wordprocessingml") == true -> {
                        extractedText = DocumentUtils.extractTextFromDocx(this, uri)
                        showExtractedText("DOCX loaded")
                    }

                    type?.contains("presentationml") == true -> {
                        extractedText = DocumentUtils.extractTextFromPptx(this, uri)
                        showExtractedText("PPTX loaded")
                    }

                    else -> showToast("Unsupported file")
                }
            } else {
                showToast("File selection cancelled")
            }
        }


    private fun showExtractedText(message: String) {
        pdfTextDisplay.text =
            extractedText.take(1000) + if (extractedText.length > 1000) "..." else ""
        showToast(message)
    }

    private fun extractTextFromPdf(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val input = contentResolver.openInputStream(uri)
                val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(input)
                val text = com.tom_roush.pdfbox.text.PDFTextStripper().getText(document)
                document.close()
                withContext(Dispatchers.Main) {
                    extractedText = text
                    showExtractedText("PDF loaded")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("Error reading PDF: ${e.message}")
                }
            }
        }
    }

    private fun analyzeWithAI(request: ChatRequest, service: OpenAIService) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = service.createChatCompletion(request)

                withContext(Dispatchers.Main) {
                    aiResponseDisplay.text =
                        response.choices.firstOrNull()?.message?.content ?: "No reply"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("AI error: ${e.message}")
                }
            }
        }
    }


    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun hasFreeQuota(): Boolean {
        val prefs = getSharedPreferences("usagePrefs", MODE_PRIVATE)
        return prefs.getInt("freeUsageCount", 0) < 5
    }

    private fun increaseUsageCount() {
        val prefs = getSharedPreferences("usagePrefs", MODE_PRIVATE)
        val current = prefs.getInt("freeUsageCount", 0)
        prefs.edit().putInt("freeUsageCount", current + 1).apply()
    }






    fun exportAsPdf(text: String) {
        try {
            val file = File(getExternalFilesDir(null), "SmartDocAI_Export.pdf")
            val document = Document()
            PdfWriter.getInstance(document, FileOutputStream(file))
            document.open()
            document.add(Paragraph(text))
            document.close()

            val uri = FileProvider.getUriForFile(
                this,
                "com.mmalyil.smartdocai.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "SmartDocAI PDF Export")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            startActivity(Intent.createChooser(shareIntent, "Share PDF via"))

        } catch (e: Exception) {
            e.printStackTrace()
            showToast("PDF export failed: ${e.message}")
        }
    }


    fun exportAsTxt(text: String) {
        val file = File(getExternalFilesDir(null), "SmartDocAI_Export.txt")
        file.writeText(text)

        val uri = FileProvider.getUriForFile(
            this,
            "com.mmalyil.smartdocai.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "SmartDocAI TXT Export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Share TXT via"))
    }

}


