package com.mmalyil.smartdocai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File

class ScanActivity : AppCompatActivity() {

    private lateinit var captureButton: Button
    private lateinit var statusText: TextView

    private var imageUri: Uri? = null

    // 🆕 Store OCR text per page
    private val scannedPagesText = mutableListOf<String>()
    private var currentPage = 1

    // Camera capture
    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                imageUri?.let { runOcrOnUri(it) }
            } else {
                Toast.makeText(this, "No image captured", Toast.LENGTH_SHORT).show()
            }
        }

    // Permission launcher
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCameraCapture()
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        captureButton = findViewById(R.id.captureButton)
        statusText = findViewById(R.id.tvStatus)

        captureButton.setOnClickListener {
            checkCameraPermissionAndStart()
        }
    }

    private fun checkCameraPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCameraCapture()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCameraCapture() {
        statusText.text = "Scanning page $currentPage…"

        val photoFile = File.createTempFile(
            "scan_${System.currentTimeMillis()}",
            ".jpg",
            cacheDir
        )

        imageUri = FileProvider.getUriForFile(
            this,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            photoFile
        )

        imageUri?.let { takePictureLauncher.launch(it) }
    }

    private fun runOcrOnUri(uri: Uri) {
        statusText.text = "Processing page $currentPage…"

        try {
            val image = InputImage.fromFilePath(this, uri)
            val recognizer = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
            )

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val pageText = visionText.text?.trim().orEmpty()

                    if (pageText.isBlank()) {
                        Toast.makeText(this, "No text detected on this page", Toast.LENGTH_SHORT).show()
                        showNextPageDialog(added = false)
                        return@addOnSuccessListener
                    }

                    scannedPagesText.add(pageText)
                    showNextPageDialog(added = true)
                }
                .addOnFailureListener {
                    Toast.makeText(this, "OCR failed: ${it.message}", Toast.LENGTH_SHORT).show()
                    showNextPageDialog(added = false)
                }

        } catch (e: Exception) {
            Toast.makeText(this, "Error reading image: ${e.message}", Toast.LENGTH_SHORT).show()
            showNextPageDialog(added = false)
        }
    }

    // 🆕 Ask user what to do next
    private fun showNextPageDialog(added: Boolean) {
        val msg = if (added) {
            "Page $currentPage scanned.\nScan another page?"
        } else {
            "This page had no readable text.\nTry another page?"
        }

        AlertDialog.Builder(this)
            .setTitle("Scan document")
            .setMessage(msg)
            .setCancelable(false)
            .setPositiveButton("Scan Next Page") { _, _ ->
                currentPage++
                startCameraCapture()
            }
            .setNegativeButton("Finish") { _, _ ->
                finishScan()
            }
            .show()
    }

    // 🆕 Combine pages and return result
    private fun finishScan() {
        if (scannedPagesText.isEmpty()) {
            Toast.makeText(this, "No text scanned", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        val combinedText = scannedPagesText.joinToString(
            separator = "\n\n--- Page Break ---\n\n"
        )

        val returnIntent = Intent().apply {
            putExtra("scannedText", combinedText)
        }
        setResult(Activity.RESULT_OK, returnIntent)
        finish()
    }
}