package com.mmalyil.smartdocai.model

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mmalyil.smartdocai.databinding.ActivityScannedFileDetailBinding
import com.mmalyil.smartdocai.util.ScanStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScannedFileDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScannedFileDetailBinding
    private lateinit var file: ScannedFile

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScannedFileDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        val scanned = intent.getSerializableExtra("scannedFile") as? ScannedFile
        if (scanned == null) {
            finish()
            return
        }
        file = scanned

        binding.tvFileName.text = file.fileName
        binding.tvDate.text =
            SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())
                .format(Date(file.timestamp))
        binding.tvContent.text = file.content.ifBlank { "No extracted text for this entry." }
        binding.tvAiResponse.text = file.aiResponse ?: "No AI response saved."

        val isPdf = ScanStorage.isPdfEntry(file)
        binding.btnOpenPdf.visibility = if (isPdf) View.VISIBLE else View.GONE
        binding.btnSaveToDownloads.visibility = if (isPdf) View.VISIBLE else View.GONE
        binding.tvStorageHint.visibility = if (isPdf) View.VISIBLE else View.GONE

        binding.btnOpenPdf.setOnClickListener {
            ScanStorage.openPdf(this, file)
        }

        binding.btnSaveToDownloads.setOnClickListener {
            binding.btnSaveToDownloads.isEnabled = false
            lifecycleScope.launch {
                val uri = withContext(Dispatchers.IO) {
                    ScanStorage.savePdfToDownloads(this@ScannedFileDetailActivity, file)
                }
                binding.btnSaveToDownloads.isEnabled = true
                if (uri != null) {
                    Toast.makeText(
                        this@ScannedFileDetailActivity,
                        "Saved to Downloads — open phone Files / Downloads to find it",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        binding.btnShare.setOnClickListener {
            if (isPdf && ScanStorage.sharePdf(this, file)) return@setOnClickListener

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, file.fileName)
                putExtra(
                    Intent.EXTRA_TEXT,
                    """
                    File: ${file.fileName}
                    Date: ${
                        SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())
                            .format(Date(file.timestamp))
                    }

                    Extracted Text:
                    ${file.content}

                    AI Response:
                    ${file.aiResponse ?: "None"}
                    """.trimIndent()
                )
            }
            startActivity(Intent.createChooser(shareIntent, "Share via"))
        }

        binding.btnDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete file?")
                .setMessage("Remove “${file.fileName}” from SmartDocAI history. The PDF stays in app storage until cleared with app data.")
                .setPositiveButton("Delete") { _, _ -> deleteEntry() }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // PDF entries: open is the primary job — auto-offer once if launched with extra
        if (isPdf && intent.getBooleanExtra(EXTRA_AUTO_OPEN, false)) {
            binding.root.post { ScanStorage.openPdf(this, file) }
        }
    }

    private fun deleteEntry() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(this@ScannedFileDetailActivity)
                    .scannedFileDao()
                    .delete(file)
            }
            Toast.makeText(this@ScannedFileDetailActivity, "File deleted", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    companion object {
        const val EXTRA_AUTO_OPEN = "autoOpenPdf"
    }
}
