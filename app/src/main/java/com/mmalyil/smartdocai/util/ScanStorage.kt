package com.mmalyil.smartdocai.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.mmalyil.smartdocai.BuildConfig
import com.mmalyil.smartdocai.model.ScannedFile
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Scan PDFs live under app-specific external files: `{externalFilesDir}/scans/`.
 * That is NOT the system Files / Downloads folder until the user shares or
 * explicitly saves a copy there.
 */
object ScanStorage {

    fun isPdfEntry(file: ScannedFile): Boolean {
        val name = file.fileName
        val uri = file.fileUri
        return name.endsWith(".pdf", ignoreCase = true) ||
            uri.contains(".pdf", ignoreCase = true) ||
            file.content.contains("image PDF", ignoreCase = true)
    }

    fun parseUri(file: ScannedFile): Uri? =
        runCatching { Uri.parse(file.fileUri) }.getOrNull()
            ?.takeIf { !file.fileUri.isBlank() }

    /** Resolve a readable stream for a history entry (content URI or file path). */
    fun openInputStream(context: Context, file: ScannedFile): InputStream? {
        val uri = parseUri(file) ?: return null
        return when (uri.scheme) {
            "content" -> context.contentResolver.openInputStream(uri)
            "file" -> uri.path?.let { FileInputStream(File(it)) }
            else -> {
                // Absolute path stored by older builds
                val path = file.fileUri
                val f = File(path)
                if (f.exists()) FileInputStream(f) else null
            }
        }
    }

    fun contentUriForView(context: Context, file: ScannedFile): Uri? {
        val uri = parseUri(file) ?: return null
        if (uri.scheme == "content") return uri
        val path = uri.path ?: file.fileUri
        val f = File(path)
        if (!f.exists()) return null
        return FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            f
        )
    }

    fun openPdf(context: Context, file: ScannedFile): Boolean {
        if (!isPdfEntry(file)) {
            Toast.makeText(context, "This entry has no PDF to open", Toast.LENGTH_SHORT).show()
            return false
        }
        val uri = contentUriForView(context, file)
        if (uri == null) {
            Toast.makeText(context, "PDF file missing from app storage", Toast.LENGTH_LONG).show()
            return false
        }
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            clipData = ClipData.newUri(context.contentResolver, file.fileName, uri)
        }
        return try {
            context.startActivity(Intent.createChooser(view, "Open PDF"))
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No PDF viewer installed", Toast.LENGTH_LONG).show()
            false
        }
    }

    fun sharePdf(context: Context, file: ScannedFile): Boolean {
        if (!isPdfEntry(file)) return false
        val uri = contentUriForView(context, file) ?: return false
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, file.fileName, uri)
        }
        context.startActivity(Intent.createChooser(share, "Share PDF"))
        return true
    }

    /**
     * Copy the app-private scan PDF into public Downloads so it appears in
     * the system Files / Downloads app.
     */
    fun savePdfToDownloads(context: Context, file: ScannedFile): Uri? {
        if (!isPdfEntry(file)) {
            Toast.makeText(context, "Only PDFs can be saved to Downloads", Toast.LENGTH_SHORT).show()
            return null
        }
        val displayName = file.fileName.ifBlank { "SmartDocAI_Scan.pdf" }
            .let { if (it.endsWith(".pdf", true)) it else "$it.pdf" }

        return try {
            val input = openInputStream(context, file)
            if (input == null) {
                Toast.makeText(context, "PDF file missing from app storage", Toast.LENGTH_LONG).show()
                return null
            }
            input.use { src ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                        put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val resolver = context.contentResolver
                    val outUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: return null
                    resolver.openOutputStream(outUri)?.use { dst -> src.copyTo(dst) }
                        ?: return null
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    resolver.update(outUri, values, null, null)
                    outUri
                } else {
                    @Suppress("DEPRECATION")
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!dir.exists()) dir.mkdirs()
                    val outFile = File(dir, displayName)
                    outFile.outputStream().use { dst -> src.copyTo(dst) }
                    @Suppress("DEPRECATION")
                    val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(outFile))
                    context.sendBroadcast(intent)
                    Uri.fromFile(outFile)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Could not save to Downloads: ${e.message}", Toast.LENGTH_LONG).show()
            null
        }
    }

    fun scansDir(context: Context): File =
        File(context.getExternalFilesDir(null), "scans").apply { mkdirs() }
}
