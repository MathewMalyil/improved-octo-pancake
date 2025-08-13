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

import com.mmalyil.smartdocai.util.GPTUsageManager
import android.content.Context
import android.app.AlarmManager
import android.app.PendingIntent
import java.util.Calendar
import android.app.Activity
import android.widget.ProgressBar

import com.mmalyil.smartdocai.api.ChatApiHelper
import com.mmalyil.smartdocai.model.ChatMessage
import com.mmalyil.smartdocai.model.ChatRequest

import androidx.appcompat.app.AlertDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.mmalyil.smartdocai.util.UsageManager
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.Scope

import com.mmalyil.smartdocai.util.DocumentUtils

import com.mmalyil.smartdocai.BuildConfig

import android.accounts.Account
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

import kotlin.io.use

import kotlinx.coroutines.*


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
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                handlePickedDocument(uri)
            }
        }
    }

    private val RC_GOOGLE_SIGN_IN = 1001

    private val RC_RECOVER_AUTH = 1002

    private val http by lazy { OkHttpClient() }

    private var pendingAccount: GoogleSignInAccount? = null
    private var pendingToken: String? = null

    // Call this from your Upload FAB (you can wire it like "Import from Google Docs")
    private fun showUploadOptionsDialog() {
        val base = mutableListOf("Upload File", "Scan Image")
        if (BuildConfig.USE_GOOGLE_DOCS) base += "Import from Google Docs"
        val options = base.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("Choose Action")
            .setItems(options) { _, which ->
                when (options[which]) {
                    "Upload File" -> openFilePicker()
                    "Scan Image" -> imagePickerLauncher.launch("image/*")
                    "Import from Google Docs" -> initiateGoogleSignIn()
                }
            }
            .show()
    }

    private fun showDocsFallbackDialog() {
        if (!BuildConfig.USE_GOOGLE_DOCS) {
            toast("Unable to read this file locally.")
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Couldn’t read this file")
            .setMessage("Try importing via Google Docs instead?")
            .setPositiveButton("Import via Google Docs") { _, _ -> initiateGoogleSignIn() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Sign-in launcher
    // Sign-in launcher
    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode != Activity.RESULT_OK || res.data == null) {
            toast("Sign-in canceled")
            return@registerForActivityResult
        }
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(res.data)
                .getResult(com.google.android.gms.common.api.ApiException::class.java)

            // Remember account for possible recover flow
            pendingAccount = account

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val token = getAccessToken(account) // may throw UserRecoverableAuthException
                    pendingToken = token

                    val items = listDriveFiles(token)
                    showDriveChooser(items) { id, mime ->
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                handleDrivePick(token, id, mime)
                            } catch (e: Exception) {
                                toast("Open failed: ${e.message}")
                            }
                        }
                    }
                } catch (e: UserRecoverableAuthException) {
                    recoverAuthLauncher.launch(e.intent)
                } catch (e: Exception) {
                    toast("Drive listing failed: ${e.message}")
                }
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            toast("Google Sign-In failed: ${e.statusCode}")
        }
    }

    // Recoverable auth launcher
    private val recoverAuthLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode != Activity.RESULT_OK) {
            toast("Permission not granted")
            return@registerForActivityResult
        }
        val account =
            pendingAccount ?: run { toast("No account"); return@registerForActivityResult }
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val token = getAccessToken(account)
                pendingToken = token

                val items = listDriveFiles(token)
                showDriveChooser(items) { id, mime ->
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            handleDrivePick(token, id, mime)
                        } catch (e: Exception) {
                            toast("Open failed: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                toast("Authorization failed: ${e.message}")
            }
        }
    }

    private fun initiateGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope("https://www.googleapis.com/auth/drive.readonly"),
                Scope("https://www.googleapis.com/auth/documents.readonly")
            )
            .build()
        val client = GoogleSignIn.getClient(requireActivity(), gso)
        signInLauncher.launch(client.signInIntent)
    }

    private suspend fun getAccessToken(account: GoogleSignInAccount): String {
        val scope =
            "oauth2:https://www.googleapis.com/auth/drive.readonly https://www.googleapis.com/auth/documents.readonly"
        pendingAccount = account
        return withContext(Dispatchers.IO) {
            @Suppress("DEPRECATION")
            GoogleAuthUtil.getToken(requireContext(), account.account as Account, scope)
        }
    }

    // 1) List LOTS of files across My Drive + Shared drives + Shared with me,
// including Google Docs/Sheets/Slides AND real PDFs/DOCX/PPTX/XLSX.
    private suspend fun listDriveFiles(token: String): List<Triple<String, String, String>> {
        val baseUrl = "https://www.googleapis.com/drive/v3/files"
        val pageSize = 200

        // Broad: all non-folder items. We'll resolve shortcuts client-side.
        val q = """
(trashed = false) and (mimeType != 'application/vnd.google-apps.folder') and 
(sharedWithMe = true or 'me' in owners or 'me' in writers or 'me' in readers)
""".trimIndent()
        val fields =
            "nextPageToken, files(id,name,mimeType,shortcutDetails(targetId,targetMimeType),modifiedTime)"

        val out = mutableListOf<Triple<String, String, String>>()
        var pageToken: String? = null
        do {
            val url = buildString {
                append(baseUrl)
                append("?q=" + Uri.encode(q))
                append("&fields=" + Uri.encode(fields))
                append("&orderBy=modifiedTime desc")
                append("&pageSize=$pageSize")
                append("&corpora=allDrives")
                append("&includeItemsFromAllDrives=true")
                append("&supportsAllDrives=true")
                if (!pageToken.isNullOrBlank()) append("&pageToken=$pageToken")
            }

            val req = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()

            val body = withContext(Dispatchers.IO) {
                http.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) error("Drive list failed: ${r.code}")
                    r.body?.string().orEmpty()
                }
            }

            val json = JSONObject(body)
            val files = json.optJSONArray("files") ?: JSONArray()
            for (i in 0 until files.length()) {
                val f = files.getJSONObject(i)
                // If it’s a shortcut, use the target
                val shortcut = f.optJSONObject("shortcutDetails")
                val id = shortcut?.optString("targetId") ?: f.getString("id")
                val mime = shortcut?.optString("targetMimeType") ?: f.getString("mimeType")
                val name = f.getString("name")
                out += Triple(name, id, mime)
            }
            pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
        } while (pageToken != null)

        // Optional: client-side filter to show only types we support analyzing
        return out.filter { mime ->
            val m = mime.third
            m == "application/vnd.google-apps.document" ||
                    m == "application/vnd.google-apps.spreadsheet" ||
                    m == "application/vnd.google-apps.presentation" ||
                    m == "application/pdf" ||
                    m == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
                    m == "application/vnd.openxmlformats-officedocument.presentationml.presentation" ||
                    m == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ||
                    m == "application/vnd.ms-excel" ||
                    m == "application/vnd.ms-powerpoint"
        }
    }

    private fun showDriveChooser(
        items: List<Triple<String, String, String>>,
        onPick: (id: String, mime: String) -> Unit
    ) {
        if (items.isEmpty()) {
            toast("No matching Drive files found."); return
        }
        val names = items.map { it.first }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Select a Drive file (${items.size})")
            .setItems(names) { _, which -> onPick(items[which].second, items[which].third) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // 2) Convert Google-native files before parsing, so Sheets/Slides also work.
    private suspend fun handleDrivePick(token: String, fileId: String, mime: String) {
        when (mime) {
            // Google Docs → export plain text
            "application/vnd.google-apps.document" -> {
                val text = exportGoogleDocToText(token, fileId)
                extractedText = if (text.isBlank()) "[No text in this Google Doc]" else text
                withContext(Dispatchers.Main) {
                    showExtractedText("Google Doc loaded")
                    maybeAutoAnalyze()
                }
            }

            // Google Sheets → export CSV (first sheet), then show as text
            "application/vnd.google-apps.spreadsheet" -> {
                val url =
                    "https://www.googleapis.com/drive/v3/files/$fileId/export?mimeType=text/csv"
                val req = Request.Builder().url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                val csv = withContext(Dispatchers.IO) {
                    http.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) error("Export failed: ${r.code}")
                        r.body?.string().orEmpty()
                    }
                }
                withContext(Dispatchers.Main) { handleResult("CSV", csv) }
            }

            // Google Slides → export PPTX, then parse locally via POI
            "application/vnd.google-apps.presentation" -> {
                val url =
                    "https://www.googleapis.com/drive/v3/files/$fileId/export?mimeType=application/vnd.openxmlformats-officedocument.presentationml.presentation"
                val req = Request.Builder().url(url)
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                val bytes = withContext(Dispatchers.IO) {
                    http.newCall(req).execute().use { r ->
                        if (!r.isSuccessful) error("Export failed: ${r.code}")
                        r.body?.bytes() ?: ByteArray(0)
                    }
                }
                val tmp = File.createTempFile("slides_", ".pptx", requireContext().cacheDir)
                withContext(Dispatchers.IO) { tmp.outputStream().use { it.write(bytes) } }
                val text = DocumentUtils.extractTextFromPptx(requireContext(), Uri.fromFile(tmp))
                withContext(Dispatchers.Main) { handleResult("PPTX", text) }
            }

            // Direct-download types
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "application/vnd.ms-powerpoint" -> {
                val bytes = downloadFileBytes(token, fileId)
                val tmp = File.createTempFile(
                    "drive_dl_",
                    guessExtension(mime),
                    requireContext().cacheDir
                )
                withContext(Dispatchers.IO) { tmp.outputStream().use { it.write(bytes) } }
                val uri = Uri.fromFile(tmp)

                val text = when (mime) {
                    "application/pdf" -> extractPdfBlocking(uri)
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                        DocumentUtils.extractTextFromDocx(requireContext(), uri)

                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/vnd.ms-powerpoint" ->
                        DocumentUtils.extractTextFromPptx(requireContext(), uri)

                    else -> // xlsx/xls
                        DocumentUtils.extractTextFromXlsx(requireContext(), uri)
                }
                withContext(Dispatchers.Main) { handleResult(mimeShort(mime), text) }
            }

            else -> withContext(Dispatchers.Main) { toast("Unsupported Drive type: $mime") }
        }
    }

    private suspend fun downloadFileBytes(token: String, fileId: String): ByteArray {
        val url =
            "https://www.googleapis.com/drive/v3/files/$fileId?alt=media&supportsAllDrives=true"
        val req = Request.Builder().url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()
        return withContext(Dispatchers.IO) {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Download failed: ${resp.code}")
                resp.body?.bytes() ?: ByteArray(0)
            }
        }
    }

    private fun extractPdfBlocking(uri: Uri): String = try {
        val input = requireContext().contentResolver.openInputStream(uri)
        val doc = com.tom_roush.pdfbox.pdmodel.PDDocument.load(input)
        val text = com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc)
        doc.close()
        text
    } catch (e: Exception) {
        "Failed to read PDF: ${e.message}"
    }

    private fun guessExtension(mime: String) = when (mime) {
        "application/pdf" -> ".pdf"
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx"
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> ".pptx"
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> ".xlsx"
        else -> ".bin"
    }

    private fun mimeShort(mime: String) = when (mime) {
        "application/pdf" -> "PDF"
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "DOCX"
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "PPTX"
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "XLSX"
        else -> "File"
    }


    private suspend fun exportGoogleDocToText(token: String, fileId: String): String {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId/export?mimeType=text/plain"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .build()
        return withContext(Dispatchers.IO) {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Export failed: ${resp.code}")
                resp.body?.string().orEmpty()
            }
        }
    }


    // Placeholder until Phase 2
    private fun loadGoogleDocs(account: GoogleSignInAccount) {
        Toast.makeText(requireContext(), "(TODO) Ready to fetch Google Docs...", Toast.LENGTH_SHORT)
            .show()
    }


    private val docPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { /* some providers don't grant persistable; ignore */
            }
            handlePickedDocument(uri)
        } else {
            toast("No file selected")
        }
    }

    private fun openFilePicker() {
        val mimeTypes = arrayOf(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",      // .docx
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",   // .pptx
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",           // .xlsx
            "text/csv",
            "image/*"
        )
        docPickerLauncher.launch(mimeTypes)
    }

    fun triggerPickImageFromFab() {
        Toast.makeText(requireContext(), "Image picker launched", Toast.LENGTH_SHORT).show()
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
        //  modelGroup = view.findViewById(R.id.modelSelection)
        pdfTextDisplay = view.findViewById(R.id.pdfTextDisplay)
        aiResponseDisplay = view.findViewById(R.id.aiResponseDisplay)
        promptInput = view.findViewById(R.id.promptInput)
        chkIncludeDoc = view.findViewById(R.id.includeDocumentText)
        chkIncludeAI = view.findViewById(R.id.includeAIResponse)
        btnExportTxt = view.findViewById(R.id.btnExportTxt)
        btnExportPdf = view.findViewById(R.id.btnExportPdf)
        exportShareButton = view.findViewById(R.id.btnExportShare)
        btnPickImage = view.findViewById(R.id.btnPickImage)

        val btnScanDocument = view.findViewById<Button>(R.id.btnScanDocument)
        btnScanDocument.setOnClickListener {
            triggerScanFromFab()  // Reuse your existing logic
        }
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

        val fabUploadScan = view.findViewById<FloatingActionButton>(R.id.fabUploadScan)
        fabUploadScan.setOnClickListener {
            showUploadOptionsDialog()

        }

        val btnOpenAIChat = view.findViewById<Button>(R.id.btnOpenAIChat)

        btnOpenAIChat.setOnClickListener {
            val intent = Intent(requireContext(), AIChatActivity::class.java)
            startActivity(intent)
        }



        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val usageText = view.findViewById<TextView>(R.id.tvUsageText)
        val usageBar = view.findViewById<ProgressBar>(R.id.usageProgressBar)
        val aiSourceText = view.findViewById<TextView>(R.id.tvAiSource)

        UsageManager.bindUsageUI(requireContext(), usageText, usageBar, aiSourceText)
    }


    private fun handlePickedDocument(uri: Uri) {
        selectedFileUri = uri.toString()

        // Resolve display name
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                selectedFileName = c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            }
        }

        val type = requireContext().contentResolver.getType(uri)
        val name = selectedFileName.lowercase()

        when {
            // PDF
            type?.contains("pdf") == true || name.endsWith(".pdf") -> extractTextFromPdf(uri)

            // DOCX
            type?.contains("wordprocessingml") == true || name.endsWith(".docx") -> {
                CoroutineScope(Dispatchers.IO).launch {
                    com.mmalyil.smartdocai.util.PoiKnobs.relax()  // <— add this
                    val text = runCatching {
                        com.mmalyil.smartdocai.util.PoiSetup.prepare()   // <<< add
                        com.mmalyil.smartdocai.util.DocumentUtils.extractTextFromDocx(requireContext(), uri)
                    }.getOrElse { "Failed to read DOCX: ${it.message}" }
                    withContext(Dispatchers.Main) { handleResult("DOCX", text) }
                }
            }

            // PPTX
            type?.contains("presentationml") == true || name.endsWith(".pptx") -> {
                CoroutineScope(Dispatchers.IO).launch {
                    com.mmalyil.smartdocai.util.PoiKnobs.relax()  // <— add this
                    val text = runCatching {
                        com.mmalyil.smartdocai.util.PoiSetup.prepare()   // <<< add
                        com.mmalyil.smartdocai.util.DocumentUtils.extractTextFromPptx(requireContext(), uri)
                    }.getOrElse { "Failed to read PPTX: ${it.message}" }
                    withContext(Dispatchers.Main) { handleResult("PPTX", text) }
                }
            }

            // XLSX (and old .xls)
            (type?.contains("spreadsheetml") == true || type == "application/vnd.ms-excel" ||
                    name.endsWith(".xlsx") || name.endsWith(".xls")) -> {
                CoroutineScope(Dispatchers.IO).launch {
                    com.mmalyil.smartdocai.util.PoiKnobs.relax()  // <— add this
                    val text = runCatching {
                        com.mmalyil.smartdocai.util.PoiSetup.prepare()   // <<< add
                        com.mmalyil.smartdocai.util.DocumentUtils.extractTextFromXlsx(requireContext(), uri)
                    }.getOrElse { "Failed to read XLSX: ${it.message}" }
                    withContext(Dispatchers.Main) { handleResult("XLSX", text) }
                }
            }

            // CSV
            type == "text/csv" || name.endsWith(".csv") -> {
                CoroutineScope(Dispatchers.IO).launch {
                    val text = runCatching {
                        com.mmalyil.smartdocai.util.DocumentUtils.extractTextFromCsv(
                            requireContext(), uri
                        )
                    }.getOrElse { "Failed to read CSV: ${it.message}" }
                    withContext(Dispatchers.Main) { handleResult("CSV", text) }
                }
            }

            else -> toast("Unsupported file: $type")
        }
    }

    // Single place to decide fallback vs success
    private fun handleResult(kind: String, text: String) {
        val isError = text.startsWith("Failed to read", ignoreCase = true) ||
                text.contains("null input stream", ignoreCase = true)

        if (isError) {
            // Show why it failed (temporary)
            Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show()
            showDocsFallbackDialog()
            return
        }

        extractedText = if (text.isBlank()) "[No text found in this $kind]" else text
        showExtractedText("$kind loaded")
        maybeAutoAnalyze()
    }

    private fun maybeAutoAnalyze() {
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
        val messages = listOf(
            ChatMessage(role = "system",
                content = "You are GPT-4o. Always reply clearly, concisely, and only in the same language as the user's input."
            ),
            ChatMessage("user", "Here is the document text:\n$extractedText"),
            ChatMessage("user", prompt)
        )

        val request = ChatRequest(
            model = modelName,
            messages = messages,
            temperature = 0.7
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reply = ChatApiHelper.chatService.getChatReply(request)

                android.util.Log.d(
                    "AI_DEBUG",
                    "Unified reply: content='${reply.content}', model='${reply.modelUsed}', rawLen=${reply.raw?.length ?: 0}"
                )

                val raw = reply.raw.orEmpty()

// ✅ Make it NON-NULL
                val aiReply: String =
                    reply.content?.takeIf { it.isNotBlank() }
                        ?: raw.let { if (it.isNotBlank()) extractAiText(it) else null }?.takeIf { it.isNotBlank() }
                        ?: "[No content returned from AI]"

// ✅ Also NON-NULL
                val modelUsed: String =
                    reply.modelUsed?.takeIf { it.isNotBlank() }
                        ?: run {
                            try {
                                org.json.JSONObject(raw).optString("model").takeIf { it.isNotBlank() }
                            } catch (_: Exception) { null }
                        }
                        ?: modelName

// …now use aiReply and modelUsed everywhere below


                // 3) Save to DB (IO thread ok)
                viewModel.insertFile(
                    fileUri = fileUri,
                    fileName = fileName,
                    content = extractedText,
                    aiResponse = aiReply
                )

                // 4) Track usage
                val ctx = requireContext().applicationContext
                val estimated = com.mmalyil.smartdocai.util.estimateTokens(prompt, aiReply)
                com.mmalyil.smartdocai.util.UsageManager.getPrefs(ctx)
                    .edit().putString("lastModelUsed", modelUsed).apply()

                when {
                    modelUsed.startsWith("gpt-4") ->
                        com.mmalyil.smartdocai.util.UsageManager.recordUsage(ctx, estimated)
                    modelUsed.startsWith("gpt-3.5") ->
                        com.mmalyil.smartdocai.util.UsageManager.incrementGpt35Usage(ctx, estimated)
                    else ->
                        com.mmalyil.smartdocai.util.UsageManager.recordUsage(ctx, estimated)
                }

                // 5) UI update on main
                withContext(Dispatchers.Main) {
                    aiResponseDisplay.text = aiReply
                    aiResponseDisplay.visibility = View.VISIBLE
                    aiAnswer = aiReply
                    toast("AI analysis complete and saved")
                }

            } catch (e: Exception) {
                val msg = e.localizedMessage ?: e.toString()
                android.util.Log.e("AI_ERROR", "Model: $modelName, Source: $source, Error: $msg", e)
                withContext(Dispatchers.Main) {
                    aiResponseDisplay.text = "Error: $msg"
                    aiResponseDisplay.visibility = View.VISIBLE
                    toast("AI Error: $msg")
                }
            }
        }
    }

    // Safe JSON extractor that checks multiple possible keys
    private fun extractAiTextSafe(raw: String): String {
        return try {
            val root = JSONObject(raw)

            // OpenAI-style choices[0].message.content
            root.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }

            // Alt formats
            root.optString("reply")?.takeIf { it.isNotBlank() }?.let { return it }
            root.optString("message")?.takeIf { it.isNotBlank() }?.let { return it }
            root.optString("output")?.takeIf { it.isNotBlank() }?.let { return it }

            // Fallback
            ""
        } catch (_: Exception) {
            ""
        }
    }

    fun extractAiText(rawJson: String?): String {
        if (rawJson.isNullOrBlank()) return "[No content returned]"
        return try {
            val root = JSONObject(rawJson)

            // 0) Common error envelope
            root.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }?.let { return it }

            // 1) OpenAI Chat format: choices[0].message.content
            root.optJSONArray("choices")?.let { choices ->
                if (choices.length() > 0) {
                    val first = choices.optJSONObject(0)
                    // a) chat format
                    first?.optJSONObject("message")?.optString("content")?.takeIf { it.isNotBlank() }?.let { return it }
                    // b) text format (some proxies put .text at the top level of a choice)
                    first?.optString("text")?.takeIf { it.isNotBlank() }?.let { return it }
                    // c) responses API style: choices[0].delta/content parts
                    first?.optJSONArray("content")?.let { parts ->
                        val sb = StringBuilder()
                        for (i in 0 until parts.length()) {
                            val part = parts.optJSONObject(i)
                            // text parts
                            part?.optString("text")?.takeIf { it.isNotBlank() }?.let { sb.append(it) }
                            // tool/text variants
                            part?.optJSONObject("text")?.optString("value")?.takeIf { it.isNotBlank() }?.let { sb.append(it) }
                        }
                        if (sb.isNotEmpty()) return sb.toString()
                    }
                }
            }

            // 2) OpenAI Responses API top-level: output_text OR content[0].text/value
            root.optJSONArray("output_text")?.let { arr ->
                if (arr.length() > 0) arr.optString(0)?.takeIf { it.isNotBlank() }?.let { return it }
            }
            root.optString("output_text")?.takeIf { it.isNotBlank() }?.let { return it }
            root.optJSONArray("content")?.let { contentArr ->
                if (contentArr.length() > 0) {
                    val first = contentArr.optJSONObject(0)
                    first?.optString("text")?.takeIf { it.isNotBlank() }?.let { return it }
                    first?.optJSONObject("text")?.optString("value")?.takeIf { it.isNotBlank() }?.let { return it }
                }
            }

            // 3) Anthropic-like: content: [{type:"text", text:"..."}]
            root.optJSONArray("content")?.let { arr ->
                val sb = StringBuilder()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i)
                    if (obj?.optString("type") == "text") {
                        obj.optString("text")?.takeIf { it.isNotBlank() }?.let { sb.append(it) }
                    }
                }
                if (sb.isNotEmpty()) return sb.toString()
            }

            // 4) Simple proxy: { content: "..." }
            root.optString("content")?.takeIf { it.isNotBlank() }?.let { return it }

            // 5) Some backends: { data: "..."} or { result: "..."}
            root.optString("data")?.takeIf { it.isNotBlank() }?.let { return it }
            root.optString("result")?.takeIf { it.isNotBlank() }?.let { return it }

            // 6) Last-ditch: find any non-empty string field
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = root.opt(k)
                if (v is String && v.isNotBlank() && k != "id" && k != "model") return v
            }

            "[No content in response]"
        } catch (e: Exception) {
            "[Parse error: ${e.message}]"
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
