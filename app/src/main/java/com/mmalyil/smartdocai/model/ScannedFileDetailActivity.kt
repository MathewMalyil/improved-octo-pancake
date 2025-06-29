package com.mmalyil.smartdocai.model

// Add at top

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.widget.Toast
import android.content.Intent





import android.os.Bundle

import androidx.appcompat.app.AppCompatActivity
import com.mmalyil.smartdocai.databinding.ActivityScannedFileDetailBinding

import java.text.SimpleDateFormat
import java.util.*

class ScannedFileDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScannedFileDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScannedFileDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val file = intent.getSerializableExtra("scannedFile") as? ScannedFile
        if (file == null) {

            finish()
            return
        }
        binding.tvFileName.text = file.fileName
        binding.tvDate.text =
            SimpleDateFormat(
                "dd MMM yyyy, hh:mm a",
                Locale.getDefault()
            ).format(Date(file.timestamp))
        binding.tvContent.text = file.content
        binding.tvAiResponse.text = file.aiResponse ?: "No AI response saved."

        binding.btnShare.setOnClickListener {
            val file = intent.getSerializableExtra("scannedFile") as? ScannedFile
            if (file != null) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, file.fileName)
                    putExtra(
                        Intent.EXTRA_TEXT,
                        """
                📄 File: ${file.fileName}
                🕒 Date: ${
                            SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(
                                Date(
                                    file.timestamp
                                )
                            )
                        }

                📑 Extracted Text:
                ${file.content}

                🤖 AI Response:
                ${file.aiResponse ?: "None"}
                """.trimIndent()
                    )
                }
                startActivity(Intent.createChooser(shareIntent, "Share via"))
            } else {
                Toast.makeText(this, "File not available", Toast.LENGTH_SHORT).show()
                finish()



            }
        }
    }
}
