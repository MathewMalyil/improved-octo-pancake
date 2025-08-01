package com.mmalyil.smartdocai

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

class PalmScannerActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var predictionText: TextView
    private lateinit var handLandmarker: HandLandmarker

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val predictions = listOf(
        "You will achieve great success soon ✨",
        "A surprise is waiting for you 🎁",
        "Love is in the air 💖",
        "Stay strong through challenges 💪",
        "Adventure awaits 🌍",
        "Be cautious with money 💸",
        "A lucky opportunity will arise 🍀"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_palm_scanner)

        previewView = findViewById(R.id.previewView)
        predictionText = findViewById(R.id.predictionText)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        } else {
            initHandLandmarker()
            startCamera()
        }
    }

    private fun initHandLandmarker() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .build()

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setMinHandDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setNumHands(1)
            .build()

        handLandmarker = HandLandmarker.createFromOptions(this, options)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analyzer = ImageAnalysis.Builder().build().also {
                it.setAnalyzer(cameraExecutor) { imageProxy -> analyzeImage(imageProxy) }
            }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, analyzer)

        }, ContextCompat.getMainExecutor(this))
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)

    private fun analyzeImage(imageProxy: ImageProxy) {
        val image = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val bitmap = toBitmap(image)
        val mpImage = BitmapImageBuilder(bitmap).build()
        val result: HandLandmarkerResult = handLandmarker.detect(mpImage)

        runOnUiThread {
            if (result.handedness().isNotEmpty()) {
                predictionText.text = predictions.random()
            } else {
                predictionText.text = "Place your palm in front of the camera ✋"
            }
        }

        imageProxy.close()
    }

    private fun toBitmap(image: Image): Bitmap {
        val yBuffer = image.planes[0].buffer
        val vuBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val vuSize = vuBuffer.remaining()
        val nv21 = ByteArray(ySize + vuSize)

        yBuffer.get(nv21, 0, ySize)
        vuBuffer.get(nv21, ySize, vuSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)

        return BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())
    }

    override fun onDestroy() {
        super.onDestroy()
        handLandmarker.close()
        cameraExecutor.shutdown()
    }
}