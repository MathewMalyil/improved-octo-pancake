package com.mmalyil.smartdocai

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.mmalyil.smartdocai.model.AppDatabase
import com.mmalyil.smartdocai.model.ScannedFile
import com.mmalyil.smartdocai.scan.CardGuideOverlayView
import com.mmalyil.smartdocai.scan.CropOverlayView
import com.mmalyil.smartdocai.scan.DocumentEdgeDetector
import com.mmalyil.smartdocai.scan.DocumentEdgeTracker
import com.mmalyil.smartdocai.scan.EdgeOverlayView
import com.mmalyil.smartdocai.scan.ImagePdfWriter
import com.mmalyil.smartdocai.scan.OpenCvDocumentDetector
import com.mmalyil.smartdocai.scan.PerspectiveHelper
import com.mmalyil.smartdocai.scan.QuadAdjustOverlayView
import com.mmalyil.smartdocai.scan.ScanFilter
import com.mmalyil.smartdocai.scan.ScanPage
import com.mmalyil.smartdocai.scan.ScanPageRenderer
import com.mmalyil.smartdocai.util.DocumentEntitlement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Live CameraX capture (edge highlight + auto/manual) → review (crop/rotate/filters)
 * → multi-page image PDF. From-photos path unchanged.
 */
class ScanActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ScanActivity"
        const val EXTRA_START_MODE = "start_mode"
        const val MODE_CAMERA = "camera"
        const val MODE_PHOTOS = "photos"
        /** Guided Front → Back ID / Aadhaar capture (2-page PDF). */
        const val MODE_ID = "id"

        const val EXTRA_PDF_URI = "pdfUri"
        const val EXTRA_PDF_PATH = "pdfPath"
        const val EXTRA_PAGE_COUNT = "scanPageCount"
        const val EXTRA_SCANNED_TEXT = "scannedText"

        /** Debug-only: inject synthetic page quads to prove lock→capture. */
        const val ACTION_DEBUG_FEED_PAGE = "com.mmalyil.smartdocai.DEBUG_FEED_PAGE"

        /** Still re-detect must clear this to perspective-warp the capture. */
        private const val STILL_WARP_SCORE = 0.22f

        /** Pull still quad inward so Review/PDF sheds desk margin around the page. */
        private const val STILL_QUAD_INSET = 0.028f

        /** Still-detect score at/above this → auto-Apply warp; skip corner UI. */
        private const val HIGH_AUTO_APPLY_SCORE = 0.72f
    }

    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvLiveHint: TextView
    private lateinit var ivPreview: ImageView
    private lateinit var cropOverlay: CropOverlayView
    private lateinit var quadAdjustOverlay: QuadAdjustOverlayView
    private lateinit var edgeOverlay: EdgeOverlayView
    private lateinit var cardGuideOverlay: CardGuideOverlayView
    private lateinit var previewView: PreviewView
    private lateinit var liveCapturePane: View
    private lateinit var thumbStrip: RecyclerView
    private lateinit var progressSaving: ProgressBar
    private lateinit var emptyPane: View
    private lateinit var filterRow: View
    private lateinit var reviewViews: List<View>

    private lateinit var chipOriginal: Chip
    private lateinit var chipEnhance: Chip
    private lateinit var chipBw: Chip
    private lateinit var compressRow: View
    private lateinit var chipCompressNormal: Chip
    private lateinit var chipCompressSmaller: Chip
    private lateinit var btnRotateLeft: MaterialButton
    private lateinit var btnRotateRight: MaterialButton
    private lateinit var btnToggleCrop: MaterialButton
    private lateinit var btnAdjustCorners: MaterialButton
    private lateinit var btnRetake: MaterialButton
    private lateinit var btnKeepScanning: MaterialButton
    private lateinit var btnApplyAdjust: MaterialButton
    private lateinit var btnSavePdf: MaterialButton
    private lateinit var captureButton: MaterialButton
    private lateinit var btnFromPhotos: MaterialButton
    private lateinit var btnCloseLive: MaterialButton
    private lateinit var btnRetryCamera: MaterialButton
    private lateinit var btnEmptyFromPhotos: MaterialButton

    private val pages = mutableListOf<ScanPage>()
    private var selectedIndex = 0
    private var cropMode = false
    /** Notes-style 4-corner adjust before filters/Save PDF. */
    private var adjustMode = false
    private var previewBitmap: Bitmap? = null
    private var saving = false
    private var retakePending = false
    /** Guided ID / Aadhaar Front → Back mode. */
    private var idMode = false
    /**
     * After Front is ready in ID mode, continue to Back capture once
     * (skip when retaking a single side).
     */
    private var idAwaitingBack = false

    private data class CapturedPage(
        /** Path Review shows first (warped if auto-applied). */
        val displayPath: String,
        /** Unwarped still for Adjust-corners escape hatch. */
        val originalPath: String,
        val adjustQuad: Array<PointF>,
        val score: Float,
        val autoApplied: Boolean
    )

    private lateinit var thumbAdapter: ThumbAdapter

    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var analysisExecutor: ExecutorService? = null
    private val analyzing = AtomicBoolean(false)
    private val capturing = AtomicBoolean(false)

    private var lastQuad: Array<PointF>? = null
    private var lastScore = 0f
    private var autoArmed = true
    private val edgeTracker = DocumentEdgeTracker()
    private val lastAnalyzeMs = AtomicLong(0L)
    private var debugFeedRegistered = false
    private val previewDetectHandler = Handler(Looper.getMainLooper())
    private var previewDetectRunning = false
    private val previewDetectTick = object : Runnable {
        override fun run() {
            if (!previewDetectRunning || !liveCapturePane.isVisible || capturing.get()) {
                if (previewDetectRunning) {
                    previewDetectHandler.postDelayed(this, 150L)
                }
                return
            }
            samplePreviewAndDetect()
            previewDetectHandler.postDelayed(this, 120L)
        }
    }

    /** DEBUG: adb shell am broadcast -a com.mmalyil.smartdocai.DEBUG_FEED_PAGE */
    private val debugFeedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!BuildConfig.DEBUG) return
            if (intent?.action != ACTION_DEBUG_FEED_PAGE) return
            Log.i(TAG, "DEBUG_FEED_PAGE — OpenCV synthetic page + lock→capture")
            edgeTracker.reset()
            autoArmed = true
            capturing.set(false)
            lifecycleScope.launch {
                val det = withContext(Dispatchers.Default) {
                    OpenCvDocumentDetector.ensureInit()
                    // White rectangle on dark bg — proves OpenCV contour path
                    val bmp = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
                    val c = android.graphics.Canvas(bmp)
                    c.drawColor(0xFF1A1A1A.toInt())
                    val paint = android.graphics.Paint().apply { color = 0xFFF5F5F5.toInt() }
                    c.drawRect(80f, 60f, 560f, 420f, paint)
                    val found = OpenCvDocumentDetector.detectBitmap(bmp)
                    // Deskew proof: skewed trapezoid page → warp to rectangle
                    val skew = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
                    val sc = android.graphics.Canvas(skew)
                    sc.drawColor(0xFF2A2A2A.toInt())
                    val path = android.graphics.Path().apply {
                        moveTo(120f, 80f)
                        lineTo(720f, 40f)
                        lineTo(760f, 560f)
                        lineTo(80f, 520f)
                        close()
                    }
                    sc.drawPath(path, paint)
                    val warped = deskewStill(skew)
                    if (warped != null) {
                        Log.i(TAG, "DEBUG DESKEW ok ${warped.width}x${warped.height}")
                        warped.recycle()
                    } else {
                        Log.e(TAG, "DEBUG DESKEW failed")
                    }
                    skew.recycle()
                    bmp.recycle()
                    found
                }
                if (det != null) {
                    Log.i(TAG, "DEBUG OpenCV synthetic HIT score=${det.score}")
                } else {
                    Log.e(TAG, "DEBUG OpenCV synthetic MISS — falling back to fixed quad")
                }
                val seed = det ?: DocumentEdgeDetector.Detection(
                    quad = arrayOf(
                        PointF(0.18f, 0.22f),
                        PointF(0.82f, 0.20f),
                        PointF(0.85f, 0.80f),
                        PointF(0.15f, 0.82f)
                    ),
                    score = 0.70f,
                    analysisWidth = 480,
                    analysisHeight = 640
                )
                repeat(12) {
                    onDetection(
                        DocumentEdgeDetector.Detection(
                            quad = Array(4) { PointF(seed.quad[it].x, seed.quad[it].y) },
                            score = seed.score,
                            analysisWidth = seed.analysisWidth,
                            analysisHeight = seed.analysisHeight
                        )
                    )
                    delay(100L)
                }
            }
        }
    }

    private val pickImagesLauncher =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
            if (uris.isNullOrEmpty()) {
                if (pages.isEmpty()) showEmptyState()
                return@registerForActivityResult
            }
            stopLiveCamera()
            lifecycleScope.launch {
                val imported = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        ScanPageRenderer.copyUriToCache(this@ScanActivity, uri)?.absolutePath
                    }
                }
                if (imported.isEmpty()) {
                    Toast.makeText(this@ScanActivity, "Could not import photos", Toast.LENGTH_SHORT).show()
                    if (pages.isEmpty()) showEmptyState()
                    return@launch
                }
                imported.forEach { path ->
                    val captured = withContext(Dispatchers.IO) {
                        finalizeImportedStill(path)
                    }
                    addOrReplacePage(captured, replaceSelected = false)
                }
            }
        }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startLiveCamera(replaceSelected = retakePending)
            else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                retakePending = false
                showEmptyState()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)
        bindViews()
        setupThumbs()
        setupControls()

        // Load OpenCV before first analyze frame (robust document contours)
        lifecycleScope.launch(Dispatchers.Default) {
            val ok = OpenCvDocumentDetector.ensureInit()
            Log.i(TAG, "OpenCV ready=$ok")
        }

        when (intent.getStringExtra(EXTRA_START_MODE)) {
            MODE_PHOTOS -> {
                showEmptyState()
                openPhotoPicker()
            }
            MODE_ID -> {
                idMode = true
                if (pages.isEmpty()) {
                    checkCameraPermissionAndStart(replaceSelected = false)
                }
            }
            else -> {
                if (pages.isEmpty()) {
                    checkCameraPermissionAndStart(replaceSelected = false)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (BuildConfig.DEBUG && !debugFeedRegistered) {
            val filter = IntentFilter(ACTION_DEBUG_FEED_PAGE)
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(debugFeedReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(debugFeedReceiver, filter)
            }
            debugFeedRegistered = true
        }
    }

    override fun onStop() {
        if (debugFeedRegistered) {
            try {
                unregisterReceiver(debugFeedReceiver)
            } catch (_: Exception) {
            }
            debugFeedRegistered = false
        }
        super.onStop()
    }

    private fun bindViews() {
        tvTitle = findViewById(R.id.tvScanTitle)
        tvStatus = findViewById(R.id.tvStatus)
        tvLiveHint = findViewById(R.id.tvLiveHint)
        ivPreview = findViewById(R.id.ivPreview)
        cropOverlay = findViewById(R.id.cropOverlay)
        quadAdjustOverlay = findViewById(R.id.quadAdjustOverlay)
        edgeOverlay = findViewById(R.id.edgeOverlay)
        cardGuideOverlay = findViewById(R.id.cardGuideOverlay)
        previewView = findViewById(R.id.previewView)
        liveCapturePane = findViewById(R.id.liveCapturePane)
        thumbStrip = findViewById(R.id.thumbStrip)
        progressSaving = findViewById(R.id.progressSaving)
        emptyPane = findViewById(R.id.emptyCapturePane)
        filterRow = findViewById(R.id.filterRow)
        chipOriginal = findViewById(R.id.chipOriginal)
        chipEnhance = findViewById(R.id.chipEnhance)
        chipBw = findViewById(R.id.chipBw)
        compressRow = findViewById(R.id.compressRow)
        chipCompressNormal = findViewById(R.id.chipCompressNormal)
        chipCompressSmaller = findViewById(R.id.chipCompressSmaller)
        btnRotateLeft = findViewById(R.id.btnRotateLeft)
        btnRotateRight = findViewById(R.id.btnRotateRight)
        btnToggleCrop = findViewById(R.id.btnToggleCrop)
        btnAdjustCorners = findViewById(R.id.btnAdjustCorners)
        btnRetake = findViewById(R.id.btnRetake)
        btnKeepScanning = findViewById(R.id.btnKeepScanning)
        btnApplyAdjust = findViewById(R.id.btnApplyAdjust)
        btnSavePdf = findViewById(R.id.btnSavePdf)
        captureButton = findViewById(R.id.captureButton)
        btnFromPhotos = findViewById(R.id.btnFromPhotos)
        btnCloseLive = findViewById(R.id.btnCloseLive)
        btnRetryCamera = findViewById(R.id.btnRetryCamera)
        btnEmptyFromPhotos = findViewById(R.id.btnEmptyFromPhotos)

        // Explicit <View>: bare listOf() infers RecyclerView from thumbStrip and
        // crashes with ArrayStoreException when storing FrameLayout previewContainer.
        reviewViews = listOf<View>(
            findViewById(R.id.previewContainer),
            thumbStrip,
            filterRow,
            findViewById(R.id.editRow),
            compressRow,
            findViewById(R.id.actionRow)
        )

        cropOverlay.onCropChanged = { l, t, r, b ->
            currentPage()?.let {
                it.cropLeft = l
                it.cropTop = t
                it.cropRight = r
                it.cropBottom = b
            }
        }
        quadAdjustOverlay.onQuadChanged = { q ->
            currentPage()?.adjustQuad = Array(4) { PointF(q[it].x, q[it].y) }
        }
    }

    private fun setupThumbs() {
        thumbAdapter = ThumbAdapter(
            pages = pages,
            selectedIndexProvider = { selectedIndex },
            idModeProvider = { idMode },
            onSelect = { index ->
                commitCropToPage()
                selectedIndex = index
                cropMode = false
                val page = pages.getOrNull(index)
                if (page?.needsAdjust == true) {
                    enterAdjustModeForSelected()
                } else {
                    adjustMode = false
                    updateAdjustUi()
                    updateCropUi()
                    refreshThumbs()
                    renderSelectedPreview()
                }
            }
        )
        thumbStrip.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        thumbStrip.adapter = thumbAdapter
    }

    private fun setupControls() {
        captureButton.setOnClickListener { takeManualCapture() }
        btnFromPhotos.setOnClickListener {
            stopLiveCamera()
            openPhotoPicker()
        }
        btnEmptyFromPhotos.setOnClickListener { openPhotoPicker() }
        btnCloseLive.setOnClickListener {
            stopLiveCamera()
            if (pages.isEmpty()) showEmptyState() else showReviewState()
        }
        btnRetryCamera.setOnClickListener {
            checkCameraPermissionAndStart(replaceSelected = false)
        }

        chipOriginal.setOnClickListener { setFilter(ScanFilter.ORIGINAL) }
        chipEnhance.setOnClickListener { setFilter(ScanFilter.ENHANCE) }
        chipBw.setOnClickListener { setFilter(ScanFilter.BW) }
        chipCompressNormal.setOnClickListener {
            chipCompressNormal.isChecked = true
        }
        chipCompressSmaller.setOnClickListener {
            if (!DocumentEntitlement.isDocumentPro(this)) {
                chipCompressNormal.isChecked = true
                DocumentEntitlement.openPaywall(
                    this,
                    "Smaller file is a Document Pro perk — unlimited tools, ad-free, clean PDF export."
                )
                return@setOnClickListener
            }
            chipCompressSmaller.isChecked = true
        }

        btnRotateLeft.setOnClickListener {
            if (adjustMode) return@setOnClickListener
            commitCropToPage()
            currentPage()?.rotateBy(-90)
            cropMode = false
            updateCropUi()
            renderSelectedPreview()
            refreshThumbs()
        }
        btnRotateRight.setOnClickListener {
            if (adjustMode) return@setOnClickListener
            commitCropToPage()
            currentPage()?.rotateBy(90)
            cropMode = false
            updateCropUi()
            renderSelectedPreview()
            refreshThumbs()
        }
        btnToggleCrop.setOnClickListener {
            if (currentPage() == null || adjustMode) return@setOnClickListener
            if (cropMode) {
                commitCropToPage()
                cropMode = false
                renderSelectedPreview()
            } else {
                cropMode = true
                syncCropOverlayFromPage()
            }
            updateCropUi()
        }
        btnApplyAdjust.setOnClickListener { applyQuadAdjust() }
        btnAdjustCorners.setOnClickListener { enterAdjustModeForSelected() }
        btnRetake.setOnClickListener {
            if (pages.isEmpty()) {
                checkCameraPermissionAndStart(false)
            } else {
                retakePending = true
                checkCameraPermissionAndStart(replaceSelected = true)
            }
        }
        btnKeepScanning.setOnClickListener {
            if (adjustMode) {
                Toast.makeText(this, "Apply the crop first — or Retake", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            commitCropToPage()
            cropMode = false
            updateCropUi()
            if (idMode && pages.size >= 2) {
                Toast.makeText(
                    this,
                    "ID scan is Front + Back only — retake a side if needed",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            checkCameraPermissionAndStart(replaceSelected = false)
        }
        btnSavePdf.setOnClickListener {
            if (adjustMode) {
                Toast.makeText(this, "Drag the corners, then tap Apply", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            savePdf()
        }
    }

    private fun currentPage(): ScanPage? = pages.getOrNull(selectedIndex)

    private fun setFilter(filter: ScanFilter) {
        if (adjustMode) {
            Toast.makeText(this, "Apply the crop first", Toast.LENGTH_SHORT).show()
            return
        }
        currentPage()?.filter = filter
        when (filter) {
            ScanFilter.ORIGINAL -> chipOriginal.isChecked = true
            ScanFilter.ENHANCE -> chipEnhance.isChecked = true
            ScanFilter.BW -> chipBw.isChecked = true
        }
        if (!cropMode) renderSelectedPreview()
        refreshThumbs()
    }

    private fun showEmptyState() {
        stopLiveCamera()
        emptyPane.isVisible = true
        liveCapturePane.isVisible = false
        reviewViews.forEach { it.isVisible = false }
        tvTitle.text = "Scan document"
        tvStatus.text = "Capture pages, then review and Save PDF"
    }

    private fun showLiveCaptureState() {
        emptyPane.isVisible = false
        liveCapturePane.isVisible = true
        reviewViews.forEach { it.isVisible = false }
        if (idMode) {
            tvTitle.text = "Scan ID"
            val side = idSideLabel()
            cardGuideOverlay.isVisible = true
            cardGuideOverlay.setSideLabel(side)
            tvStatus.text = if (retakePending) {
                "Retake $side — align card in the frame"
            } else {
                "$side — align card in the frame"
            }
            tvLiveHint.text = "Hold phone above the ID — fit the card in the gold frame, then tap ●"
            btnFromPhotos.isVisible = false
        } else {
            tvTitle.text = "Scan document"
            cardGuideOverlay.isVisible = false
            tvStatus.text = if (retakePending) "Retake page — hold steady" else "Page ${pages.size + 1} — hold steady"
            tvLiveHint.text = "Hold phone above the page — hold steady until lock, or tap ●"
            btnFromPhotos.isVisible = true
        }
        edgeOverlay.clear()
    }

    private fun idSideLabel(): String {
        return when {
            retakePending && selectedIndex == 0 -> "Front of ID"
            retakePending && selectedIndex == 1 -> "Back of ID"
            pages.isEmpty() -> "Front of ID"
            pages.size == 1 -> "Back of ID"
            else -> "ID card"
        }
    }

    private fun showReviewState() {
        stopLiveCamera()
        emptyPane.isVisible = false
        liveCapturePane.isVisible = false
        cardGuideOverlay.isVisible = false
        reviewViews.forEach { it.isVisible = true }
        tvTitle.text = when {
            adjustMode -> "Adjust crop"
            idMode -> "Review ID"
            else -> "Review scan"
        }
        updateStatus()
        updateAdjustUi()
    }

    private fun updateStatus() {
        val n = pages.size
        tvStatus.text = when {
            n == 0 -> "No pages yet"
            adjustMode -> "Page ${selectedIndex + 1} of $n · drag the 4 corners, then Apply"
            idMode && n == 1 -> "Front ready · Scan back, or Save (1 side)"
            idMode && n >= 2 -> "Front + Back · retake a side or Save PDF"
            else -> "Page ${selectedIndex + 1} of $n · filter, Adjust corners, or Save PDF"
        }
    }

    private fun checkCameraPermissionAndStart(replaceSelected: Boolean) {
        retakePending = replaceSelected
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startLiveCamera(replaceSelected)
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startLiveCamera(replaceSelected: Boolean) {
        retakePending = replaceSelected
        autoArmed = true
        lastQuad = null
        lastScore = 0f
        edgeTracker.reset()
        lastAnalyzeMs.set(0L)
        capturing.set(false)
        showLiveCaptureState()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider
                // Bind after PreviewView has a real size — avoids black preview on some Samsungs
                previewView.post {
                    if (isFinishing || isDestroyed) return@post
                    bindCameraUseCases(provider)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Camera start failed", e)
                Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_LONG).show()
                showEmptyState()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases(provider: ProcessCameraProvider) {
        provider.unbindAll()
        stopPreviewDetectLoop()
        analysisExecutor?.shutdown()
        analysisExecutor = Executors.newSingleThreadExecutor()

        val rotation = previewView.display?.rotation ?: windowManager.defaultDisplay.rotation

        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

        val preview = Preview.Builder()
            .setTargetRotation(rotation)
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(rotation)
            .build()

        // Preview + Capture only. Live edges come from PreviewView.bitmap so they
        // match what the user sees (ImageAnalysis was returning near-black frames on S25).
        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        provider.bindToLifecycle(this, selector, preview, imageCapture)
        Log.i(TAG, "Camera bound Preview+Capture; starting PreviewView detect loop")
        startPreviewDetectLoop()
    }

    private fun startPreviewDetectLoop() {
        previewDetectRunning = true
        previewDetectHandler.removeCallbacks(previewDetectTick)
        previewDetectHandler.postDelayed(previewDetectTick, 200L)
    }

    private fun stopPreviewDetectLoop() {
        previewDetectRunning = false
        previewDetectHandler.removeCallbacks(previewDetectTick)
    }

    /**
     * Sample the live preview pixels and run OpenCV/Kotlin detect.
     * Quads are already in PreviewView/overlay-normalized space — no CoordinateTransform.
     *
     * [PreviewView.getBitmap] MUST run on the main thread (CameraX Preconditions).
     * Grab here (handler is main), then detect off-main.
     */
    private fun samplePreviewAndDetect() {
        if (!analyzing.compareAndSet(false, true)) return
        val executor = analysisExecutor ?: run {
            analyzing.set(false)
            return
        }
        // Main-thread grab — calling getBitmap off-main always throws and yields no outline.
        val bmp = try {
            previewView.bitmap
        } catch (e: Exception) {
            Log.w(TAG, "previewView.bitmap failed", e)
            analyzing.set(false)
            onPreviewTooDark(0f)
            return
        }
        if (bmp == null || bmp.width < 32 || bmp.height < 32) {
            if (System.currentTimeMillis() % 1000L < 150L) {
                Log.d(TAG, "preview bitmap unavailable")
            }
            analyzing.set(false)
            onPreviewTooDark(0f)
            return
        }
        executor.execute {
            try {
                val mean = sampleMeanLuma(bmp)
                if (System.currentTimeMillis() % 1500L < 150L) {
                    Log.d(TAG, "previewBmp ${bmp.width}x${bmp.height} mean=${"%.1f".format(mean)}")
                }
                if (mean < 12f) {
                    // Flat on table / lens covered — don't show Ready
                    runOnUiThread { onPreviewTooDark(mean) }
                    return@execute
                }
                val detection = OpenCvDocumentDetector.detectBitmap(bmp)
                    ?: DocumentEdgeDetector.detectBitmap(bmp, DocumentEdgeDetector.Mode.LIVE)
                if (detection != null) {
                    Log.d(TAG, "preview HIT score=${"%.2f".format(detection.score)} mean=${"%.0f".format(mean)}")
                } else if (System.currentTimeMillis() % 1000L < 150L) {
                    Log.d(TAG, "preview MISS mean=${"%.0f".format(mean)}")
                }
                // Already view-normalized
                runOnUiThread { onDetection(detection) }
            } catch (e: Exception) {
                Log.w(TAG, "preview detect failed", e)
                runOnUiThread { onDetection(null) }
            } finally {
                try {
                    bmp.recycle()
                } catch (_: Exception) {
                }
                analyzing.set(false)
            }
        }
    }

    private fun sampleMeanLuma(bmp: Bitmap): Float {
        val w = bmp.width
        val h = bmp.height
        var sum = 0L
        var n = 0
        var y = h / 10
        while (y < h * 9 / 10) {
            var x = w / 10
            while (x < w * 9 / 10) {
                val c = bmp.getPixel(x, y)
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val b = c and 0xff
                sum += (0.299 * r + 0.587 * g + 0.114 * b).toLong()
                n++
                x += w / 20
            }
            y += h / 20
        }
        return if (n == 0) 0f else sum.toFloat() / n
    }

    /**
     * Preview is nearly black — usually phone laid flat on the table covering the lens.
     * Clear overlay/Ready and show an explicit tip. Manual shutter still works.
     */
    private fun onPreviewTooDark(mean: Float) {
        if (!liveCapturePane.isVisible || capturing.get()) return
        edgeTracker.reset()
        lastQuad = null
        lastScore = 0f
        edgeOverlay.clear()
        tvLiveHint.text = "Hold phone above the page — not flat on the table"
        if (System.currentTimeMillis() % 2000L < 150L) {
            Log.d(TAG, "preview too dark mean=${"%.1f".format(mean)} — tip shown")
        }
    }

    /** [detection] quad is already in overlay-normalized PreviewView space. */
    private fun onDetection(detection: DocumentEdgeDetector.Detection?) {
        if (!liveCapturePane.isVisible || capturing.get()) return

        val now = System.currentTimeMillis()
        val frame = edgeTracker.onFrame(detection, now)
        lastQuad = frame.displayQuad
        lastScore = frame.smoothedScore

        if (detection != null) {
            Log.d(
                TAG,
                "det score=${"%.2f".format(detection.score)} " +
                    "vis=${frame.visible} hold=${frame.holdProgress}/${frame.holdNeeded} " +
                    "lock=${frame.lockedForCapture} armed=$autoArmed"
            )
        }

        if (frame.displayQuad == null) {
            edgeOverlay.clear()
            tvLiveHint.text = "Align the page — fill most of the frame, then hold still"
            return
        }

        // Gold fill brightens while stable; patient hold before auto.
        edgeOverlay.setQuad(frame.displayQuad, frame.ready || frame.lockedForCapture)

        tvLiveHint.text = when {
            frame.lockedForCapture -> "Locked — capturing…"
            frame.holdProgress > 0 ->
                "Hold steady… ${frame.holdProgress}/${frame.holdNeeded}"
            frame.visible -> "Page found — hold steady until lock, or tap ●"
            else -> "Align the page inside the frame"
        }

        // Auto when tracker locks. Only disarm AFTER takeCapture actually starts.
        if (frame.lockedForCapture && autoArmed && !capturing.get()) {
            Log.i(TAG, "AUTO_FIRE score=${"%.2f".format(frame.smoothedScore)} hold=${frame.holdProgress}")
            takeCapture(auto = true)
        }
    }

    private fun takeManualCapture() {
        if (!liveCapturePane.isVisible) {
            checkCameraPermissionAndStart(false)
            return
        }
        takeCapture(auto = false)
    }

    private fun takeCapture(auto: Boolean) {
        val capture = imageCapture
        if (capture == null) {
            Log.e(TAG, "takeCapture aborted: imageCapture=null auto=$auto")
            return
        }
        if (!capturing.compareAndSet(false, true)) {
            Log.w(TAG, "takeCapture skipped: already capturing auto=$auto")
            return
        }
        // Disarm only after we own the capture latch — avoids stuck no-auto on silent fail
        if (auto) autoArmed = false

        Log.i(TAG, "CAPTURE_START auto=$auto")
        tvLiveHint.text = if (auto) "Auto-capturing…" else "Capturing…"
        val photoFile = File(cacheDir, "scan_${System.currentTimeMillis()}.jpg")
        val output = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        capture.takePicture(
            output,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.i(TAG, "CAPTURE_OK auto=$auto file=${photoFile.name}")
                    // Do NOT use live overlay quads for warp — they are in PreviewView space,
                    // not JPEG sensor space. Always re-detect on the still.
                    stopLiveCamera()
                    lifecycleScope.launch {
                        val captured = withContext(Dispatchers.IO) {
                            finalizeCapturedPage(photoFile)
                        }
                        capturing.set(false)
                        if (captured == null) {
                            Toast.makeText(this@ScanActivity, "Capture failed", Toast.LENGTH_SHORT).show()
                            if (pages.isEmpty()) showEmptyState() else showReviewState()
                            return@launch
                        }
                        addOrReplacePage(captured, replaceSelected = retakePending)
                        retakePending = false
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing.set(false)
                    autoArmed = true
                    Log.e(TAG, "CAPTURE_ERR auto=$auto", exception)
                    Toast.makeText(
                        this@ScanActivity,
                        "Capture failed: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    /**
     * Decode capture with EXIF orientation, detect corners + confidence.
     * High confidence → warp now (auto-Apply). Low/medium → keep still for corner UI.
     */
    private fun finalizeCapturedPage(photoFile: File): CapturedPage? {
        val oriented = decodeCaptureOriented(photoFile) ?: return null
        return try {
            val originalFile = File(cacheDir, "scan_orig_${System.currentTimeMillis()}.jpg")
            FileOutputStream(originalFile).use { fos ->
                oriented.compress(Bitmap.CompressFormat.JPEG, 92, fos)
            }
            val det = bestStillDetection(oriented)
            val score = det?.score ?: 0f
            val quad = det?.quad?.let {
                PerspectiveHelper.insetQuad(it, STILL_QUAD_INSET * 0.5f)
            } ?: QuadAdjustOverlayView.defaultQuad()
            Log.i(
                TAG,
                "still corners score=${"%.2f".format(score)} area=${"%.2f".format(polygonAreaNorm(quad))}"
            )

            val high = score >= HIGH_AUTO_APPLY_SCORE && det != null
            if (high) {
                val warped = deskewStill(oriented, quad)
                if (warped != null) {
                    val flat = File(cacheDir, "scan_flat_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(flat).use { fos ->
                        warped.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                    }
                    if (!warped.isRecycled) warped.recycle()
                    Log.i(TAG, "AUTO_APPLY score=${"%.2f".format(score)} file=${flat.name}")
                    if (!oriented.isRecycled) oriented.recycle()
                    if (photoFile.exists()) photoFile.delete()
                    return CapturedPage(
                        displayPath = flat.absolutePath,
                        originalPath = originalFile.absolutePath,
                        adjustQuad = quad,
                        score = score,
                        autoApplied = true
                    )
                }
                Log.w(TAG, "AUTO_APPLY warp failed — falling back to corner UI")
            }

            if (!oriented.isRecycled) oriented.recycle()
            if (photoFile.exists()) photoFile.delete()
            CapturedPage(
                displayPath = originalFile.absolutePath,
                originalPath = originalFile.absolutePath,
                adjustQuad = quad,
                score = score,
                autoApplied = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "finalize failed", e)
            if (!oriented.isRecycled) oriented.recycle()
            null
        }
    }

    /** Photo-import twin of [finalizeCapturedPage]. */
    private fun finalizeImportedStill(path: String): CapturedPage {
        val bmp = BitmapFactory.decodeFile(path)
        if (bmp == null) {
            return CapturedPage(
                displayPath = path,
                originalPath = path,
                adjustQuad = QuadAdjustOverlayView.defaultQuad(),
                score = 0f,
                autoApplied = false
            )
        }
        return try {
            val det = bestStillDetection(bmp)
            val score = det?.score ?: 0f
            val quad = det?.quad?.let {
                PerspectiveHelper.insetQuad(it, STILL_QUAD_INSET * 0.5f)
            } ?: QuadAdjustOverlayView.defaultQuad()
            if (score >= HIGH_AUTO_APPLY_SCORE && det != null) {
                val warped = deskewStill(bmp, quad)
                if (warped != null) {
                    val flat = File(cacheDir, "scan_flat_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(flat).use { fos ->
                        warped.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                    }
                    if (!warped.isRecycled) warped.recycle()
                    Log.i(TAG, "AUTO_APPLY import score=${"%.2f".format(score)} file=${flat.name}")
                    return CapturedPage(
                        displayPath = flat.absolutePath,
                        originalPath = path,
                        adjustQuad = quad,
                        score = score,
                        autoApplied = true
                    )
                }
            }
            CapturedPage(
                displayPath = path,
                originalPath = path,
                adjustQuad = quad,
                score = score,
                autoApplied = false
            )
        } finally {
            if (!bmp.isRecycled) bmp.recycle()
        }
    }

    /** Best still-detect result (quad + score). Prefer tighter area among strong scores. */
    private fun bestStillDetection(src: Bitmap): DocumentEdgeDetector.Detection? {
        val candidates = mutableListOf<Pair<String, DocumentEdgeDetector.Detection>>()
        OpenCvDocumentDetector.detectBitmap(src, analyzeWidth = 960)?.let { candidates.add("ocv960" to it) }
        OpenCvDocumentDetector.detectBitmap(src, analyzeWidth = 640)?.let { candidates.add("ocv640" to it) }
        DocumentEdgeDetector.detectBitmap(src, DocumentEdgeDetector.Mode.STILL)?.let { candidates.add("ktStill" to it) }
        DocumentEdgeDetector.detectBitmap(src, DocumentEdgeDetector.Mode.STILL_STRONG)?.let {
            candidates.add("ktStrong" to it)
        }
        val ranked = candidates
            .filter { it.second.score >= STILL_WARP_SCORE }
            .sortedWith(
                compareByDescending<Pair<String, DocumentEdgeDetector.Detection>> {
                    val a = polygonAreaNorm(it.second.quad)
                    it.second.score - (a - 0.55f).coerceAtLeast(0f) * 0.35f
                }.thenBy { polygonAreaNorm(it.second.quad) }
            )
        for ((name, det) in ranked) {
            Log.i(
                TAG,
                "still detect $name score=${"%.2f".format(det.score)} " +
                    "area=${"%.2f".format(polygonAreaNorm(det.quad))}"
            )
            return det
        }
        candidates.maxByOrNull { it.second.score }?.let { (name, det) ->
            Log.i(TAG, "still detect weak $name score=${"%.2f".format(det.score)}")
            return det
        }
        return null
    }

    private fun bestStillQuad(src: Bitmap): Array<PointF>? = bestStillDetection(src)?.quad

    /**
     * Perspective-warp [src] with [quadNorm]. Prefer warp; bbox crop last resort.
     * Still-only (Apply path). Live auto is untouched.
     */
    private fun deskewStill(src: Bitmap, quadNorm: Array<PointF>? = null): Bitmap? {
        if (quadNorm != null && quadNorm.size >= 4) {
            val tight = PerspectiveHelper.insetQuad(quadNorm, STILL_QUAD_INSET)
            val warped = PerspectiveHelper.warpDocument(src, tight)
            if (warped != null) {
                val trimmed = PerspectiveHelper.trimDarkMargins(warped)
                if (trimmed !== warped && !warped.isRecycled) warped.recycle()
                Log.i(TAG, "still WARP ok apply inset=$STILL_QUAD_INSET ${trimmed.width}x${trimmed.height}")
                return trimmed
            }
            val box = PerspectiveHelper.boundingCrop(tight)
            val cropped = cropNormalized(src, box) ?: return null
            val trimmed = PerspectiveHelper.trimDarkMargins(cropped)
            if (trimmed !== cropped && !cropped.isRecycled) cropped.recycle()
            Log.i(TAG, "still bbox crop fallback after apply")
            return trimmed
        }
        val q = bestStillQuad(src) ?: return null
        return deskewStill(src, q)
    }

    private fun polygonAreaNorm(quad: Array<PointF>): Float {
        if (quad.size < 3) return 1f
        var sum = 0f
        for (i in quad.indices) {
            val j = (i + 1) % quad.size
            sum += quad[i].x * quad[j].y - quad[j].x * quad[i].y
        }
        return abs(sum) * 0.5f
    }

    private fun decodeCaptureOriented(file: File): Bitmap? {
        val raw = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        return try {
            val exif = ExifInterface(file.absolutePath)
            val orient = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val degrees = when (orient) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
            if (degrees == 0) return raw
            val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
            val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
            if (rotated !== raw && !raw.isRecycled) raw.recycle()
            rotated
        } catch (_: Exception) {
            raw
        }
    }

    private fun cropNormalized(src: Bitmap, box: FloatArray): Bitmap? {
        val l = (box[0] * src.width).toInt().coerceIn(0, src.width - 2)
        val t = (box[1] * src.height).toInt().coerceIn(0, src.height - 2)
        val r = (box[2] * src.width).toInt().coerceIn(l + 1, src.width)
        val b = (box[3] * src.height).toInt().coerceIn(t + 1, src.height)
        if (r - l < 32 || b - t < 32) return null
        return Bitmap.createBitmap(src, l, t, r - l, b - t)
    }

    private fun stopLiveCamera() {
        stopPreviewDetectLoop()
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {
        }
        analysisExecutor?.shutdown()
        analysisExecutor = null
        imageCapture = null
        edgeOverlay.clear()
        liveCapturePane.isVisible = false
    }

    private fun openPhotoPicker() {
        pickImagesLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun addOrReplacePage(captured: CapturedPage, replaceSelected: Boolean) {
        val page = ScanPage(
            sourcePath = captured.displayPath,
            originalPath = captured.originalPath,
            adjustQuad = Array(4) { PointF(captured.adjustQuad[it].x, captured.adjustQuad[it].y) },
            needsAdjust = !captured.autoApplied,
            detectScore = captured.score
        )
        val wasReplace = replaceSelected
        if (replaceSelected && pages.isNotEmpty() && selectedIndex in pages.indices) {
            pages[selectedIndex] = page
        } else {
            pages.add(page)
            selectedIndex = pages.lastIndex
        }
        cropMode = false
        // ID Front just added → prompt Back after adjust/apply (unless retake).
        idAwaitingBack = idMode && !wasReplace && pages.size == 1
        if (captured.autoApplied) {
            adjustMode = false
            Log.i(TAG, "skip corner UI — high confidence score=${"%.2f".format(captured.score)}")
            showReviewState()
            updateCropUi()
            refreshThumbs()
            renderSelectedPreview()
            if (idAwaitingBack) {
                Toast.makeText(this, "Front captured — now scan the back", Toast.LENGTH_SHORT).show()
                continueIdBackCapture()
            } else {
                Toast.makeText(this, "Crop applied — Adjust corners anytime", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.i(TAG, "show corner UI — score=${"%.2f".format(captured.score)} < $HIGH_AUTO_APPLY_SCORE")
            enterAdjustModeForSelected()
        }
    }

    /** After Front is ready in ID mode, open live camera for Back. */
    private fun continueIdBackCapture() {
        if (!idMode || !idAwaitingBack || pages.size != 1) return
        idAwaitingBack = false
        lifecycleScope.launch {
            delay(450)
            if (isFinishing || isDestroyed) return@launch
            if (pages.size != 1 || adjustMode) return@launch
            checkCameraPermissionAndStart(replaceSelected = false)
        }
    }

    private fun enterAdjustModeForSelected() {
        val page = currentPage() ?: return
        adjustMode = true
        cropMode = false
        if (page.adjustQuad == null) {
            page.adjustQuad = QuadAdjustOverlayView.defaultQuad()
        }
        page.needsAdjust = true
        // Prefer unwarped still for corner dragging
        if (page.originalPath != null) {
            // Keep sourcePath as current flat for after Apply; adjust preview uses originalPath
        }
        showReviewState()
        updateCropUi()
        refreshThumbs()
        renderSelectedPreview()
    }

    private fun updateAdjustUi() {
        val page = currentPage()
        val adjusting = adjustMode && page?.needsAdjust == true
        adjustMode = adjusting
        quadAdjustOverlay.isVisible = adjusting
        btnApplyAdjust.isVisible = adjusting
        btnSavePdf.isVisible = !adjusting
        btnKeepScanning.isVisible = !adjusting
        if (idMode) {
            btnKeepScanning.text = when {
                pages.size >= 2 -> "Front + Back"
                pages.size == 1 -> "Scan back"
                else -> "Scan ID"
            }
            btnKeepScanning.isEnabled = !adjusting && pages.size < 2
        } else {
            btnKeepScanning.text = "Keep scanning"
            btnKeepScanning.isEnabled = !adjusting
        }
        filterRow.isVisible = !adjusting && !liveCapturePane.isVisible && pages.isNotEmpty()
        compressRow.isVisible = !adjusting && !liveCapturePane.isVisible && pages.isNotEmpty()
        btnToggleCrop.isVisible = !adjusting
        // Escape hatch: always after Apply / auto-Apply when we still have the original still
        btnAdjustCorners.isVisible = !adjusting && page?.originalPath != null && pages.isNotEmpty()
        btnRotateLeft.isEnabled = !adjusting
        btnRotateRight.isEnabled = !adjusting
        tvTitle.text = when {
            adjusting -> "Adjust crop"
            idMode && pages.isNotEmpty() -> "Review ID"
            pages.isNotEmpty() -> "Review scan"
            else -> tvTitle.text
        }
        updateStatus()
    }

    private fun applyQuadAdjust() {
        val page = currentPage() ?: return
        if (!adjustMode) return
        val quad = quadAdjustOverlay.getNormalizedQuad()
        page.adjustQuad = Array(4) { PointF(quad[it].x, quad[it].y) }
        progressSaving.isVisible = true
        btnApplyAdjust.isEnabled = false
        lifecycleScope.launch {
            try {
                val newPath = withContext(Dispatchers.IO) {
                    val srcPath = page.pathForCornerAdjust()
                    val src = BitmapFactory.decodeFile(srcPath) ?: return@withContext null
                    try {
                        val warped = deskewStill(src, quad) ?: return@withContext null
                        val out = File(cacheDir, "scan_flat_${System.currentTimeMillis()}.jpg")
                        FileOutputStream(out).use { fos ->
                            warped.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                        }
                        if (!warped.isRecycled) warped.recycle()
                        // Keep originalPath; only replace previous flat if different
                        val prev = page.sourcePath
                        if (prev != page.originalPath && prev != out.absolutePath) {
                            val old = File(prev)
                            if (old.exists()) old.delete()
                        }
                        out.absolutePath
                    } finally {
                        if (!src.isRecycled) src.recycle()
                    }
                }
                if (newPath == null) {
                    Toast.makeText(this@ScanActivity, "Could not apply crop", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                page.sourcePath = newPath
                if (page.originalPath == null) {
                    // Should already be set; keep adjustQuad for escape hatch
                }
                page.needsAdjust = false
                page.resetCrop()
                page.rotationDegrees = 0
                adjustMode = false
                Log.i(TAG, "APPLY_QUAD ok file=${File(newPath).name}")
                updateAdjustUi()
                updateCropUi()
                refreshThumbs()
                renderSelectedPreview()
                if (idAwaitingBack) {
                    Toast.makeText(
                        this@ScanActivity,
                        "Front ready — now scan the back",
                        Toast.LENGTH_SHORT
                    ).show()
                    continueIdBackCapture()
                } else {
                    Toast.makeText(
                        this@ScanActivity,
                        "Crop applied — Adjust corners anytime",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                progressSaving.isVisible = false
                btnApplyAdjust.isEnabled = true
            }
        }
    }

    private fun refreshThumbs() {
        thumbAdapter.notifyDataSetChanged()
        updateStatus()
    }

    private fun renderSelectedPreview() {
        val page = currentPage() ?: return
        syncFilterChips(page.filter)
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.Default) {
                // During corner adjust, show the unwarped still (no axis crop / filter)
                if (adjustMode && page.needsAdjust) {
                    val clone = page.copy(
                        sourcePath = page.pathForCornerAdjust(),
                        filter = ScanFilter.ORIGINAL,
                        cropLeft = 0f,
                        cropTop = 0f,
                        cropRight = 1f,
                        cropBottom = 1f,
                        rotationDegrees = 0
                    )
                    ScanPageRenderer.renderForPreview(clone)
                } else {
                    ScanPageRenderer.renderForPreview(page)
                }
            }
            previewBitmap?.let { if (!it.isRecycled) it.recycle() }
            previewBitmap = bmp
            if (bmp == null) {
                Toast.makeText(this@ScanActivity, "Could not load page image", Toast.LENGTH_SHORT).show()
                return@launch
            }
            ivPreview.setImageBitmap(bmp)
            ivPreview.post {
                syncOverlayBounds()
                if (cropMode) syncCropOverlayFromPage()
                if (adjustMode && page.needsAdjust) {
                    val q = page.copyAdjustQuad() ?: QuadAdjustOverlayView.defaultQuad()
                    quadAdjustOverlay.setNormalizedQuad(q)
                }
            }
        }
    }

    private fun syncFilterChips(filter: ScanFilter) {
        chipOriginal.isChecked = filter == ScanFilter.ORIGINAL
        chipEnhance.isChecked = filter == ScanFilter.ENHANCE
        chipBw.isChecked = filter == ScanFilter.BW
    }

    private fun updateCropUi() {
        cropOverlay.isVisible = cropMode && !adjustMode
        btnToggleCrop.text = if (cropMode) "Done crop" else "Crop"
        if (cropMode && !adjustMode) {
            val page = currentPage() ?: return
            lifecycleScope.launch {
                val base = withContext(Dispatchers.Default) {
                    val clone = page.copy(
                        filter = ScanFilter.ORIGINAL,
                        cropLeft = 0f,
                        cropTop = 0f,
                        cropRight = 1f,
                        cropBottom = 1f
                    )
                    ScanPageRenderer.renderForPreview(clone)
                }
                previewBitmap?.let { if (!it.isRecycled) it.recycle() }
                previewBitmap = base
                ivPreview.setImageBitmap(base)
                ivPreview.post {
                    syncOverlayBounds()
                    syncCropOverlayFromPage()
                }
            }
        }
    }

    private fun syncOverlayBounds() {
        val drawable = ivPreview.drawable ?: return
        val vw = ivPreview.width.toFloat()
        val vh = ivPreview.height.toFloat()
        if (vw <= 0f || vh <= 0f) return
        val dw = drawable.intrinsicWidth.toFloat()
        val dh = drawable.intrinsicHeight.toFloat()
        if (dw <= 0f || dh <= 0f) return
        val scale = minOf(vw / dw, vh / dh)
        val shownW = dw * scale
        val shownH = dh * scale
        val left = (vw - shownW) / 2f + ivPreview.paddingLeft
        val top = (vh - shownH) / 2f + ivPreview.paddingTop
        val locIv = IntArray(2)
        val locCrop = IntArray(2)
        val locQuad = IntArray(2)
        ivPreview.getLocationInWindow(locIv)
        cropOverlay.getLocationInWindow(locCrop)
        quadAdjustOverlay.getLocationInWindow(locQuad)
        val cropOx = (locIv[0] - locCrop[0]).toFloat()
        val cropOy = (locIv[1] - locCrop[1]).toFloat()
        cropOverlay.imageBounds = RectF(
            cropOx + left, cropOy + top, cropOx + left + shownW, cropOy + top + shownH
        )
        val quadOx = (locIv[0] - locQuad[0]).toFloat()
        val quadOy = (locIv[1] - locQuad[1]).toFloat()
        quadAdjustOverlay.imageBounds = RectF(
            quadOx + left, quadOy + top, quadOx + left + shownW, quadOy + top + shownH
        )
    }

    private fun syncCropOverlayBounds() = syncOverlayBounds()

    private fun syncCropOverlayFromPage() {
        val page = currentPage() ?: return
        cropOverlay.setNormalizedCrop(page.cropLeft, page.cropTop, page.cropRight, page.cropBottom)
    }

    private fun commitCropToPage() {
        if (!cropMode) return
        val page = currentPage() ?: return
        val c = cropOverlay.getNormalizedCrop()
        page.cropLeft = c[0]
        page.cropTop = c[1]
        page.cropRight = c[2]
        page.cropBottom = c[3]
    }

    private fun savePdf() {
        if (saving) return
        commitCropToPage()
        if (pages.isEmpty()) {
            Toast.makeText(this, "Scan at least one page first", Toast.LENGTH_SHORT).show()
            return
        }

        val multiPage = pages.size > 1
        if (multiPage && !DocumentEntitlement.canUse(this, DocumentEntitlement.Feature.SCAN_TO_PDF)) {
            DocumentEntitlement.openPaywall(
                this,
                DocumentEntitlement.freeLimitMessage(this, DocumentEntitlement.Feature.SCAN_TO_PDF)
            )
            return
        }

        val wantSmaller = chipCompressSmaller.isChecked
        if (wantSmaller && !DocumentEntitlement.isDocumentPro(this)) {
            chipCompressNormal.isChecked = true
            DocumentEntitlement.openPaywall(
                this,
                "Smaller file is a Document Pro perk — unlimited tools, ad-free, clean PDF export."
            )
            return
        }
        val compression = if (wantSmaller) {
            ImagePdfWriter.Compression.SMALLER
        } else {
            ImagePdfWriter.Compression.NORMAL
        }

        saving = true
        progressSaving.isVisible = true
        btnSavePdf.isEnabled = false
        tvStatus.text = "Building PDF…"

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val bitmaps = pages.mapNotNull { ScanPageRenderer.renderForExport(it) }
                    if (bitmaps.isEmpty()) throw IllegalStateException("No page images")

                    val dir = File(getExternalFilesDir(null), "scans").apply { mkdirs() }
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val outName = if (idMode) {
                        "SmartDocAI_ID_$stamp.pdf"
                    } else {
                        "SmartDocAI_Scan_$stamp.pdf"
                    }
                    val outFile = File(dir, outName)
                    ImagePdfWriter.write(
                        bitmaps,
                        outFile,
                        watermark = DocumentEntitlement.shouldWatermarkPdf(this@ScanActivity),
                        compression = compression
                    )
                    bitmaps.forEach { if (!it.isRecycled) it.recycle() }

                    val uri = FileProvider.getUriForFile(
                        this@ScanActivity,
                        "${BuildConfig.APPLICATION_ID}.fileprovider",
                        outFile
                    )

                    val historyLabel = if (idMode) {
                        "ID / Aadhaar · ${pages.size} page(s) · image PDF"
                    } else {
                        "Scanned document · ${pages.size} page(s) · image PDF"
                    }
                    val dao = AppDatabase.getDatabase(this@ScanActivity).scannedFileDao()
                    dao.insert(
                        ScannedFile(
                            fileName = outFile.name,
                            fileUri = uri.toString(),
                            content = historyLabel,
                            aiResponse = null
                        )
                    )

                    if (multiPage) {
                        DocumentEntitlement.recordUse(
                            this@ScanActivity,
                            DocumentEntitlement.Feature.SCAN_TO_PDF
                        )
                    }

                    Pair(outFile, uri)
                }

                val (outFile, uri) = result
                progressSaving.isVisible = false
                btnSavePdf.isEnabled = true
                saving = false

                val returnIntent = Intent().apply {
                    putExtra(EXTRA_PDF_URI, uri.toString())
                    putExtra(EXTRA_PDF_PATH, outFile.absolutePath)
                    putExtra(EXTRA_PAGE_COUNT, pages.size)
                    putExtra(EXTRA_SCANNED_TEXT, "")
                }
                setResult(Activity.RESULT_OK, returnIntent)

                val freeMark = DocumentEntitlement.shouldWatermarkPdf(this@ScanActivity)
                AlertDialog.Builder(this@ScanActivity)
                    .setTitle("PDF saved")
                    .setMessage(
                        "${outFile.name}\n${pages.size} page(s) saved in SmartDocAI " +
                            "(app storage — not phone Downloads yet). " +
                            if (freeMark) {
                                "Free PDF includes a light mark — Unlock Pro for clean export. Share or Done."
                            } else {
                                "Share now, or open later from Recents / Files."
                            }
                    )
                    .setPositiveButton("Share") { _, _ ->
                        sharePdf(uri, outFile.name)
                    }
                    .setNeutralButton("Done") { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            } catch (e: Exception) {
                progressSaving.isVisible = false
                btnSavePdf.isEnabled = true
                saving = false
                tvStatus.text = "Could not save PDF"
                Toast.makeText(
                    this@ScanActivity,
                    "Save failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun sharePdf(uri: Uri, name: String) {
        DocumentEntitlement.requireCleanShareOrPaywall(this) {
            DocumentEntitlement.recordUse(this, DocumentEntitlement.Feature.CLEAN_SHARE)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = android.content.ClipData.newUri(contentResolver, name, uri)
            }
            startActivity(Intent.createChooser(share, "Share PDF"))
            finish()
        }
    }

    override fun onDestroy() {
        stopLiveCamera()
        previewBitmap?.let { if (!it.isRecycled) it.recycle() }
        previewBitmap = null
        super.onDestroy()
    }

    private class ThumbAdapter(
        private val pages: List<ScanPage>,
        private val selectedIndexProvider: () -> Int,
        private val idModeProvider: () -> Boolean,
        private val onSelect: (Int) -> Unit
    ) : RecyclerView.Adapter<ThumbAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val iv: ImageView = view.findViewById(R.id.ivThumb)
            val tv: TextView = view.findViewById(R.id.tvThumbIndex)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_scan_page_thumb, parent, false)
            return VH(v)
        }

        override fun getItemCount(): Int = pages.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.tv.text = when {
                idModeProvider() && position == 0 -> "Front"
                idModeProvider() && position == 1 -> "Back"
                else -> "${position + 1}"
            }
            val selected = position == selectedIndexProvider()
            holder.itemView.alpha = if (selected) 1f else 0.65f
            holder.itemView.setBackgroundColor(
                if (selected) 0x33C8A04A else 0x00000000
            )
            holder.itemView.setOnClickListener { onSelect(position) }

            val page = pages[position]
            holder.iv.tag = page.id
            Thread {
                val bmp = ScanPageRenderer.renderForPreview(page)
                holder.iv.post {
                    if (holder.iv.tag == page.id && bmp != null) {
                        holder.iv.setImageBitmap(bmp)
                    } else {
                        bmp?.recycle()
                    }
                }
            }.start()
        }
    }
}
