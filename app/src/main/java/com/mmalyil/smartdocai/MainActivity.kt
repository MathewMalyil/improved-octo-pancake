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
import android.widget.CheckBox
import java.time.LocalDate
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

import com.itextpdf.text.Document
import com.itextpdf.text.Paragraph
import com.itextpdf.text.pdf.PdfWriter


import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions






// Constants for API keys









val groqApiKey= com.mmalyil.smartdocai.BuildConfig.GROQ_API_KEY


class MainActivity : AppCompatActivity() {

    private lateinit var selectPdfButton: Button
    private lateinit var analyzeButton: Button
    private lateinit var modelGroup: RadioGroup
    private lateinit var pdfTextDisplay: TextView
    private lateinit var aiResponseDisplay: TextView
    private lateinit var promptInput: EditText
    private var extractedText = ""
    private lateinit var exportShareButton: Button

    private lateinit var chkIncludeDoc: CheckBox
    private lateinit var chkIncludeAI: CheckBox
    private var aiAnswer: String = ""

    private lateinit var btnPickImage: Button



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


        val cbIncludeDoc = findViewById<CheckBox>(R.id.includeDocumentText)
        val cbIncludeAI = findViewById<CheckBox>(R.id.includeAIResponse)


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

            val (modelName, source) = when (selectedModelId) {
                R.id.gpt4Radio -> "gpt-4" to "openai"
                R.id.groqRadio -> "meta-llama/llama-4-scout-17b-16e-instruct" to "groq"
                R.id.mistralRadio -> "mistral" to "mistral"
                else -> "gpt-3.5-turbo" to "openai"
            }

            if (modelName == "gpt-3.5-turbo" && !hasFreeQuota()) {
                showToast("Free usage limit reached. Upgrade to use more.")
                return@setOnClickListener
            }

            increaseUsageCount()

            val messages = listOf(
                ChatMessage("system", "You are a helpful assistant."),
                ChatMessage("user", "Document:\n$extractedText"),
                ChatMessage("user", prompt)
            )

            val request = ChatRequest(
                model = modelName,
                messages = messages,
                temperature = 0.7
            )


            val service = RetrofitClient.getService(source)
            val authHeader = when (source) {

                "groq" -> "Bearer $groqApiKey"
                else -> "" // Local mistral needs no auth
            }
            analyzeWithAI(prompt, modelName, source, extractedText)
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
            val includeDoc = chkIncludeDoc.isChecked
            val includeAI = chkIncludeAI.isChecked
            if (!includeDoc && !includeAI) {
                showToast("Please select at least one option to export")
                return@setOnClickListener
            }
            exportAsTxt(includeDoc, includeAI)
        }

        btnExportPdf.setOnClickListener {
            val includeDoc = chkIncludeDoc.isChecked
            val includeAI = chkIncludeAI.isChecked
            if (!includeDoc && !includeAI) {
                showToast("Please select at least one option to export")
                return@setOnClickListener
            }
            exportAsPdf(includeDoc, includeAI)
        }


        // Checkboxes for export options ANDROID ADDED
        chkIncludeDoc = findViewById(R.id.includeDocumentText)
        chkIncludeAI = findViewById(R.id.includeAIResponse)
        chkIncludeDoc.isChecked = true // Default to include document text
        chkIncludeAI.isChecked = true // Default to include AI response
        chkIncludeDoc.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked && !chkIncludeAI.isChecked) {
                showToast("Please select at least one option to export")
                chkIncludeDoc.isChecked = true // Revert to checked
            }
        }
        chkIncludeAI.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked && !chkIncludeDoc.isChecked) {
                showToast("Please select at least one option to export")
                chkIncludeAI.isChecked = true // Revert to checked
            }
        }


//ANDROID CLOSE HERE CODE
        val btnOpenAIChat = findViewById<Button>(R.id.btnOpenAIChat)
        btnOpenAIChat.setOnClickListener {
            val intent = Intent(this, AIChatActivity::class.java)
            startActivity(intent)
        }


        val btnScanDoc = findViewById<Button>(R.id.btnScanDoc)
        btnScanDoc.setOnClickListener {
            val intent = Intent(this, ScanActivity::class.java)
            scanLauncher.launch(intent)
        }

        btnPickImage = findViewById(R.id.btnPickImage)

        btnPickImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }


    }
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { processImageForOCR(it) }
    }

    private fun processImageForOCR(uri: Uri) {
        try {
            val inputImage = InputImage.fromFilePath(this, uri)


            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            recognizer.process(inputImage)
                .addOnSuccessListener { result ->
                    val scannedText = result.text
                    extractedText = scannedText
                    pdfTextDisplay.text = scannedText
                    showToast("Text extracted from image successfully!")
                }
                .addOnFailureListener { e ->
                    showToast("OCR failed: ${e.message}")
                }
        } catch (e: Exception) {
            showToast("Failed to load image: ${e.message}")
        }
    }



private val scanLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val scannedText = result.data?.getStringExtra("scannedText") ?: ""
            extractedText = scannedText
            pdfTextDisplay.text = scannedText
            showToast("Scanned text loaded")
        }

    }


    // Open file picker to select PDF, DOCX, or PPTX
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

    fun analyzeWithAI(prompt: String, modelName: String, source: String, extractedText: String) {
        val service = RetrofitClient.getService(source)
        val apiKey = when (source) {
            "groq" -> com.mmalyil.smartdocai.BuildConfig.GROQ_API_KEY
            "openai" -> com.mmalyil.smartdocai.BuildConfig.GROQ_API_KEY
            "mistral" -> "" // Local Mistral does not require an API key
            else -> "DUMMY_KEY" // if using OpenAI too
        }


        val messages = listOf(
            ChatMessage("system", "Reply in the same language as the user's message."),
            ChatMessage("user", "Here is the document text:\n$extractedText"),
            ChatMessage("user", prompt)
        )

        val request = ChatRequest(
            model = modelName,
            messages = messages,
            temperature = 0.7// 👈 Add this
        )
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = service.createChatCompletion("Bearer $apiKey", request)
                val aiReply = response.choices.firstOrNull()?.message?.content ?: "No reply"
                withContext(Dispatchers.Main) {
                    aiResponseDisplay.text = aiReply
                    aiAnswer = aiReply // Store the AI answer for export
                    showToast("AI analysis complete")
                    if (hasFreeQuota()) {
                        showToast("Free usage count increased. You have ${5 - getSharedPreferences("usagePrefs", MODE_PRIVATE).getInt("freeUsageCount", 0)} uses left.")
                    } else {
                        showToast("Free usage limit reached. Upgrade to use more.")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    aiResponseDisplay.text = "Error: ${e.localizedMessage}"
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


    fun exportAsPdf(includeDoc: Boolean, includeAI: Boolean) {
        val content = buildExportContent(includeDoc, includeAI)
        val file = File(getExternalFilesDir(null), "SmartDocAI_Export.pdf")
        val document = Document()
        PdfWriter.getInstance(document, FileOutputStream(file))
        document.open()
        document.add(Paragraph(content))
        document.close()

        val uri = FileProvider.getUriForFile(this, "com.mmalyil.smartdocai.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "SmartDocAI PDF Export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Share PDF via"))
    }


    fun exportAsTxt(includeDoc: Boolean, includeAI: Boolean) {
        val content = buildExportContent(includeDoc, includeAI)
        val file = File(getExternalFilesDir(null), "SmartDocAI_Export.txt")
        file.writeText(content)

        val uri = FileProvider.getUriForFile(this, "com.mmalyil.smartdocai.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "SmartDocAI TXT Export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Share TXT via"))
    }


    private fun buildExportContent(includeDoc: Boolean, includeAI: Boolean): String {
        val builder = StringBuilder()
        if (includeDoc) {
            builder.append("📄 Extracted Document:\n")
                .append(extractedText.trim())
                .append("\n\n------------------------------\n\n")
        }
        if (includeAI) {
            builder.append("🤖 AI Analysis:\n").append(aiAnswer) .append("\n\n")
        }
        return builder.toString()
    }





}


