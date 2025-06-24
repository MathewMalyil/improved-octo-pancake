package com.mmalyil.smartdocai
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.PDFBoxResourceLoader
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
import java.io.InputStream
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent

import com.github.dhaval2404.imagepicker.ImagePicker






class MainActivity : AppCompatActivity() {

    private lateinit var selectPdfButton: Button
    private lateinit var analyzeButton: Button
    private lateinit var pdfTextDisplay: TextView
    private lateinit var aiResponseDisplay: TextView
    private lateinit var promptInput: EditText
    private lateinit var modelGroup: RadioGroup
    private var extractedText: String = ""

    private lateinit var modelSelection: RadioGroup

    private val pickPdfLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { extractTextFromPdf(it) }
    }
    companion object {
        private const val REQUEST_CODE_PICK_PDF = 1001
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // View binding
        pdfTextDisplay = findViewById(R.id.pdfTextDisplay)
        aiResponseDisplay = findViewById(R.id.aiResponseDisplay)
        promptInput = findViewById(R.id.promptInput)
        modelSelection = findViewById(R.id.modelSelection)

        findViewById<Button>(R.id.selectPdfButton).setOnClickListener {
            openFilePicker()
        }

        findViewById<Button>(R.id.analyzeButton).setOnClickListener {
            val prompt = promptInput.text.toString().trim()

            if (prompt.isEmpty()) {
                showToast("Please enter a prompt")
                return@setOnClickListener
            }

            if (extractedText.isEmpty()) {
                showToast("Please select a PDF first")
                return@setOnClickListener
            }

            // Check selected GPT model
            val selectedModel = when (modelSelection.checkedRadioButtonId) {
                R.id.gpt3Radio -> "gpt-3.5-turbo"
                R.id.gpt4Radio -> "gpt-4"
                else -> "gpt-3.5-turbo"
            }

            // Free quota logic for GPT-3.5
            if (selectedModel == "gpt-3.5-turbo") {
                if (!hasFreeQuota()) {
                    showToast("Free usage quota exceeded. Upgrade to Pro to continue.")
                    return@setOnClickListener
                } else {
                    increaseUsageCount()
                }
            }

            // Call AI with prompt
            analyzeWithAI(prompt)
        }
    }

    private fun extractTextFromPdf(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream: InputStream? = contentResolver.openInputStream(uri)
                val doc = PDDocument.load(inputStream)
                val text = PDFTextStripper().getText(doc)
                doc.close()
                extractedText = text
                withContext(Dispatchers.Main) {
                    pdfTextDisplay.text = text.take(1000) + if (text.length > 1000) "..." else ""
                    showToast("PDF extracted successfully")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("PDF extraction failed: ${e.message}")
                }
            }
        }
    }

    private fun analyzeWithAI(prompt: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val model = if (modelGroup.checkedRadioButtonId == R.id.gpt3Radio)
                    "gpt-3.5-turbo" else "gpt-4"

                val service = RetrofitClient.openAIService
                val apiKey = "Bearer YOUR_API_KEY" // Replace this

                val messages = listOf(
                    Message("system", "You are a helpful assistant."),
                    Message("user", "PDF:\n$extractedText"),
                    Message("user", prompt)
                )

                val request = ChatRequest(model, messages, 0.7)
                val response = service.createChatCompletion(apiKey, request)

                withContext(Dispatchers.Main) {
                    aiResponseDisplay.text = response.choices.firstOrNull()?.message?.content ?: "No reply"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showToast("GPT error: ${e.message}")
                }

                val selectedModel = when (modelSelection.checkedRadioButtonId) {
                    R.id.gpt3Radio -> "gpt-3.5-turbo"
                    R.id.gpt4Radio -> "gpt-4"
                    else -> "gpt-3.5-turbo"
                }

                if (selectedModel == "gpt-3.5-turbo" && !hasFreeQuota()) {
                    showToast("Daily limit reached for free users. Try again tomorrow or upgrade to Pro.")

                }
                increaseUsageCount()
                analyzeWithAI(prompt)

            }
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun hasFreeQuota(): Boolean {
        val prefs = getSharedPreferences("usagePrefs", MODE_PRIVATE)
        val today = java.time.LocalDate.now().toString()
        val lastDate = prefs.getString("lastUsedDate", "")
        val currentCount = if (today == lastDate) prefs.getInt("freeUsageCount", 0) else 0
        return currentCount < 5  // ✅ LIMIT: 5 free uses/day
    }

    private fun increaseUsageCount() {
        val prefs = getSharedPreferences("usagePrefs", MODE_PRIVATE)
        val today = java.time.LocalDate.now().toString()
        val editor = prefs.edit()
        val currentCount = if (prefs.getString("lastUsedDate", "") == today)
            prefs.getInt("freeUsageCount", 0) else 0

        editor.putString("lastUsedDate", today)
        editor.putInt("freeUsageCount", currentCount + 1)
        editor.apply()
    }
    private val documentPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            val mimeType = contentResolver.getType(uri)

            when (mimeType) {
                "application/pdf" -> extractTextFromPdf(uri)

                "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> {
                    val text = DocumentUtils.extractTextFromDocx(this, uri)
                    extractedText = text
                    pdfTextDisplay.text = text.take(1000) + if (text.length > 1000) "..." else ""
                    showToast("DOCX loaded successfully")
                }

                "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> {
                    val text = DocumentUtils.extractTextFromPptx(this, uri)
                    extractedText = text
                    pdfTextDisplay.text = text.take(1000) + if (text.length > 1000) "..." else ""
                    showToast("PPTX loaded successfully")
                }

                else -> showToast("Unsupported file type")
            }
        }
    }


    private fun openFilePicker() {
        documentPickerLauncher.launch(
            arrayOf(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            )
        )
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_PDF && resultCode == RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null) {
                extractTextFromPdf(uri)
            } else {
                showToast("No file selected.")
            }
        }
    }



}