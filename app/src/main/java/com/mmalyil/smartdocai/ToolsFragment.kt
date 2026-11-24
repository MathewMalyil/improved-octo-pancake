package com.mmalyil.smartdocai


import android.os.Bundle
import android.widget.TextView
import androidx.fragment.app.Fragment
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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
import com.mmalyil.smartdocai.util.DocumentEntitlement
import com.mmalyil.smartdocai.util.FilesHistory
import com.mmalyil.smartdocai.util.UsageManager
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.Scope
import com.mmalyil.smartdocai.util.DocumentUtils
import com.mmalyil.smartdocai.BuildConfig
import android.accounts.Account
import androidx.activity.result.PickVisualMediaRequest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import kotlin.io.use
import android.content.ClipData
import android.graphics.RectF
import android.os.StrictMode
import androidx.core.view.doOnPreDraw
import kotlinx.coroutines.flow.first
import android.util.Log
import com.google.android.material.snackbar.Snackbar
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

// ToolsFragment.kt (top)
import android.net.TrafficStats
import android.widget.ScrollView
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.google.android.gms.tasks.Tasks

import okhttp3.Interceptor
import java.util.concurrent.TimeUnit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.mmalyil.smartdocai.prefs.CoachPrefs
import kotlin.math.max
import kotlin.math.min



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

    private val TAG = "SmartDocAI/Tools"

    private lateinit var docPickerLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>

    // Single image picker (Photo Picker)
    private lateinit var imagePickerLauncher: androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest>

    // 1) Add a second launcher for legacy image picking via SAF (no permissions)
    private lateinit var imageOpenDocLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>

    private lateinit var btnImageToExcel: Button

    // New launcher for "Image → Excel"
    private lateinit var imageToExcelLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>

    /** Pick .xlsx then run Excel → Word (always discoverable CTA). */
    private lateinit var excelToWordLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>

    private var lastGeneratedExcelUri: Uri? = null

    /** Pages from last camera scan (for multi-page scan → PDF free cap). */
    private var lastScanPageCount: Int = 0

    private var extractedText = ""
    private var aiAnswer = ""
    private var selectedFileName = ""
    private var selectedFileUri = ""

    private val PICK_DOCUMENT_REQUEST_CODE = 1001
    private lateinit var scannedFileViewModel: ScannedFileViewModel


    private val RC_GOOGLE_SIGN_IN = 1001

    private val RC_RECOVER_AUTH = 1002

    // Quick AI UI

    private lateinit var promptContainer: View
    private lateinit var quickAiActions: View

    private lateinit var btnAiSummary: Button
    private lateinit var btnAiActions: Button
    private lateinit var btnAiKeyPoints: Button
    private lateinit var btnAiAsk: Button

    // Anti-double-call guards
    private var isAnalyzing = false
    private var lastAutoSummaryForUri: String? = null
    private var lastQuickActionAtMs: Long = 0L




    companion object {
        // Tune these as you like
        private const val MAX_PDF_SIZE_BYTES = 40L * 1024 * 1024 // 40 MB
        private const val MAX_PDF_PAGES = 200                     // cap pages to parse
        /** Image-PDF OCR fallback (SmartDocAI scans) — keep bounded for device OCR. */
        private const val MAX_PDF_OCR_PAGES = 20
        /** Below this many alphanumerics → treat extract as empty and OCR pages. */
        private const val MIN_USABLE_PDF_CHARS = 40
        private const val PDF_OCR_MAX_WIDTH = 1600
    }

    private var pendingAccount: GoogleSignInAccount? = null
    private var pendingToken: String? = null

    // Field (nullable instead of lateinit)
    private var loadingOverlay: View? = null
    private var loadingSpinner: ProgressBar? = null

    private var suppressGuideThisSession = false


    // Legacy spotlight host — only swept on teardown; Settings/first-run tips use AlertDialog.
    private var coachOverlay: com.mmalyil.smartdocai.ui.walkthrough.SpotlightOverlay? = null
    private var tipsDialog: AlertDialog? = null

    private var isCoachRunning = false
    // Call this from your Upload FAB (you can wire it like "Import from Google Docs")

    // ToolsFragment.kt (top, near other private vars)
    private var offeredDocxForUri: String? = null


    // Prevent double-tap DOCX generation
    private var isGeneratingDocx = false


    // For SAF save on Android 8/9
    private var pendingDocxToSave: File? = null

    private val saveDocxLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == android.app.Activity.RESULT_OK && res.data?.data != null && pendingDocxToSave != null) {
            val dest = res.data!!.data!!
            try {
                requireContext().contentResolver.openOutputStream(dest)?.use { out ->
                    pendingDocxToSave!!.inputStream().use { it.copyTo(out) }
                }
                toast("Saved")
            } catch (e: Exception) {
                toast("Save failed: ${e.message}")
            } finally {
                pendingDocxToSave = null
            }
        } else {
            pendingDocxToSave = null
        }
    }

    private fun promptSaveDocxViaSaf(file: File) {
        pendingDocxToSave = file
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            putExtra(Intent.EXTRA_TITLE, file.name) // keep .docx extension
        }
        saveDocxLauncher.launch(intent)
    }

    private fun showUploadOptionsDialog() {
        val base = mutableListOf("Upload File", "Pick Image", "Scan with Camera")
        if (BuildConfig.USE_GOOGLE_DOCS) base += "Import from Google Docs"
        val options = base.toTypedArray()

        val ctx = context ?: return
        AlertDialog.Builder(ctx)
            .setTitle("Choose Action")
            .setItems(options) { _, which ->
                when (options[which]) {
                    "Upload File" -> openFilePicker()
                    "Pick Image" -> pickImageHybrid()
                    "Scan with Camera" -> triggerScanFromFab()
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

    private fun extractPdfBlocking(uri: Uri): String {
        val appCtx = context?.applicationContext
            ?: return "Failed to read PDF: no context"

        val resolver = appCtx.contentResolver

        // 1) Try to get file size and enforce a hard limit
        var sizeBytes: Long = -1L
        try {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && c.moveToFirst()) {
                    sizeBytes = c.getLong(idx)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not determine PDF size for uri=$uri", t)
        }

        if (sizeBytes > 0 && sizeBytes > MAX_PDF_SIZE_BYTES) {
            val mb = sizeBytes / (1024 * 1024)
            return "Failed to read PDF: file is too large (${mb} MB)."
        }

        // 2) Copy to a temp file instead of loading from InputStream directly
        val tmp = File.createTempFile("smartdoc_pdf_", ".pdf", appCtx.cacheDir)

        return try {
            resolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                    }
                }
            } ?: return "Failed to read PDF: null input stream"

            // 3) Init PdfBox (safe to call more than once)
            PDFBoxResourceLoader.init(appCtx)

            // 4) Fast text extract; image PDFs (SmartDocAI scans) have no text layer.
            val extracted = com.tom_roush.pdfbox.pdmodel.PDDocument.load(tmp).use { doc ->
                val totalPages = doc.numberOfPages
                val stripper = com.tom_roush.pdfbox.text.PDFTextStripper().apply {
                    sortByPosition = false
                    startPage = 1
                    endPage = minOf(totalPages, MAX_PDF_PAGES)
                }
                stripper.getText(doc).orEmpty()
            }
            if (isUsablePdfText(extracted)) {
                Log.i(TAG, "PDF text extract ok chars=${extracted.length}")
                extracted
            } else {
                // 5) Fallback: render pages + ML Kit OCR (same path as image OCR).
                Log.i(
                    TAG,
                    "PDF text sparse (alpha=${countAlphaNum(extracted)}) — OCR fallback"
                )
                val ocr = ocrPdfFileBlocking(tmp)
                when {
                    ocr.isNotBlank() -> {
                        Log.i(TAG, "PDF OCR ok chars=${ocr.length}")
                        ocr
                    }
                    extracted.isNotBlank() -> extracted
                    else -> ""
                }
            }
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "OOM while reading PDF $uri", oom)
            "Failed to read PDF: document is too large or image-heavy for this device."
        } catch (e: Exception) {
            Log.e(TAG, "Error while reading PDF $uri", e)
            "Failed to read PDF: ${e.message}"
        } finally {
            // Clean up temp file
            try {
                tmp.delete()
            } catch (_: Exception) {
            }
        }
    }

    /** True when PDFBox already found enough real text — skip OCR. */
    private fun isUsablePdfText(text: String): Boolean {
        if (text.startsWith("Failed to read", ignoreCase = true)) return true
        return countAlphaNum(text) >= MIN_USABLE_PDF_CHARS
    }

    private fun countAlphaNum(text: String): Int =
        text.count { it.isLetterOrDigit() }

    /**
     * Render PDF pages with [PdfRenderer] and run ML Kit text recognition.
     * Used only when the PDF has no usable text layer (image scans from ImagePdfWriter).
     */
    private fun ocrPdfFileBlocking(file: File): String {
        val pfd = try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Exception) {
            Log.e(TAG, "ocrPdf open failed", e)
            return ""
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            PdfRenderer(pfd).use { renderer ->
                val pages = min(renderer.pageCount, MAX_PDF_OCR_PAGES)
                val sb = StringBuilder()
                for (i in 0 until pages) {
                    renderer.openPage(i).use { page ->
                        val pw = page.width.coerceAtLeast(1)
                        val ph = page.height.coerceAtLeast(1)
                        val scale = min(1f, PDF_OCR_MAX_WIDTH.toFloat() / pw)
                        val w = (pw * scale).toInt().coerceAtLeast(1)
                        val h = (ph * scale).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        try {
                            page.render(
                                bmp,
                                null,
                                null,
                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                            )
                            val vision = Tasks.await(
                                recognizer.process(InputImage.fromBitmap(bmp, 0))
                            )
                            val pageText = vision.text.trim()
                            if (pageText.isNotEmpty()) {
                                if (sb.isNotEmpty()) {
                                    sb.append("\n\n--- Page Break ---\n\n")
                                }
                                sb.append(pageText)
                            }
                        } finally {
                            if (!bmp.isRecycled) bmp.recycle()
                        }
                    }
                }
                sb.toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "ocrPdfFileBlocking failed", e)
            ""
        } finally {
            try {
                recognizer.close()
            } catch (_: Exception) {
            }
        }
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
        pickImageHybrid()
    }

    fun triggerImageToExcelFromHome() {
        val ctx = requireContext()
        DocumentEntitlement.requireOrPaywall(ctx, DocumentEntitlement.Feature.IMAGE_TO_EXCEL) {
            imageToExcelLauncher.launch(arrayOf("image/*"))
        }
    }

    /** Always-on Excel → Word: use loaded XLSX or pick one first. */
    fun triggerExcelToWord() {
        val ctx = context ?: return
        DocumentEntitlement.requireOrPaywall(ctx, DocumentEntitlement.Feature.AI_REPORT) {
            val uri = runCatching { Uri.parse(selectedFileUri) }.getOrNull()
            val name = selectedFileName.lowercase()
            val looksXlsx = !selectedFileUri.isBlank() &&
                (name.endsWith(".xlsx") ||
                    selectedFileUri.contains("spreadsheetml", ignoreCase = true))
            if (uri != null && looksXlsx) {
                showAiReportPromptOptions(uri)
            } else {
                toast("Pick an Excel (.xlsx) file")
                launchExcelPickerForWord()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1) Register the document picker EARLY (SAF)
        docPickerLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            android.util.Log.d(TAG, "OpenDocument result uri = $uri")
            if (uri != null) {
                try {
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) { /* some providers don't support persistable perms */
                }
                handlePickedDocument(uri)
            } else {
                toast("No file selected")
            }
        }

        // 2a) Register the modern Photo Picker (Android 13+, API 33+)
        imagePickerLauncher = registerForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            android.util.Log.d(TAG, "PickVisualMedia (single) uri = $uri")
            uri?.let { processImageForOCR(it) }
        }

        // 2b) Register the fallback image picker (SAF OpenDocument) for API < 33
        imageOpenDocLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            android.util.Log.d(TAG, "OpenDocument (image fallback) uri = $uri")
            if (uri != null) {
                try {
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) { /* ok if not supported */
                }
                processImageForOCR(uri)
            } else {
                toast("No image selected")
            }
        }


        // 🔹 NEW: dedicated picker for Image → Excel
        imageToExcelLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            android.util.Log.d(TAG, "OpenDocument (Image→Excel) uri = $uri")
            if (uri != null) {
                try {
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) { /* some providers don't support it */
                }
                processImageToExcel(uri)
            } else {
                toast("No image selected")
            }
        }

        excelToWordLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            android.util.Log.d(TAG, "OpenDocument (Excel→Word) uri = $uri")
            if (uri != null) {
                try {
                    requireContext().contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                }
                // Load sheet text for Analyse context, then offer AI Word report.
                handlePickedDocument(uri)
                DocumentEntitlement.requireOrPaywall(
                    requireContext(),
                    DocumentEntitlement.Feature.AI_REPORT
                ) {
                    showAiReportPromptOptions(uri)
                }
            } else {
                toast("No Excel file selected")
            }
        }

        // A) toolsFabRequest
        parentFragmentManager.setFragmentResultListener("toolsFabRequest", this) { _, bundle ->
            when (bundle.getString("action")) {
                "upload" -> triggerUploadFromFab()
                "scan" -> triggerScanFromFab()
                "pickImage" -> triggerPickImageFromFab()
                "imageToExcel" -> triggerImageToExcelFromHome()
                "excelToWord" -> triggerExcelToWord()
            }
            parentFragmentManager.clearFragmentResult("toolsFabRequest")
        }

        // B) replayCoach — Settings “Analyse guided tips”
        parentFragmentManager.setFragmentResultListener("replayCoach", this) { _, bundle ->
            if (bundle.getBoolean("replay", false)) {
                viewLifecycleOwner.lifecycleScope.launch {
                    com.mmalyil.smartdocai.prefs.CoachPrefs.setCoachSeen(requireContext(), false)
                    // force=true so we don’t race DataStore “seen” still true
                    view?.let { showWalkthroughNow(it, force = true) }
                }
            }
        }


        // 🔔 Handle files coming from MainActivity (Open with / Share)
        parentFragmentManager.setFragmentResultListener("externalFile", this) { _, b ->
            val sharedText = b.getString("text")
            val uriStr = b.getString("uri")
            val mime = b.getString("mime") ?: "*/*"

            when {
                // Case A: shared text (ACTION_SEND with EXTRA_TEXT)
                !sharedText.isNullOrBlank() -> {
                    handleSharedText(sharedText)
                }

                // Case B: file/URI (ACTION_VIEW or ACTION_SEND with EXTRA_STREAM)
                !uriStr.isNullOrBlank() -> {
                    val uri = Uri.parse(uriStr)
                    // visible feedback so it never feels unresponsive
                    toast("Opening file…")
                    handlePickedDocument(uri)
                }

                else -> {
                    toast("Nothing to open")
                }
            }

            // Clear so it doesn't re-trigger on config change
            parentFragmentManager.clearFragmentResult("externalFile")
        }


    }

    private fun pickImageHybrid() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            imagePickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } else {
            imageOpenDocLauncher.launch(arrayOf("image/*"))
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

        btnImageToExcel = view.findViewById(R.id.btnImageToExcel)   // 🔹 NEW

        val btnScanDocument = view.findViewById<Button>(R.id.btnScanDocument)
        btnScanDocument.setOnClickListener {
            stopWalkthroughIfRunning()
            triggerScanFromFab()  // Reuse your existing logic
        }
        scannedFileViewModel = ScannedFileViewModel(
            ScannedFileRepository(AppDatabase.getDatabase(requireContext()).scannedFileDao())
        )

        selectPdfButton.setOnClickListener {
            stopWalkthroughIfRunning()
            openFilePicker()
        }


        promptContainer = view.findViewById(R.id.promptContainer)
        quickAiActions = view.findViewById(R.id.quickAiActions)

        btnAiSummary = view.findViewById(R.id.btnAiSummary)
        btnAiActions = view.findViewById(R.id.btnAiActions)
        btnAiKeyPoints = view.findViewById(R.id.btnAiKeyPoints)
        btnAiAsk = view.findViewById(R.id.btnAiAsk)

// initial state

        promptContainer.visibility = View.GONE
        quickAiActions.visibility = View.GONE



        quickAiActions.visibility = View.VISIBLE

        
        btnAiSummary.setOnClickListener {
            stopWalkthroughIfRunning()
            runQuickAction("summary")
        }

        btnAiActions.setOnClickListener {
            stopWalkthroughIfRunning()
            runQuickAction("actions")
        }

        btnAiKeyPoints.setOnClickListener {
            stopWalkthroughIfRunning()
            runQuickAction("keypoints")
        }

        btnAiAsk.setOnClickListener {
            stopWalkthroughIfRunning()
            // show prompt input + focus
            promptContainer.visibility = View.VISIBLE
            promptInput.requestFocus()
            toast("Type your question, then tap Analyze")
        }






        analyzeButton.setOnClickListener {
            stopWalkthroughIfRunning()
            val prompt = promptInput.text.toString().trim()

            if (extractedText.isEmpty()) {
                toast("Please select a file or extract text first")
                return@setOnClickListener
            }

            if (prompt.isEmpty()) {
                showPromptSheet { picked ->
                    promptInput.setText(picked)
                    analyzeSmartlyWithQuota(
                        context = requireContext(),
                        prompt = picked,
                        extractedText = extractedText,
                        fileName = selectedFileName,
                        fileUri = selectedFileUri,
                        viewModel = scannedFileViewModel
                    )
                }
            } else {
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

// Optional: press-and-hold to open suggestions anytime
        analyzeButton.setOnLongClickListener {
            if (extractedText.isEmpty()) {
                toast("Load a document first")
                return@setOnLongClickListener true
            }

            showPromptSheet { picked ->
                promptInput.setText(picked)

                // ✅ Run immediately (no second tap)
                analyzeSmartlyWithQuota(
                    context = requireContext(),
                    prompt = picked,
                    extractedText = extractedText,
                    fileName = selectedFileName,
                    fileUri = selectedFileUri,
                    viewModel = scannedFileViewModel
                )
            }

            true
        }

        btnExportTxt.setOnClickListener {
            if (!chkIncludeDoc.isChecked && !chkIncludeAI.isChecked) {
                toast("Please select at least one option to export")
                return@setOnClickListener
            }
            val ctx = context ?: return@setOnClickListener
            DocumentEntitlement.requireOrPaywall(ctx, DocumentEntitlement.Feature.OTHER_EXPORT) {
                exportAsTxt(chkIncludeDoc.isChecked, chkIncludeAI.isChecked)
            }
        }

        btnExportPdf.setOnClickListener {
            if (!chkIncludeDoc.isChecked && !chkIncludeAI.isChecked) {
                toast("Please select at least one option to export")
                return@setOnClickListener
            }
            val ctx = context ?: return@setOnClickListener
            val multiPageScan = lastScanPageCount > 1 ||
                extractedText.contains("--- Page Break ---")
            val feature = if (multiPageScan) {
                DocumentEntitlement.Feature.SCAN_TO_PDF
            } else {
                DocumentEntitlement.Feature.OTHER_EXPORT
            }
            DocumentEntitlement.requireOrPaywall(ctx, feature) {
                exportAsPdf(chkIncludeDoc.isChecked, chkIncludeAI.isChecked, feature)
            }
        }

        exportShareButton.setOnClickListener {
            stopWalkthroughIfRunning()

            val includeDoc = chkIncludeDoc.isChecked
            val includeAI = chkIncludeAI.isChecked

            if (!includeDoc && !includeAI) {
                toast("Please select at least one option to share")
                return@setOnClickListener
            }

            val content = buildExportContent(includeDoc, includeAI).trim()
            if (content.isBlank()) {
                toast("Nothing to share yet")
                return@setOnClickListener
            }

            // Persist a real TXT into Files (valid path), then share that file — no text-only stub.
            val ctx = context ?: return@setOnClickListener
            DocumentEntitlement.requireOrPaywall(ctx, DocumentEntitlement.Feature.OTHER_EXPORT) {
                shareAndPersistTxt(content, aiForFiles = aiBlobForFiles(includeAI))
            }
        }

        val btnExportDocx = view.findViewById<Button>(R.id.btnExportDocx)
        btnExportDocx.visibility = View.VISIBLE
        btnExportDocx.setOnClickListener {
            stopWalkthroughIfRunning()
            triggerExcelToWord()
        }
        view.findViewById<Button>(R.id.btnExcelToWord).setOnClickListener {
            stopWalkthroughIfRunning()
            triggerExcelToWord()
        }



        chkIncludeDoc.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked && !chkIncludeAI.isChecked) chkIncludeDoc.isChecked = true
        }
        chkIncludeAI.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked && !chkIncludeDoc.isChecked) chkIncludeAI.isChecked = true
        }

        btnPickImage.setOnClickListener {
            stopWalkthroughIfRunning()
            pickImageHybrid()
        }

// new Image → Excel behavior
        btnImageToExcel.setOnClickListener {
            stopWalkthroughIfRunning()
            val ctx = context ?: return@setOnClickListener
            DocumentEntitlement.requireOrPaywall(ctx, DocumentEntitlement.Feature.IMAGE_TO_EXCEL) {
                imageToExcelLauncher.launch(arrayOf("image/*"))
            }
        }

        view.findViewById<Button>(R.id.btnCombinePdfs).setOnClickListener {
            stopWalkthroughIfRunning()
            startActivity(Intent(requireContext(), CombinePdfActivity::class.java))
        }

        view.findViewById<Button>(R.id.btnScanId).setOnClickListener {
            stopWalkthroughIfRunning()
            startActivity(
                Intent(requireContext(), ScanActivity::class.java).apply {
                    putExtra(ScanActivity.EXTRA_START_MODE, ScanActivity.MODE_ID)
                }
            )
        }


        val fabUploadScan = view.findViewById<FloatingActionButton>(R.id.fabUploadScan)
        fabUploadScan.setOnClickListener {
            stopWalkthroughIfRunning()
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


        val scroll = view.findViewById<ScrollView>(R.id.scrollView)

        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom

            val act = activity
            val bottomNav = act?.findViewById<View?>(R.id.bottomNav)
            val banner = act?.findViewById<View?>(R.id.adViewBanner)

            val navH = bottomNav?.height ?: 0
            val bannerH = if (banner?.visibility == View.VISIBLE) banner.height else 0

            v.updatePadding(
                bottom = dpToPx(16) + max(ime, bars) + navH + bannerH
            )
            insets
        }

// ensures insets get applied after first layout pass
        scroll.doOnPreDraw {
            ViewCompat.requestApplyInsets(scroll)
        }


        view.findViewById<View>(R.id.fabUploadScan)?.visibility = View.GONE


        val usageText = view.findViewById<TextView>(R.id.tvUsageText)
        val usageBar = view.findViewById<ProgressBar>(R.id.usageProgressBar)
        val aiSourceText = view.findViewById<TextView>(R.id.tvAiSource)

        // Bind once to show current values
        val ctx = context ?: return
        UsageManager.bindUsageUI(
            context = ctx,
            usageTextView = usageText,
            progressBar = usageBar,
            aiSourceTextView = aiSourceText
        )
        refreshDocCaps(view)

        loadingOverlay = view.findViewById(R.id.loadingOverlay)
        loadingSpinner = view.findViewById(R.id.loadingSpinner)

        // Brief flash on open — must clear clickable when hiding or it can eat taps.
        loadingOverlay?.apply {
            visibility = View.VISIBLE
            isClickable = true
            isFocusable = true
            postDelayed({
                visibility = View.GONE
                isClickable = false
                isFocusable = false
                isFocusableInTouchMode = false
            }, 1000)
        }


        //UsageManager.bindUsageUI(requireContext(), usageText, usageBar, aiSourceText)


        view.doOnPreDraw {
            showWalkthroughNow(view)
        }

    }

    override fun onResume() {
        super.onResume()

        view?.let { v ->
            refreshDocCaps(v)
            val ctx = context ?: return@let
            val prefs = ctx.getSharedPreferences("exports", Context.MODE_PRIVATE)

            val uriStr = prefs.getString("last_excel_uri", null)
            val pending = prefs.getBoolean("excel_pending_open", false)

            if (uriStr != null && pending) {
                val uri = Uri.parse(uriStr)

                UsageManager.bindUsageUI(
                    context = ctx,
                    usageTextView = v.findViewById(R.id.tvUsageText),
                    progressBar = v.findViewById(R.id.usageProgressBar),
                    aiSourceTextView = v.findViewById(R.id.tvAiSource)
                )





                Snackbar.make(
                    v,
                    "Excel file created",
                    Snackbar.LENGTH_LONG
                )
                    .setAction("Open") {
                        openExcel(uri)
                    }
                    .show()

                // 🔥 clear flag so it NEVER repeats
                prefs.edit()
                    .putBoolean("excel_pending_open", false)
                    .apply()
            }
        }
    }

    override fun onDestroyView() {
        view?.handler?.removeCallbacksAndMessages(null)
        tearDownAllCoachLayers()
        super.onDestroyView()
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()

    // Safe show/hide
    private fun showLoading(show: Boolean) {
        val overlay = loadingOverlay ?: return
        // always run on main
        view?.post {
            if (show) {
                overlay.visibility = View.VISIBLE
                overlay.bringToFront()
            } else {
                overlay.visibility = View.GONE
                overlay.isClickable = false
                overlay.isFocusable = false
                overlay.isFocusableInTouchMode = false

            }
        }
    }

    private fun handlePickedDocument(uri: Uri) {
        // 🔐 Ultra defensive: bail out if fragment not ready
        if (!isAdded) {
            showLoading(false)
            return
        }
        val ctx = context ?: run {
            showLoading(false); return
        }

        showLoading(true)
        selectedFileUri = uri.toString()

        val resolver = ctx.contentResolver

        // ✅ Resolve display name safely, with fallbacks & SecurityException guard
        var nameFromCursor: String? = null

        if ("content".equals(uri.scheme, ignoreCase = true)) {
            try {
                resolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val nameCol = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameCol >= 0) {
                            nameFromCursor = c.getString(nameCol)
                        }
                    }
                }
            } catch (se: SecurityException) {
                Log.w(TAG, "Cannot query display name for uri=$uri", se)
            } catch (t: Throwable) {
                Log.w(TAG, "Unexpected error reading metadata for uri=$uri", t)
            }
        }

        selectedFileName = nameFromCursor
            ?: uri.lastPathSegment
                    ?: "Document"

        // 🔐 getType() can also throw SecurityException with some providers
        val type = try {
            resolver.getType(uri)
        } catch (se: SecurityException) {
            Log.w(TAG, "getType() denied for uri=$uri", se)
            null
        } catch (t: Throwable) {
            Log.w(TAG, "getType() failed for uri=$uri", t)
            null
        }

        val name = selectedFileName.lowercase()

        fun onDone(kind: String, text: String) {
            // If view is gone (user navigated away), don’t touch UI
            if (!isAdded || view == null) {
                extractedText = text
                showLoading(false)
                return
            }
            handleResult(kind, text)
            showLoading(false)

            quickAiActions.visibility = View.VISIBLE

            promptContainer.visibility = View.GONE
            promptInput.setText("")

        }

        when {
            type?.startsWith("image/") == true ||
                    name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") -> {

                viewLifecycleOwner.lifecycleScope.launch {
                    processImageForOCR(uri)
                    showLoading(false)
                }
            }

            type?.contains("pdf") == true || name.endsWith(".pdf") -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val text = withContext(Dispatchers.IO) {
                            relaxVmPolicyDuring {
                                extractPdfBlocking(uri)
                            }
                        }
                        onDone("PDF", text)
                    } catch (e: Exception) {
                        showLoading(false)
                        toast("PDF read error: ${e.message}")
                    }
                }
            }

            type?.contains("wordprocessingml") == true || name.endsWith(".docx") -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val appCtx = context?.applicationContext
                            ?: return@launch.also { showLoading(false) }

                        val text = withContext(Dispatchers.IO) {
                            relaxVmPolicyDuring {
                                com.mmalyil.smartdocai.util.PoiKnobs.relax()
                                com.mmalyil.smartdocai.util.PoiSetup.prepare()
                                DocumentUtils.extractTextFromDocx(appCtx, uri)
                            }
                        }
                        onDone("DOCX", text)
                    } catch (e: Exception) {
                        showLoading(false)
                        toast("DOCX read error: ${e.message}")
                    }
                }
            }

            type?.contains("presentationml") == true || name.endsWith(".pptx") -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val appCtx = context?.applicationContext
                            ?: return@launch.also { showLoading(false) }

                        val text = withContext(Dispatchers.IO) {
                            relaxVmPolicyDuring {
                                com.mmalyil.smartdocai.util.PoiKnobs.relax()
                                com.mmalyil.smartdocai.util.PoiSetup.prepare()
                                DocumentUtils.extractTextFromPptx(appCtx, uri)
                            }
                        }
                        onDone("PPTX", text)
                    } catch (e: Exception) {
                        showLoading(false)
                        toast("PPTX read error: ${e.message}")
                    }
                }
            }

            (type?.contains("spreadsheetml") == true || name.endsWith(".xlsx")) -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val appCtx = context?.applicationContext
                            ?: return@launch.also { showLoading(false) }

                        val text = withContext(Dispatchers.IO) {
                            relaxVmPolicyDuring {
                                com.mmalyil.smartdocai.util.PoiKnobs.relax()
                                com.mmalyil.smartdocai.util.PoiSetup.prepare()
                                DocumentUtils.extractTextFromXlsx(appCtx, uri)
                            }
                        }
                        onDone("XLSX", text)
                    } catch (t: Throwable) {
                        showLoading(false)
                        toast("XLSX read error: ${t.message ?: "Unknown error"}")
                    }
                }
            }

            (type == "application/vnd.ms-excel" || name.endsWith(".xls")) -> {
                showLoading(false)
                toast("Legacy .xls files are not supported yet. Please save as .xlsx and try again.")
            }

            else -> {
                showLoading(false)
                toast("Unsupported file: $type")
            }
        }
    }

    private fun exportXlsxAiReportNow(xlsxUri: Uri, customPrompt: String? = null) {
        if (isGeneratingDocx) {
            toast("Already generating…"); return
        }
        isGeneratingDocx = true
        showLoading(true)

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val title =
                    selectedFileName.substringBeforeLast('.', selectedFileName).ifBlank { "Report" }

                val result = com.mmalyil.smartdocai.export.ReportExporter.generateXlsxAiReport(
                    context = requireContext(),
                    srcUri = xlsxUri,
                    titleHint = title,
                    proxy = com.mmalyil.smartdocai.api.ChatApiHelper.chatService,
                    customPrompt = customPrompt
                )

                withContext(Dispatchers.Main) {
                    showLoading(false)

                    result.onSuccess { generated ->
                        context?.let {
                            DocumentEntitlement.recordUse(it, DocumentEntitlement.Feature.AI_REPORT)
                            refreshDocCaps()
                        }

                        if (generated.length() <= 0L) {
                            toast("Report file is empty"); return@onSuccess
                        }

                        val ctx = requireContext()
                        // Always persist under FileProvider-backed exports/ so Files can open it.
                        val file = persistExportFile(ctx, generated)
                        val fileProviderUri = uriForExportFile(ctx, file)

                        // --- choose target URI: Q+ => Downloads copy, else FileProvider ---
                        val downloadsUri: Uri? =
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
                                runCatching { saveDocxToDownloadsQ(ctx, file) }.getOrNull()
                            else null

                        // Room must reference a readable app file (not a missing path / stub).
                        val historyUri = fileProviderUri
                        val targetUri = downloadsUri ?: fileProviderUri

                        // DOCX is the AI deliverable — don't invent a fake aiResponse string.
                        FilesHistory.saveAsync(
                            context = ctx,
                            fileName = file.name,
                            fileUri = historyUri.toString(),
                            content = "Excel → Word AI report — open this DOCX from Files.",
                            aiResponse = null,
                            scope = viewLifecycleOwner.lifecycleScope,
                            anchor = view
                        )

                        // --- base VIEW intent + ClipData for legacy Word ---
                        val openIntent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(
                                targetUri,
                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                            )
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            clipData = ClipData.newUri(
                                requireContext().contentResolver, file.name, targetUri
                            )
                        }

                        // explicit grants for all handlers (Android 8/9 quirk)
                        val pm = requireContext().packageManager
                        pm.queryIntentActivities(openIntent, 0).forEach { ri ->
                            try {
                                requireContext().grantUriPermission(
                                    ri.activityInfo.packageName,
                                    targetUri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            } catch (_: Exception) {
                            }
                        }
                        // common apps (best-effort)
                        try {
                            requireContext().grantUriPermission(
                                "com.microsoft.office.word",
                                targetUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                            requireContext().grantUriPermission(
                                "com.google.android.apps.docs",
                                targetUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        } catch (_: Exception) {
                        }

                        fun openViaSendFallback() {
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type =
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                putExtra(Intent.EXTRA_STREAM, targetUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                clipData = ClipData.newUri(
                                    requireContext().contentResolver,
                                    file.name,
                                    targetUri
                                )
                            }
                            try {
                                startActivity(Intent.createChooser(send, "Share/Open with"))
                            } catch (_: Exception) {
                                // Guaranteed path on 8/9
                                promptSaveDocxViaSaf(file)
                            }
                        }

                        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                            // ANDROID 8/9: just ask user where to save the DOCX
                            showOpenSaveSheetForLegacy(file)
                        } else {
                            // ANDROID 10+: keep the existing snackbar+Open behavior
                            val sb = com.google.android.material.snackbar.Snackbar
                                .make(
                                    requireView(),
                                    if (downloadsUri != null) "Saved to Downloads" else "AI Report ready",
                                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                                )
                                .setAction("Open") {
                                    try {
                                        startActivity(Intent.createChooser(openIntent, "Open with"))
                                    } catch (_: Exception) {
                                        openViaSendFallback()
                                    }
                                }
                            sb.anchorView = requireView().findViewById(R.id.fabUploadScan)
                            sb.show()

                        }

                        // (Optional) add to your in-app Saved list here if you want

                    }.onFailure { e ->
                        Log.e(TAG, "AI report export failed", e)
                        val root = view
                        if (root != null) {
                            com.google.android.material.snackbar.Snackbar
                                .make(
                                    root,
                                    "Couldn't create the Word report. Try again.",
                                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                                )
                                .show()
                        } else {
                            toast("Couldn't create the Word report. Try again.")
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Log.e(TAG, "AI report export error", e)
                    toast("Couldn't create the Word report. Try again.")
                }
            } finally {
                withContext(Dispatchers.Main) { isGeneratingDocx = false }
            }
        }
    }

    @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.Q)
    private fun saveDocxToDownloadsQ(context: Context, src: File): Uri? {
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, src.name) // must end with .docx
            put(
                android.provider.MediaStore.Downloads.MIME_TYPE,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            )
            put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri =
            resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null

        resolver.openOutputStream(uri)?.use { out ->
            src.inputStream().use { it.copyTo(out) }
        }

        values.clear()
        values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
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

        if (kind.equals("XLSX", true)) {
            // Show this offer only once per file
            if (offeredDocxForUri != selectedFileUri) {
                offeredDocxForUri = selectedFileUri

                val createAction = {
                    runCatching { Uri.parse(selectedFileUri) }.getOrNull()
                        ?.let { exportXlsxAiReportNow(it) }
                        ?: toast("Missing XLSX URI")
                }

                val sb = com.google.android.material.snackbar.Snackbar
                    .make(
                        requireView(),
                        "Export Word (AI Report)?",
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    )
                    .setAction("Create") { createAction() }

                // lift above FAB so it's not hidden
                sb.anchorView = requireView().findViewById(R.id.fabUploadScan)
                sb.show()
            }
        }



        extractedText = if (text.isBlank()) "[No text found in this $kind]" else text
        showExtractedText("$kind loaded")

        quickAiActions.visibility = View.VISIBLE

        // Excel → Word stays always visible (pick Excel if none loaded).
        view?.findViewById<View>(R.id.btnExportDocx)?.visibility = View.VISIBLE
        view?.findViewById<View>(R.id.btnExcelToWord)?.visibility = View.VISIBLE

        maybeAutoAnalyze()

    }

    private fun refreshDocCaps(root: View? = view) {
        val tv = root?.findViewById<TextView>(R.id.tvDocCaps) ?: return
        val ctx = context ?: return
        if (DocumentEntitlement.isDocumentPro(ctx)) {
            tv.text = "Document Pro · unlimited Image→Excel, Word reports & exports"
            return
        }
        val img = DocumentEntitlement.remainingToday(ctx, DocumentEntitlement.Feature.IMAGE_TO_EXCEL)
        val word = DocumentEntitlement.remainingToday(ctx, DocumentEntitlement.Feature.AI_REPORT)
        val exp = DocumentEntitlement.remainingToday(ctx, DocumentEntitlement.Feature.OTHER_EXPORT)
        val imgLim = DocumentEntitlement.Feature.IMAGE_TO_EXCEL.dailyFreeLimit
        val wordLim = DocumentEntitlement.Feature.AI_REPORT.dailyFreeLimit
        val expLim = DocumentEntitlement.Feature.OTHER_EXPORT.dailyFreeLimit
        tv.text =
            "Free today · Image→Excel $img/$imgLim left · Word report $word/$wordLim left · Exports $exp/$expLim left"
    }


    private fun runQuickAction(type: String, isAuto: Boolean = false) {
        if (extractedText.isBlank()) {
            toast("Load a document first")
            return
        }

        // throttle spam taps
        val now = System.currentTimeMillis()
        if (!isAuto && now - lastQuickActionAtMs < 700) return
        lastQuickActionAtMs = now

        // prevent collisions with “Analyze” button / auto analyze
        if (isAnalyzing) {
            toast("Already analyzing…")
            return
        }

        val prompt = when (type) {
            "summary" -> "Summarize this document in 5 bullet points. Keep it concise."
            "actions" -> "List action items. Use bullets. Include owner and deadline if present."
            "keypoints" -> "Extract key points. Group them by topic. Use concise bullets."
            else -> "Summarize this document."
        }

        // put prompt into the input (nice UX)
        promptInput.setText(prompt)
        promptContainer.visibility = View.GONE

        // kick off analysis
        analyzeSmartlyWithQuota(
            context = requireContext(),
            prompt = prompt,
            extractedText = extractedText,
            fileName = selectedFileName,
            fileUri = selectedFileUri,
            viewModel = scannedFileViewModel
        )
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
        showLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = context ?: return@launch.also { showLoading(false) }
            val text = withContext(Dispatchers.IO) {
                try {
                    val img = InputImage.fromFilePath(ctx, uri)
                    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                        .process(img).await()
                        .text
                } catch (e: Exception) {
                    "[OCR failed: ${e.message}]"
                }
            }
            if (!isAdded || !viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                showLoading(false); return@launch
            }
            extractedText = text
            pdfTextDisplay.text = text

            analyzeButton.isEnabled = text.isNotBlank()                 // ✅
            toast("Text extracted from image")
            showLoading(false)
        }
    }


    private fun processImageToExcel(uri: Uri) {
        val ctx = context ?: return
        if (!DocumentEntitlement.canUse(ctx, DocumentEntitlement.Feature.IMAGE_TO_EXCEL)) {
            DocumentEntitlement.openPaywall(
                ctx,
                DocumentEntitlement.freeLimitMessage(ctx, DocumentEntitlement.Feature.IMAGE_TO_EXCEL)
            )
            return
        }

        showLoading(true)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val appCtx = context?.applicationContext ?: return@launch.also { showLoading(false) }

                val xlsxFile = withContext(Dispatchers.IO) {
                    ImageToExcelProcessor.processImageUriToExcel(appCtx, uri)
                }

                if (!isAdded || view == null ||
                    !viewLifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                ) {
                    showLoading(false)
                    return@launch
                }

                if (!xlsxFile.exists() || xlsxFile.length() <= 0L) {
                    toast("Couldn't create Excel from this image. Try a clearer photo.")
                    return@launch
                }

                DocumentEntitlement.recordUse(appCtx, DocumentEntitlement.Feature.IMAGE_TO_EXCEL)

                val shareUri = getFileUri(appCtx, xlsxFile.name, subDir = "exports")

                try {
                    appCtx.grantUriPermission(
                        "com.microsoft.office.excel",
                        shareUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    appCtx.grantUriPermission(
                        "com.google.android.apps.docs",
                        shareUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }

                withContext(Dispatchers.Main) {
                    if (!isAdded || view == null) return@withContext
                    refreshDocCaps()

                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(
                            shareUri,
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        )
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        clipData = ClipData.newUri(appCtx.contentResolver, xlsxFile.name, shareUri)
                    }

                    try {
                        startActivity(Intent.createChooser(intent, "Open Excel with"))
                    } catch (_: Exception) {
                        toast("Excel created. No app found to open it.")
                    }

                    lastGeneratedExcelUri = shareUri
                    saveLastExcelUri(shareUri)

                    FilesHistory.saveAsync(
                        context = requireContext(),
                        fileName = xlsxFile.name,
                        fileUri = shareUri.toString(),
                        content = "Image → Excel spreadsheet",
                        aiResponse = null,
                        scope = viewLifecycleOwner.lifecycleScope,
                        anchor = view
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Image→Excel failed", e)
                toast("Image → Excel failed. Try another image.")
            } finally {
                showLoading(false)
            }
        }
    }

    private fun saveLastExcelUri(uri: Uri) {
        requireContext()
            .getSharedPreferences("exports", Context.MODE_PRIVATE)
            .edit()
            .putString("last_excel_uri", uri.toString())
            .putBoolean("excel_pending_open",true)
            .apply()
    }
    private fun openExcel(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                uri,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(Intent.createChooser(intent, "Open Excel with"))
        } catch (e: Exception) {
            toast("No app found to open Excel file")
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
        // Free AI only: monthly token cap via UsageManager (~30k). No Pro / paid AI path.
        val freeUsed = UsageManager.getGroqUsed(context)
        val freeCap = UsageManager.getGroqCap(context)

        if (freeUsed < freeCap) {
            GPTUsageManager.incrementUsage(context, "groq")
            analyzeWithAI(
                prompt,
                com.mmalyil.smartdocai.util.FREE_CHAT_MODEL,
                "groq",
                extractedText,
                fileName,
                fileUri,
                viewModel
            )
        } else {
            Toast.makeText(
                context,
                "Monthly free AI limit reached. It resets next month.",
                Toast.LENGTH_LONG
            ).show()
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
        // ✅ Prevent double calls
        if (isAnalyzing) return
        isAnalyzing = true
        showLoading(true)

                val messages = listOf(
            ChatMessage(
                role = "system",
                content = "You are a helpful document assistant. Reply clearly and concisely in the same language as the user."
            ),
            ChatMessage("user", "Here is the document text:\n$extractedText"),
            ChatMessage("user", prompt)
        )

        val request = ChatRequest(
            model = modelName,
            messages = messages,
            temperature = 0.7
        )

        // ✅ Use lifecycleScope so coroutine cancels automatically when Fragment is destroyed
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val reply = ChatApiHelper.chatService.getChatReply(request)

                Log.d(
                    "AI_DEBUG",
                    "Unified reply: content='${reply.content}', model='${reply.modelUsed}', rawLen=${reply.raw?.length ?: 0}"
                )

                val raw = reply.raw.orEmpty()

                // ✅ NON-NULL AI reply (never show raw provider JSON)
                var aiReply: String =
                    reply.content?.takeIf { it.isNotBlank() }
                        ?: raw.let { if (it.isNotBlank()) extractAiText(it) else null }
                            ?.takeIf { it.isNotBlank() }
                        ?: ""

                if (aiReply.isBlank() && raw.isNotBlank()) {
                    aiReply = com.mmalyil.smartdocai.util.friendlyAiErrorMessage(raw)
                } else if (aiReply.trim().startsWith("{") && aiReply.contains("\"error\"")) {
                    aiReply = com.mmalyil.smartdocai.util.friendlyAiErrorMessage(aiReply)
                }
                if (aiReply.isBlank()) {
                    aiReply = "No response from AI. Please try again."
                }

                val isErrorReply = aiReply.startsWith("AI ") ||
                    aiReply.startsWith("Network ") ||
                    aiReply.startsWith("No response")

                // ✅ NON-NULL model used
                val modelUsed: String =
                    reply.modelUsed?.takeIf { it.isNotBlank() }
                        ?: run {
                            try {
                                JSONObject(raw).optString("model").takeIf { it.isNotBlank() }
                            } catch (_: Exception) {
                                null
                            }
                        }
                        ?: modelName

                // ✅ Save only if meaningful success
                if (!isErrorReply) {
                    viewModel.insertFile(
                        fileUri = fileUri,
                        fileName = fileName,
                        content = extractedText,
                        aiResponse = aiReply
                    )
                }

                // ✅ Track usage SAFELY (avoid requireContext crash if detached)
                val appCtx = context?.applicationContext
                if (appCtx != null && !isErrorReply) {
                    val estimated = com.mmalyil.smartdocai.util.estimateTokens(prompt, aiReply)
                    com.mmalyil.smartdocai.util.UsageManager.getPrefs(appCtx)
                        .edit().putString("lastModelUsed", modelUsed).apply()
                    com.mmalyil.smartdocai.util.UsageManager.recordGroqUsage(appCtx, estimated)
                }

                // ✅ UI update on main (only if fragment still active)
                withContext(Dispatchers.Main) {
                    if (!isAdded || view == null) return@withContext

                    aiResponseDisplay.text = aiReply
                    aiResponseDisplay.visibility = View.VISIBLE
                    aiAnswer = aiReply

                    quickAiActions.visibility = View.VISIBLE
                    promptContainer.visibility = View.GONE

                    if (isErrorReply) {
                        toast(aiReply)
                    } else {
                        toast("AI analysis complete and saved")
                    }
                }

            } catch (e: Exception) {
                val body = (e as? retrofit2.HttpException)
                    ?.response()?.errorBody()?.string()?.take(1000)
                val msg = com.mmalyil.smartdocai.util.friendlyAiErrorMessage(
                    body ?: e.localizedMessage
                )
                Log.e("AI_ERROR", "Model: $modelName, Source: $source, Error: ${e.localizedMessage}", e)

                withContext(Dispatchers.Main) {
                    if (!isAdded || view == null) return@withContext
                    aiResponseDisplay.text = msg
                    aiResponseDisplay.visibility = View.VISIBLE
                    toast(msg)
                }

            } finally {
                // ✅ ALWAYS reset guard + loading (on main)
                withContext(Dispatchers.Main) {
                    isAnalyzing = false
                    showLoading(false)
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
            root.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?.let { return it }

            // 1) OpenAI Chat format: choices[0].message.content
            root.optJSONArray("choices")?.let { choices ->
                if (choices.length() > 0) {
                    val first = choices.optJSONObject(0)
                    // a) chat format
                    first?.optJSONObject("message")?.optString("content")
                        ?.takeIf { it.isNotBlank() }?.let { return it }
                    // b) text format (some proxies put .text at the top level of a choice)
                    first?.optString("text")?.takeIf { it.isNotBlank() }?.let { return it }
                    // c) responses API style: choices[0].delta/content parts
                    first?.optJSONArray("content")?.let { parts ->
                        val sb = StringBuilder()
                        for (i in 0 until parts.length()) {
                            val part = parts.optJSONObject(i)
                            // text parts
                            part?.optString("text")?.takeIf { it.isNotBlank() }
                                ?.let { sb.append(it) }
                            // tool/text variants
                            part?.optJSONObject("text")?.optString("value")
                                ?.takeIf { it.isNotBlank() }?.let { sb.append(it) }
                        }
                        if (sb.isNotEmpty()) return sb.toString()
                    }
                }
            }

            // 2) OpenAI Responses API top-level: output_text OR content[0].text/value
            root.optJSONArray("output_text")?.let { arr ->
                if (arr.length() > 0) arr.optString(0)?.takeIf { it.isNotBlank() }
                    ?.let { return it }
            }
            root.optString("output_text")?.takeIf { it.isNotBlank() }?.let { return it }
            root.optJSONArray("content")?.let { contentArr ->
                if (contentArr.length() > 0) {
                    val first = contentArr.optJSONObject(0)
                    first?.optString("text")?.takeIf { it.isNotBlank() }?.let { return it }
                    first?.optJSONObject("text")?.optString("value")?.takeIf { it.isNotBlank() }
                        ?.let { return it }
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


    // ---- PDF export (no iText) ----
    private fun exportAsPdf(
        includeDoc: Boolean,
        includeAI: Boolean,
        capFeature: DocumentEntitlement.Feature = DocumentEntitlement.Feature.OTHER_EXPORT
    ) {
        val content = buildExportContent(includeDoc, includeAI)
        if (content.isBlank()) {
            toast("Nothing to export")
            return
        }

        showLoading(true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val ctx = context ?: return@launch
                val outFile = withContext(Dispatchers.IO) {
                    val outDir = File(ctx.getExternalFilesDir(null), "exports").apply { mkdirs() }
                    val file = File(outDir, "SmartDocAI_Export_${System.currentTimeMillis()}.pdf")

                    val pageWidth = 595
                    val pageHeight = 842
                    val margin = 40f

                    val titlePaint = android.graphics.Paint().apply {
                        isAntiAlias = true; textSize = 16f
                        typeface = android.graphics.Typeface.create(
                            android.graphics.Typeface.DEFAULT_BOLD,
                            android.graphics.Typeface.BOLD
                        )
                    }
                    val bodyPaint = android.graphics.Paint().apply {
                        isAntiAlias = true; textSize = 12f
                        typeface = android.graphics.Typeface.MONOSPACE
                    }

                    val pdf = android.graphics.pdf.PdfDocument()
                    var pageNum = 1
                    var y = 0f

                    fun newPage(): android.graphics.pdf.PdfDocument.Page {
                        val info =
                            android.graphics.pdf.PdfDocument.PageInfo.Builder(
                                pageWidth,
                                pageHeight,
                                pageNum
                            ).create()
                        val page = pdf.startPage(info)
                        val c = page.canvas
                        y = margin + titlePaint.textSize
                        c.drawText("SmartDocAI Export", margin, y, titlePaint)
                        val footer = "Page $pageNum"
                        val fw = titlePaint.measureText(footer)
                        c.drawText(
                            footer,
                            pageWidth - margin - fw,
                            pageHeight - margin / 2,
                            bodyPaint
                        )
                        y += titlePaint.textSize + 12f
                        return page
                    }

                    val lines = wrapText(content, bodyPaint, pageWidth - 2 * margin)
                    var page = newPage()
                    val usableBottom = pageHeight - margin - (bodyPaint.textSize + 4f)

                    lines.forEach { line ->
                        if (y + bodyPaint.textSize > usableBottom) {
                            pdf.finishPage(page)
                            pageNum++
                            page = newPage()
                        }
                        page.canvas.drawText(line, margin, y, bodyPaint)
                        y += bodyPaint.textSize + 4f
                    }
                    pdf.finishPage(page)

                    try {
                        FileOutputStream(file).use { pdf.writeTo(it) }
                    } finally {
                        pdf.close()
                    }
                    file
                }

                if (!isAdded) return@launch
                if (outFile.length() <= 0L) {
                    toast("PDF file is empty")
                    return@launch
                }
                DocumentEntitlement.recordUse(ctx, capFeature)
                refreshDocCaps()
                val uri = uriForExportFile(ctx, outFile)
                FilesHistory.saveAsync(
                    context = ctx,
                    fileName = outFile.name,
                    fileUri = uri.toString(),
                    content = content.take(2000),
                    aiResponse = aiBlobForFiles(includeAI),
                    scope = viewLifecycleOwner.lifecycleScope,
                    anchor = view
                )
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "SmartDocAI PDF Export")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newUri(ctx.contentResolver, outFile.name, uri)
                }
                startActivity(Intent.createChooser(share, "Share PDF via"))
            } catch (e: Exception) {
                Log.e(TAG, "PDF export failed", e)
                toast("Couldn't create PDF. Try again.")
            } finally {
                showLoading(false)
            }
        }
    }

    /** Wraps a long string into lines that fit a given width using the provided Paint. */
    private fun wrapText(text: String, paint: android.graphics.Paint, maxWidth: Float): List<String> {
        val out = ArrayList<String>(text.length / 30 + 1)
        val newlineSplit = text.replace("\r", "").split('\n')

        for (para in newlineSplit) {
            if (para.isEmpty()) { out.add(""); continue }
            var start = 0
            val len = para.length
            while (start < len) {
                var end = paint.breakText(para, start, len, true, maxWidth, null) + start
                if (end < len) {
                    // try to break at last space for nicer wrap
                    val lastSpace = para.lastIndexOf(' ', end - 1)
                    if (lastSpace > start + 5) end = lastSpace + 1
                }
                out.add(para.substring(start, end).trimEnd())
                start = end
            }
        }
        return out
    }

    private fun exportAsTxt(includeDoc: Boolean, includeAI: Boolean) {
        val raw = buildExportContent(includeDoc, includeAI)
        if (raw.isBlank()) {
            toast("Nothing to export")
            return
        }

        // 🚫 Clamp output to avoid giant files / memory pressure
        val content = if (raw.length > 2_000_000) {
            raw.take(2_000_000) + "\n\n[Truncated for export]"
        } else raw

        shareAndPersistTxt(
            content,
            recordCap = true,
            chooserTitle = "Share TXT via",
            aiForFiles = aiBlobForFiles(includeAI)
        )
    }

    /** Write extract/AI text to a real .txt under exports/, save to Room Files, then share. */
    private fun shareAndPersistTxt(
        content: String,
        recordCap: Boolean = true,
        chooserTitle: String = "Share via",
        aiForFiles: String? = null
    ) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ctx = context ?: return@launch
                val outDir = File(ctx.getExternalFilesDir(null), "exports").apply { mkdirs() }
                val file = File(outDir, "SmartDocAI_Export_${System.currentTimeMillis()}.txt")
                file.writeText(content)
                if (file.length() <= 0L) throw IllegalStateException("empty txt")

                val uri = uriForExportFile(ctx, file)
                if (recordCap) {
                    DocumentEntitlement.recordUse(ctx, DocumentEntitlement.Feature.OTHER_EXPORT)
                }

                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    refreshDocCaps()
                    FilesHistory.saveAsync(
                        context = ctx,
                        fileName = file.name,
                        fileUri = uri.toString(),
                        content = content.take(2000),
                        aiResponse = aiForFiles,
                        scope = viewLifecycleOwner.lifecycleScope,
                        anchor = view
                    )
                    val share = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, content.take(50_000))
                        putExtra(Intent.EXTRA_SUBJECT, "SmartDocAI export")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        clipData = ClipData.newUri(ctx.contentResolver, file.name, uri)
                    }
                    startActivity(Intent.createChooser(share, chooserTitle))
                }
            } catch (e: Exception) {
                Log.e(TAG, "TXT export failed", e)
                withContext(Dispatchers.Main) {
                    toast("Couldn't create text export. Try again.")
                }
            }
        }
    }


    private fun buildExportContent(includeDoc: Boolean, includeAI: Boolean): String {
        val builder = StringBuilder()
        if (includeDoc) {
            builder.append("\uD83D\uDCC4 Extracted Document:\n$extractedText\n\n------------------------------\n\n")
        }
        // Only append AI when there is a real reply — never an empty “AI Analysis” stub.
        if (includeAI && aiAnswer.isNotBlank()) {
            builder.append("\uD83E\uDD16 AI Analysis:\n$aiAnswer\n")
        }
        return builder.toString()
    }

    /**
     * Room `aiResponse` only when Include AI is on **and** Ask/Chat already produced text.
     * Unchecked / empty → null (detail shows “No AI response saved.”).
     */
    private fun aiBlobForFiles(includeAI: Boolean): String? =
        if (includeAI && aiAnswer.isNotBlank()) aiAnswer.take(4000) else null

    private fun showExtractedText(message: String) {
        pdfTextDisplay.text =
            extractedText.take(1000) + if (extractedText.length > 1000) "..." else ""

        // ✅ Show Quick AI buttons once content exists
        quickAiActions.visibility = View.VISIBLE
        promptContainer.visibility = View.GONE

        analyzeButton.isEnabled = extractedText.isNotBlank()   // ✅ enable here
        toast(message)
    }


    private fun toast(msg: String) {
        if (!isAdded) return
        val ctx = context ?: return
        val owner = viewLifecycleOwnerLiveData.value ?: return
        owner.lifecycleScope.launch(Dispatchers.Main) {
            if (!isAdded || !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }
    }

    fun triggerUploadFromFab() {
        openFilePicker()
    }


    private val scanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                lastScanPageCount =
                    result.data?.getIntExtra(ScanActivity.EXTRA_PAGE_COUNT, 1) ?: 1
                val pdfUri = result.data?.getStringExtra(ScanActivity.EXTRA_PDF_URI).orEmpty()
                val scannedText =
                    result.data?.getStringExtra(ScanActivity.EXTRA_SCANNED_TEXT).orEmpty()
                val pages = lastScanPageCount.coerceAtLeast(1)

                if (pdfUri.isNotBlank()) {
                    selectedFileUri = pdfUri
                    selectedFileName = result.data?.getStringExtra(ScanActivity.EXTRA_PDF_PATH)
                        ?.substringAfterLast('/')
                        ?: "SmartDocAI_Scan.pdf"
                    if (scannedText.isNotBlank()) {
                        extractedText = scannedText
                        pdfTextDisplay.text = scannedText
                        analyzeButton.isEnabled = true
                        quickAiActions.visibility = View.VISIBLE
                    } else {
                        extractedText = "Scanned document · $pages page(s) · image PDF saved"
                        pdfTextDisplay.text = extractedText
                    }
                    toast(
                        if (pages > 1) "Saved $pages-page scan PDF"
                        else "Saved scan PDF"
                    )
                    return@registerForActivityResult
                }

                if (scannedText.isBlank()) {
                    toast("Scan cancelled or empty")
                    return@registerForActivityResult
                }
                extractedText = scannedText
                pdfTextDisplay.text = scannedText
                analyzeButton.isEnabled = true
                quickAiActions.visibility = View.VISIBLE
                toast(if (pages > 1) "Scanned $pages pages" else "Scanned text loaded")
            }
        }

    fun triggerScanFromFab() {
        val intent = Intent(requireContext(), ScanActivity::class.java).apply {
            putExtra(ScanActivity.EXTRA_START_MODE, ScanActivity.MODE_CAMERA)
        }
        scanLauncher.launch(intent)
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


    fun getFileUri(context: Context, fileName: String, subDir: String = ""): Uri {
        // Store inside your app-specific external files dir (safe under scoped storage)
        val dir = if (subDir.isNotEmpty()) {
            File(context.getExternalFilesDir(null), subDir)
        } else {
            context.getExternalFilesDir(null)
        }
        if (dir != null && !dir.exists()) dir.mkdirs()

        val file = File(dir, fileName)
        return uriForExportFile(context, file)
    }

    /** Copy [src] into FileProvider-backed `externalFilesDir/exports/` if needed. */
    private fun persistExportFile(context: Context, src: File): File {
        val dir = File(context.getExternalFilesDir(null), "exports").apply { mkdirs() }
        val dest = File(dir, src.name)
        if (src.absolutePath != dest.absolutePath) {
            src.copyTo(dest, overwrite = true)
        }
        return dest
    }

    private fun uriForExportFile(context: Context, file: File): Uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

    private fun launchExcelPickerForWord() {
        excelToWordLauncher.launch(
            arrayOf(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel"
            )
        )
    }

    /**
     * Tools tips (Settings replay + first-run).
     *
     * 3.19.9–3.19.10 SpotlightOverlay left a blank transparent click-eater on S25
     * (Mathew verify). Abandoned fullscreen spotlight for an AlertDialog pager that
     * cannot leave a transparent View. Tip copy unchanged from 3.19.8.
     */
    private fun showWalkthroughNow(rootView: View, force: Boolean = false) {
        if (!isAdded) return
        val ctx = context ?: return
        val owner = viewLifecycleOwnerLiveData.value ?: return
        if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return

        if (suppressGuideThisSession || isCoachRunning) return
        isCoachRunning = true

        owner.lifecycleScope.launch {
            if (!isAdded || !owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                isCoachRunning = false
                return@launch
            }

            if (!force) {
                val seen = com.mmalyil.smartdocai.prefs.CoachPrefs
                    .hasSeenCoach(ctx)
                    .first()
                if (seen) {
                    isCoachRunning = false
                    return@launch
                }
            }

            // Strip any leftover SpotlightOverlay from older installs, then show dialog.
            tearDownAllCoachLayers(keepRunningFlag = true)
            if (!isAdded) {
                isCoachRunning = false
                return@launch
            }
            showToolsTipsDialog(ctx)
        }
    }

    /** Non-fullscreen tip pager — dismisses cleanly; no transparent View host. */
    private fun showToolsTipsDialog(ctx: android.content.Context) {
        tipsDialog?.dismiss()
        tipsDialog = null

        val tips = listOf(
            "Jobs here: Image→Excel, Excel→Word, Combine, Scan ID. Tap to continue.",
            "Upload a PDF, Excel, or image to Analyse.",
            "Free Groq AI — summary, key points, chat. Caps under usage.",
            "Export saves to Files. Document Pro = clean unlimited tools."
        )
        var index = 0

        fun finishTips(showReplayToast: Boolean) {
            tipsDialog = null
            isCoachRunning = false
            viewLifecycleOwner.lifecycleScope.launch {
                com.mmalyil.smartdocai.prefs.CoachPrefs.setCoachSeen(ctx, true)
            }
            if (showReplayToast && isAdded) {
                Toast.makeText(
                    ctx,
                    "Replay tips anytime: Settings → Analyse guided tips",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        fun showStep() {
            if (!isAdded) {
                finishTips(showReplayToast = false)
                return
            }
            val last = index >= tips.lastIndex
            val dialog = AlertDialog.Builder(ctx)
                .setTitle("Analyse tips (${index + 1}/${tips.size})")
                .setMessage(tips[index])
                .setCancelable(true)
                .setNegativeButton("Skip") { d, _ ->
                    d.dismiss()
                    finishTips(showReplayToast = false)
                }
                .setPositiveButton(if (last) "Done" else "Next") { d, _ ->
                    d.dismiss()
                    if (last) {
                        finishTips(showReplayToast = true)
                    } else {
                        index++
                        showStep()
                    }
                }
                .setOnCancelListener {
                    finishTips(showReplayToast = false)
                }
                .create()
            tipsDialog = dialog
            dialog.show()
        }

        showStep()
    }

    /**
     * Dismiss tip dialog + strip any leftover SpotlightOverlay freeloaders
     * (older builds) from fragment / contentFrame / decor. Clears loadingOverlay
     * click-eating while GONE.
     */
    private fun tearDownAllCoachLayers(keepRunningFlag: Boolean = false) {
        try {
            tipsDialog?.dismiss()
        } catch (_: Exception) {
        }
        tipsDialog = null

        coachOverlay?.dismissCompletely()
        coachOverlay = null
        if (!keepRunningFlag) isCoachRunning = false

        val fragRoot = view as? ViewGroup
        val contentFrame = activity?.findViewById<ViewGroup>(R.id.contentFrame)
        val activityContent = activity?.findViewById<ViewGroup>(android.R.id.content)
        val decor = activity?.window?.decorView as? ViewGroup

        com.mmalyil.smartdocai.ui.walkthrough.SpotlightOverlay.stripFrom(fragRoot)
        com.mmalyil.smartdocai.ui.walkthrough.SpotlightOverlay.stripFrom(contentFrame)
        com.mmalyil.smartdocai.ui.walkthrough.SpotlightOverlay.stripFrom(activityContent)
        com.mmalyil.smartdocai.ui.walkthrough.SpotlightOverlay.stripFrom(decor)

        loadingOverlay?.apply {
            visibility = View.GONE
            isClickable = false
            isFocusable = false
            isFocusableInTouchMode = false
        }
    }

    // STOP tips if the user starts any real action.
    private fun stopWalkthroughIfRunning() {
        if (!isCoachRunning && tipsDialog == null && coachOverlay == null) return
        tearDownAllCoachLayers()
        val ctx = context ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            CoachPrefs.setCoachSeen(ctx, true)
        }
    }

    // Reuse the same socket tagging idea
    private val driveSocketTagging = Interceptor { chain ->
        TrafficStats.setThreadStatsTag(0x44524956) // 'DRIV'
        try {
            chain.proceed(chain.request())
        } finally {
            TrafficStats.clearThreadStatsTag()
        }
    }

    // One reusable interceptor
    private val taggingInterceptor = Interceptor { chain ->
        TrafficStats.setThreadStatsTag(0x53444149) // 'SDAI'
        try { chain.proceed(chain.request()) } finally { TrafficStats.clearThreadStatsTag() }
    }

    // Reuse this for every OkHttp client you own
    val http by lazy {
        OkHttpClient.Builder()
            .addNetworkInterceptor(taggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }


    private fun <T> relaxVmPolicyDuring(block: () -> T): T {
        val old = StrictMode.getVmPolicy()
        return try {
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder(old)
                    .penaltyLog()       // log StrictMode hits instead of crashing
                    .build()
            )
            block()
        } finally {
            StrictMode.setVmPolicy(old)
        }
    }

    private fun handleSharedText(text: String) {
        extractedText = text
        selectedFileName = "Shared text"
        selectedFileUri = ""   // none

        // show in UI
        pdfTextDisplay.text = text
        analyzeButton.isEnabled = text.isNotBlank()
        aiResponseDisplay.text = ""
        aiResponseDisplay.visibility = View.GONE

        toast("Text received")
        maybeAutoAnalyze()
    }

// ---- Prompt suggestions helpers ----

    private fun docKind(): String {
        val n = selectedFileName.lowercase()
        return when {
            n.endsWith(".pdf") -> "pdf"
            n.endsWith(".docx") -> "docx"
            n.endsWith(".pptx") -> "pptx"
            n.endsWith(".xlsx") || n.endsWith(".xls") || n.endsWith(".csv") -> "sheet"
            extractedText.length < 80 -> "short"
            extractedText.count { it == '\n' } > 20 -> "long"
            else -> "generic"
        }
    }

    private fun promptSuggestions(): List<String> {
        val base = listOf(
            "Summarize this document in 5 bullet points.",
            "List key action items with owners and deadlines.",
            "Extract all dates, amounts, and names in a table."
        )
        return when (docKind()) {
            "pdf" -> base + listOf(
                "Give a plain-English summary (<=120 words).",
                "What are the risks, assumptions, and next steps?"
            )
            "docx" -> base + listOf(
                "Rewrite the executive summary to be clearer and shorter.",
                "Create a meeting agenda based on this doc."
            )
            "pptx" -> base + listOf(
                "Turn each slide into one bullet (slide-by-slide).",
                "What’s the overall narrative and suggested conclusion?"
            )
            "sheet" -> listOf(
                "Describe the main trends and outliers.",
                "Which 3 metrics changed the most and why?",
                "Find anomalies and possible data quality issues."
            )
            "short" -> listOf(
                "Expand this into a clear paragraph.",
                "Create 3 alternative phrasings, each with a different tone."
            )
            "long" -> base + listOf(
                "Build a one-page brief with sections: Context, Findings, Decisions.",
                "Pull all questions the doc raises but doesn’t answer."
            )
            else -> base
        }
    }

    private fun showPromptSheet(onPick: (String) -> Unit) {
        val items = promptSuggestions()
        val view = layoutInflater.inflate(R.layout.simple_list_sheet, null) // see layout note below
        val rv = view.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.sheetRecycler)
        rv.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
        rv.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<VH>() {
            override fun onCreateViewHolder(p: ViewGroup, vType: Int): VH {
                val tv = android.widget.TextView(p.context).apply {
                    setPadding(32, 32, 32, 32)
                    textSize = 16f
                }
                return VH(tv)
            }

            override fun getItemCount() = items.size
            override fun onBindViewHolder(h: VH, i: Int) {
                (h.itemView as android.widget.TextView).text = "• " + items[i]
                h.itemView.setOnClickListener {
                    sheet?.dismiss()
                    onPick(items[i])
                }
            }
        }
        sheet = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext()).apply {
            setContentView(view)
            setTitle("Try a prompt")
            show()
        }
    }

    private fun showOpenSaveSheetForLegacy(file: File) {
        // For Android 8/9, we keep it super simple:
        // just let the user pick a location via SAF.
        promptSaveDocxViaSaf(file)
    }



    private var sheet: com.google.android.material.bottomsheet.BottomSheetDialog? = null
    private class VH(v: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(v)

    private fun showAiReportPromptOptions(uri: Uri) {
        val options = arrayOf(
            "Quick Summary (Recommended)",
            "Detailed Analysis",
            "Custom Prompt"
        )
        val rawName = selectedFileName.ifBlank {
            uri.lastPathSegment?.substringAfterLast('/') ?: "workbook"
        }
        val label = if (rawName.contains('.')) rawName else "$rawName.xlsx"

        AlertDialog.Builder(requireContext())
            .setTitle("Excel → Word report")
            .setMessage(
                "Using: $label · Ask what you want in the Word report " +
                    "(or pick a preset below)."
            )
            .setCancelable(true)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> exportXlsxAiReportNow(uri) // default auto prompt
                    1 -> {
                        val detailed = """
                        Provide a detailed analysis of this Excel sheet.
                        Include trends, comparisons, outliers, anomalies,
                        summaries of each column, and suggested actions.
                    """.trimIndent()
                        exportXlsxAiReportNow(uri, detailed)
                    }
                    2 -> showCustomPromptInput(uri)
                }
            }
            .setPositiveButton("Continue") { _, _ ->
                // Same as recommended preset — one-tap path.
                exportXlsxAiReportNow(uri)
            }
            .setNeutralButton("Change Excel") { _, _ ->
                offeredDocxForUri = null
                // Clear sticky selection so the next pick is required.
                selectedFileUri = ""
                selectedFileName = ""
                toast("Pick another Excel (.xlsx) file")
                launchExcelPickerForWord()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCustomPromptInput(uri: Uri) {
        val input = EditText(requireContext())
        input.hint = "Describe what AI should analyze…"
        input.minLines = 3
        input.maxLines = 6

        AlertDialog.Builder(requireContext())
            .setTitle("Custom AI Prompt")
            .setView(input)
            .setCancelable(true)
            .setPositiveButton("Generate") { _, _ ->
                val prompt = input.text.toString().trim()
                exportXlsxAiReportNow(uri, prompt)
            }
            .setNeutralButton("Change Excel") { _, _ ->
                offeredDocxForUri = null
                selectedFileUri = ""
                selectedFileName = ""
                toast("Pick another Excel (.xlsx) file")
                launchExcelPickerForWord()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


}
