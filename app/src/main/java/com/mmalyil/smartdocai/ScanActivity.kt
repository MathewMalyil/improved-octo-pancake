package com.mmalyil.smartdocai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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

    // Full-resolution camera capture
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
                setResult(RESULT_CANCELED)
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
        // Create a temp file in app cache
        val photoFile = File.createTempFile(
            "scan_${System.currentTimeMillis()}",
            ".jpg",
            cacheDir
        )

        // Authority matches your FileProvider in AndroidManifest
        imageUri = FileProvider.getUriForFile(
            this,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            photoFile
        )

        imageUri?.let { takePictureLauncher.launch(it) }
    }

    private fun runOcrOnUri(uri: Uri) {


        statusText.text = "Processing…"

        try {
            val image = InputImage.fromFilePath(this, uri)
            val recognizer = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS
            )

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val resultText = visionText.text ?: ""
                    if (resultText.isBlank()) {
                        Toast.makeText(this, "No text detected", Toast.LENGTH_SHORT).show()
                        statusText.text = "No text detected. Try again."
                        return@addOnSuccessListener
                    }

                    val returnIntent = Intent().apply {
                        putExtra("scannedText", resultText)
                    }
                    setResult(RESULT_OK, returnIntent)
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(
                        this,
                        "OCR failed: ${it.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    statusText.text = "OCR failed. Please try again."
                }
        } catch (e: Exception) {
            Toast.makeText(this, "Error reading image: ${e.message}", Toast.LENGTH_SHORT).show()
            statusText.text = "Error loading image."
        }
    }
}