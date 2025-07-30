package com.mmalyil.smartdocai


import android.os.Bundle
import android.widget.TextView
import androidx.fragment.app.Fragment
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.itextpdf.text.Document
import com.itextpdf.text.Paragraph
import com.itextpdf.text.pdf.PdfWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import android.widget.CheckBox
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.mmalyil.smartdocai.model.AppDatabase
import com.mmalyil.smartdocai.model.ScannedFileRepository
import com.mmalyil.smartdocai.model.ScannedFileViewModel
import android.provider.OpenableColumns
import android.util.Log
import com.mmalyil.smartdocai.util.GPTUsageManager
import android.content.Context
import android.app.AlarmManager
import android.app.PendingIntent
import java.util.Calendar
import android.app.Activity
import com.mmalyil.smartdocai.model.ChatMessage
import com.mmalyil.smartdocai.model.ChatRequest

class ToolsFragment : Fragment() {

    private lateinit var selectPdfButton: Button
    private lateinit var analyzeButton: Button
    private lateinit var modelGroup: RadioGroup
    private lateinit var pdfTextDisplay: TextView
    private lateinit var aiResponseDisplay: TextView
    private lateinit var promptInput: EditText
    private lateinit var exportShareButton: Button
    private lateinit var chkIncludeDoc: CheckBox
    private lateinit var chkIncludeAI: CheckBox
    private lateinit var btnPickImage: Button
    private lateinit var btnExportTxt: Button
    private lateinit var btnExportPdf: Button

    private var extractedText = ""
    private var aiAnswer = ""
    private var selectedFileName = ""
    private var selectedFileUri = ""
    private val PICK_DOCUMENT_REQUEST_CODE = 1001
    private lateinit var scannedFileViewModel: ScannedFileViewModel

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { processImageForOCR(it) } }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            selectedFileUri = uri.toString()

            val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    selectedFileName =
                        it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
            }

            val type = requireContext().contentResolver.getType(uri)
            when {
                type?.contains("pdf") == true -> extractTextFromPdf(uri)
                type?.contains("wordprocessingml") == true -> {
                    extractedText = DocumentUtils.extractTextFromDocx(requireContext(), uri)
                    showExtractedText("DOCX loaded")
                }

                type?.contains("presentationml") == true -> {
                    extractedText = DocumentUtils.extractTextFromPptx(requireContext(), uri)
                    showExtractedText("PPTX loaded")
                }

                else -> toast("Unsupported file")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_tools, container, false)

        // Bind views
        selectPdfButton = view.findViewById(R.id.selectPdfButton)
        analyzeButton = view.findViewById(R.id.analyzeButton)
        modelGroup = view.findViewById(R.id.modelSelection)
        pdfTextDisplay = view.findViewById(R.id.pdfTextDisplay)
        aiResponseDisplay = view.findViewById(R.id.aiResponseDisplay)
        promptInput = view.findViewById(R.id.promptInput)
        chkIncludeDoc = view.findViewById(R.id.includeDocumentText)
        chkIncludeAI = view.findViewById(R.id.includeAIResponse)
        btnExportTxt = view.findViewById(R.id.btnExportTxt)
        btnExportPdf = view.findViewById(R.id.btnExportPdf)
        exportShareButton = view.findViewById(R.id.btnExportShare)
        btnPickImage = view.findViewById(R.id.btnPickImage)

        scannedFileViewModel = ScannedFileViewModel(
            ScannedFileRepository(AppDatabase.getDatabase(requireContext()).scannedFileDao())
        )

        selectPdfButton.setOnClickListener { openFilePicker() }

        analyzeButton.setOnClickListener {
            val prompt = promptInput.text.toString().trim()
            if (prompt.isEmpty() || extractedText.isEmpty()) {
                toast("Please select a file and enter a prompt")
                return@setOnClickListener
            }
            analyzeSmartlyWithQuota(
                context = requireContext(),
                prompt = prompt,
                extractedText = extractedText,
                fileName = selectedFileName,
                fileUri = selectedFileUri,
                viewModel = scannedFileViewModel
            )

        }

        btnExportTxt.setOnClickListener {
            if (!chkIncludeDoc.isChecked && !chkIncludeAI.isChecked) {
                toast("Please select at least one option to export")
                return@setOnClickListener
            }
            exportAsTxt(chkIncludeDoc.isChecked, chkIncludeAI.isChecked)
        }

        btnExportPdf.setOnClickListener {
            if (!chkIncludeDoc.isChecked && !chkIncludeAI.isChecked) {
                toast("Please select at least one option to export")
                return@setOnClickListener
            }
            exportAsPdf(chkIncludeDoc.isChecked, chkIncludeAI.isChecked)
        }

        exportShareButton.setOnClickListener {
            if (extractedText.isEmpty()) {
                toast("No text to share")
                return@setOnClickListener
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, extractedText)
                putExtra(Intent.EXTRA_SUBJECT, "Document Analysis Result")
            }
            startActivity(Intent.createChooser(shareIntent, "Share Document Analysis"))
        }

        chkIncludeDoc.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked && !chkIncludeAI.isChecked) chkIncludeDoc.isChecked = true
        }
        chkIncludeAI.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked && !chkIncludeDoc.isChecked) chkIncludeAI.isChecked = true
        }

        btnPickImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        view.findViewById<Button?>(R.id.btnOpenAIChat)?.setOnClickListener {
            val intent = Intent(requireContext(), AIChatActivity::class.java)
            startActivity(intent)
        }
        scheduleDailyUsageReset(requireContext())
        // Initialize the view model observer

        return view
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES, arrayOf(
                    "application/pdf",
                    "application/msword", // .doc
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
                    "application/vnd.ms-powerpoint", // .ppt
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation" // .pptx
                ))
        }
        startActivityForResult(intent, PICK_DOCUMENT_REQUEST_CODE)
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_DOCUMENT_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                // 👇 Persist permission
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                // 👉 Pass the URI to your extractor/AI logic
                handlePickedDocument(uri)
            }
        }
    }
    private fun handlePickedDocument(uri: Uri) {
        selectedFileUri = uri.toString()

        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                selectedFileName =
                    it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        }

        val type = requireContext().contentResolver.getType(uri)
        when {
            type?.contains("pdf") == true -> extractTextFromPdf(uri)
            type?.contains("wordprocessingml") == true -> {
                extractedText = DocumentUtils.extractTextFromDocx(requireContext(), uri)
                showExtractedText("DOCX loaded")
            }
            type?.contains("presentationml") == true -> {
                extractedText = DocumentUtils.extractTextFromPptx(requireContext(), uri)
                showExtractedText("PPTX loaded")
            }
            else -> toast("Unsupported file")
        }

       
// Show feedback that file was picked
        Toast.makeText(requireContext(), "Picked: $selectedFileName", Toast.LENGTH_SHORT).show()

// 🔁 Auto-trigger AI if prompt is already entered
        val prompt = promptInput.text.toString().trim()
        if (prompt.isNotEmpty() && extractedText.isNotEmpty()) {
            analyzeSmartlyWithQuota(
                context = requireContext(),
                prompt = prompt,
                extractedText = extractedText,
                fileName = selectedFileName,
                fileUri = selectedFileUri,
                viewModel = scannedFileViewModel
            )
        }

    }


    private fun processImageForOCR(uri: Uri) {
        try {
            val inputImage = InputImage.fromFilePath(requireContext(), uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(inputImage)
                .addOnSuccessListener {
                    extractedText = it.text
                    pdfTextDisplay.text = it.text
                    toast("Text extracted from image successfully!")
                }
                .addOnFailureListener { toast("OCR failed: ${it.message}") }
        } catch (e: Exception) {
            toast("Failed to load image: ${e.message}")
        }
    }

    private fun extractTextFromPdf(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val input = requireContext().contentResolver.openInputStream(uri)
                val doc = com.tom_roush.pdfbox.pdmodel.PDDocument.load(input)
                val text = com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc)
                doc.close()
                withContext(Dispatchers.Main) {
                    extractedText = text
                    showExtractedText("PDF loaded")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast("Error reading PDF: ${e.message}") }
            }
        }
    }

    private fun analyzeSmartlyWithQuota(
        context: Context,

        prompt: String,
        extractedText: String,
        fileName: String,
        fileUri: String,
        viewModel: ScannedFileViewModel
    ) {
        val gpt4Limit = 300
        val gpt35Limit = 300
        val groqLimit = 500

        val gpt4Used = GPTUsageManager.getUsage(context, "gpt-4")
        val gpt35Used = GPTUsageManager.getUsage(context, "gpt-3.5")
        val groqUsed = GPTUsageManager.getUsage(context, "groq")

        when {
            gpt4Used < gpt4Limit && isProUser() -> {
                GPTUsageManager.incrementUsage(context, "gpt-4")
                analyzeWithAI(
                    prompt,
                    "gpt-4",
                    "openai",
                    extractedText,
                    fileName,
                    fileUri,
                    viewModel
                )
            }

            groqUsed < groqLimit -> {
                GPTUsageManager.incrementUsage(context, "groq")
                analyzeWithAI(
                    prompt,
                    "llama3-8b-8192",
                    "groq",
                    extractedText,
                    fileName,
                    fileUri,
                    viewModel
                )
            }

            gpt35Used < gpt35Limit -> {
                GPTUsageManager.incrementUsage(context, "gpt-3.5")
                analyzeWithAI(
                    prompt,
                    "gpt-3.5-turbo",
                    "openai",
                    extractedText,
                    fileName,
                    fileUri,
                    viewModel
                )
            }

            else -> {
                Toast.makeText(
                    context,
                    "All free model limits used. Upgrade for more access.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }


    private fun analyzeWithAI(
        prompt: String,
        modelName: String,
        source: String,
        extractedText: String,
        fileName: String,
        fileUri: String,
        viewModel: ScannedFileViewModel
    ) {
        val service = RetrofitClient.getService(source)
        val apiKey = RetrofitClient.apiKey(source)


        val messages = listOf(
            ChatMessage("system", "Reply in the same language as the user's message."),
            ChatMessage("user", "Here is the document text:\n$extractedText"),
            ChatMessage("user", prompt)
        )

        val request = ChatRequest(model = modelName, messages = messages, temperature = 0.7)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = service.createChatCompletion("Bearer $apiKey", request)
                val aiReply = response.choices.firstOrNull()?.message?.content ?: "No reply"

                viewModel.insertFile(
                    fileUri = fileUri,
                    fileName = fileName,
                    content = extractedText,
                    aiResponse = aiReply
                )
                Log.d("DEBUG_SAVE", "fileName=$fileName")
                Log.d("DEBUG_SAVE", "fileUri=$fileUri")
                Log.d("DEBUG_SAVE", "content=${extractedText.take(100)}")
                Log.d("DEBUG_SAVE", "aiResponse=${aiReply?.take(100)}")
                withContext(Dispatchers.Main) {
                    aiResponseDisplay.text = aiReply
                    aiAnswer = aiReply
                    toast("AI analysis complete and saved")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val message = e.localizedMessage ?: "Unknown error"
                    Log.e("AI_ERROR", "Model: $modelName, Source: $source, Error: $message")

                    // Auto-fallback only for GPT-4 and Pro users
                    if (modelName == "gpt-4" && isProUser()) {
                        aiResponseDisplay.text = "GPT-4 failed. Retrying with Groq..."

                        // Track Groq usage for fallback
                        GPTUsageManager.incrementUsage(requireContext(), "groq")

                        // Retry with Groq fallback model
                        analyzeWithAI(
                            prompt = prompt,
                            modelName = "llama3-8b-8192",
                            source = "groq",
                            extractedText = extractedText,
                            fileName = fileName,
                            fileUri = fileUri,
                            viewModel = viewModel
                        )
                    } else {
                        aiResponseDisplay.text = "Error: $message"
                        toast("AI Error: $message")
                    }
                }
            }


        }
    }


    private fun exportAsPdf(includeDoc: Boolean, includeAI: Boolean) {
        val content = buildExportContent(includeDoc, includeAI)
        val file = File(requireContext().getExternalFilesDir(null), "SmartDocAI_Export.pdf")
        val document = Document()
        PdfWriter.getInstance(document, FileOutputStream(file))
        document.open()
        document.add(Paragraph(content))
        document.close()
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "com.mmalyil.smartdocai.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "SmartDocAI PDF Export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share PDF via"))
    }

    private fun exportAsTxt(includeDoc: Boolean, includeAI: Boolean) {
        val content = buildExportContent(includeDoc, includeAI)
        val file = File(requireContext().getExternalFilesDir(null), "SmartDocAI_Export.txt")
        file.writeText(content)
        val uri = FileProvider.getUriForFile(
            requireContext(),
            "com.mmalyil.smartdocai.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "SmartDocAI TXT Export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share TXT via"))
    }

    private fun buildExportContent(includeDoc: Boolean, includeAI: Boolean): String {
        val builder = StringBuilder()
        if (includeDoc) builder.append("\uD83D\uDCC4 Extracted Document:\n$extractedText\n\n------------------------------\n\n")
        if (includeAI) builder.append("\uD83E\uDD16 AI Analysis:\n$aiAnswer\n")
        return builder.toString()
    }

    private fun showExtractedText(message: String) {
        pdfTextDisplay.text =
            extractedText.take(1000) + if (extractedText.length > 1000) "..." else ""
        toast(message)
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    fun triggerUploadFromFab() {
        openFilePicker()
    }

    fun triggerPickImageFromFab() {
        imagePickerLauncher.launch("image/*")
    }

    private val scanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val scannedText = result.data?.getStringExtra("scannedText") ?: ""
                extractedText = scannedText
                pdfTextDisplay.text = scannedText
                toast("Scanned text loaded")
            }
        }

    fun triggerScanFromFab() {
        val intent = Intent(requireContext(), ScanActivity::class.java)
        scanLauncher.launch(intent)
    }

    private fun isProUser(): Boolean {
        val prefs = requireContext().getSharedPreferences("billingPrefs", 0)
        return prefs.getBoolean("isPro", false)
    }

    /**
     * Schedules a daily reset of GPT usage limits at 2 AM.
     * This uses AlarmManager to trigger a broadcast receiver that resets the usage counts.
     */

    private fun scheduleDailyUsageReset(context: Context) {
        val intent = Intent(context, UsageResetReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 2)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
        }

        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }
}
