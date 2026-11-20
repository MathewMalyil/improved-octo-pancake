package com.mmalyil.smartdocai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
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
import androidx.camera.core.ImageAnalysis
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
import com.mmalyil.smartdocai.scan.CropOverlayView
import com.mmalyil.smartdocai.scan.DocumentEdgeDetector
import com.mmalyil.smartdocai.scan.DocumentEdgeTracker
import com.mmalyil.smartdocai.scan.EdgeOverlayView
import com.mmalyil.smartdocai.scan.ImagePdfWriter
import com.mmalyil.smartdocai.scan.PerspectiveHelper
import com.mmalyil.smartdocai.scan.PreviewCoordMapper
import com.mmalyil.smartdocai.scan.ScanFilter
import com.mmalyil.smartdocai.scan.ScanPage
import com.mmalyil.smartdocai.scan.ScanPageRenderer
import com.mmalyil.smartdocai.util.DocumentEntitlement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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

        const val EXTRA_PDF_URI = "pdfUri"
        const val EXTRA_PDF_PATH = "pdfPath"
        const val EXTRA_PAGE_COUNT = "scanPageCount"
        const val EXTRA_SCANNED_TEXT = "scannedText"

        /** Still re-detect must clear this to perspective-warp the capture. */
        private const val STILL_WARP_SCORE = 0.32f
    }

    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvLiveHint: TextView
    private lateinit var ivPreview: ImageView
    private lateinit var cropOverlay: CropOverlayView
    private lateinit var edgeOverlay: EdgeOverlayView
    private lateinit var previewView: PreviewView
    private lateinit var liveCapturePane: View
    private lateinit var thumbStrip: RecyclerView
    private lateinit var progressSaving: ProgressBar
    private lateinit var emptyPane: View
    private lateinit var reviewViews: List<View>

    private lateinit var chipOriginal: Chip
    private lateinit var chipEnhance: Chip
    private lateinit var chipBw: Chip
    private lateinit var btnRotateLeft: MaterialButton
    private lateinit var btnRotateRight: MaterialButton
    private lateinit var btnToggleCrop: MaterialButton
    private lateinit var btnRetake: MaterialButton
    private lateinit var btnKeepScanning: MaterialButton
    private lateinit var btnSavePdf: MaterialButton
    private lateinit var captureButton: MaterialButton
    private lateinit var btnFromPhotos: MaterialButton
    private lateinit var btnCloseLive: MaterialButton
    private lateinit var btnRetryCamera: MaterialButton
    private lateinit var btnEmptyFromPhotos: MaterialButton

    private val pages = mutableListOf<ScanPage>()
    private var selectedIndex = 0
    private var cropMode = false
    private var previewBitmap: Bitmap? = null
    private var saving = false
    private var retakePending = false

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
                imported.forEach { path -> pages.add(ScanPage(sourcePath = path)) }
                selectedIndex = pages.lastIndex
                showReviewState()
                refreshThumbs()
                renderSelectedPreview()
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

        when (intent.getStringExtra(EXTRA_START_MODE)) {
            MODE_PHOTOS -> {
                showEmptyState()
                openPhotoPicker()
            }
            else -> {
                if (pages.isEmpty()) {
                    checkCameraPermissionAndStart(replaceSelected = false)
                }
            }
        }
    }

    private fun bindViews() {
        tvTitle = findViewById(R.id.tvScanTitle)
        tvStatus = findViewById(R.id.tvStatus)
        tvLiveHint = findViewById(R.id.tvLiveHint)
        ivPreview = findViewById(R.id.ivPreview)
        cropOverlay = findViewById(R.id.cropOverlay)
        edgeOverlay = findViewById(R.id.edgeOverlay)
        previewView = findViewById(R.id.previewView)
        liveCapturePane = findViewById(R.id.liveCapturePane)
        thumbStrip = findViewById(R.id.thumbStrip)
        progressSaving = findViewById(R.id.progressSaving)
        emptyPane = findViewById(R.id.emptyCapturePane)
        chipOriginal = findViewById(R.id.chipOriginal)
        chipEnhance = findViewById(R.id.chipEnhance)
        chipBw = findViewById(R.id.chipBw)
        btnRotateLeft = findViewById(R.id.btnRotateLeft)
        btnRotateRight = findViewById(R.id.btnRotateRight)
        btnToggleCrop = findViewById(R.id.btnToggleCrop)
        btnRetake = findViewById(R.id.btnRetake)
        btnKeepScanning = findViewById(R.id.btnKeepScanning)
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
            findViewById(R.id.filterRow),
            findViewById(R.id.editRow),
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
    }

    private fun setupThumbs() {
        thumbAdapter = ThumbAdapter(
            pages = pages,
            selectedIndexProvider = { selectedIndex },
            onSelect = { index ->
                commitCropToPage()
                selectedIndex = index
                cropMode = false
                updateCropUi()
                refreshThumbs()
                renderSelectedPreview()
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

        btnRotateLeft.setOnClickListener {
            commitCropToPage()
            currentPage()?.rotateBy(-90)
            cropMode = false
            updateCropUi()
            renderSelectedPreview()
            refreshThumbs()
        }
        btnRotateRight.setOnClickListener {
            commitCropToPage()
            currentPage()?.rotateBy(90)
            cropMode = false
            updateCropUi()
            renderSelectedPreview()
            refreshThumbs()
        }
        btnToggleCrop.setOnClickListener {
            if (currentPage() == null) return@setOnClickListener
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
        btnRetake.setOnClickListener {
            if (pages.isEmpty()) {
                checkCameraPermissionAndStart(false)
            } else {
                retakePending = true
                checkCameraPermissionAndStart(replaceSelected = true)
            }
        }
        btnKeepScanning.setOnClickListener {
            commitCropToPage()
            cropMode = false
            updateCropUi()
            checkCameraPermissionAndStart(replaceSelected = false)
        }
        btnSavePdf.setOnClickListener { savePdf() }
    }

    private fun currentPage(): ScanPage? = pages.getOrNull(selectedIndex)

    private fun setFilter(filter: ScanFilter) {
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
        tvTitle.text = "Scan document"
        tvStatus.text = if (retakePending) "Retake page — hold steady" else "Page ${pages.size + 1} — hold steady"
        tvLiveHint.text = "Align page — hold ~1s when Ready, or tap ● anytime"
        edgeOverlay.clear()
    }

    private fun showReviewState() {
        stopLiveCamera()
        emptyPane.isVisible = false
        liveCapturePane.isVisible = false
        reviewViews.forEach { it.isVisible = true }
        tvTitle.text = "Review scan"
        updateStatus()
    }

    private fun updateStatus() {
        val n = pages.size
        tvStatus.text = if (n == 0) {
            "No pages yet"
        } else {
            "Page ${selectedIndex + 1} of $n · crop, rotate, or filter, then Save PDF"
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
                bindCameraUseCases(provider)
            } catch (e: Exception) {
                Log.e(TAG, "Camera start failed", e)
                Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_LONG).show()
                showEmptyState()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases(provider: ProcessCameraProvider) {
        provider.unbindAll()
        analysisExecutor?.shutdown()
        analysisExecutor = Executors.newSingleThreadExecutor()

        val rotation = previewView.display?.rotation ?: windowManager.defaultDisplay.rotation

        previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

        val preview = Preview.Builder()
            .setTargetRotation(rotation)
            .build()
            .also { it.surfaceProvider = previewView.surfaceProvider }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetRotation(rotation)
            .build()

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .setTargetRotation(rotation)
            .build()

        analysis.setAnalyzer(analysisExecutor!!) { imageProxy ->
            // Throttle ~8 fps — calmer overlay, less CPU thrash
            val now = System.currentTimeMillis()
            val prev = lastAnalyzeMs.get()
            if (now - prev < 120L || !analyzing.compareAndSet(false, true)) {
                imageProxy.close()
                return@setAnalyzer
            }
            lastAnalyzeMs.set(now)
            try {
                val detection = DocumentEdgeDetector.detect(imageProxy)
                // Map WHILE ImageProxy is open — CoordinateTransform needs buffer + PreviewView
                val overlayMapped: DocumentEdgeDetector.Detection? =
                    if (detection != null) {
                        val ow = edgeOverlay.width.takeIf { it > 0 } ?: previewView.width
                        val oh = edgeOverlay.height.takeIf { it > 0 } ?: previewView.height
                        val viewQuad = PreviewCoordMapper.analysisQuadToOverlay(
                            imageProxy = imageProxy,
                            previewView = previewView,
                            overlayWidth = ow,
                            overlayHeight = oh,
                            quadNorm = detection.quad
                        )
                        if (viewQuad != null) {
                            DocumentEdgeDetector.Detection(
                                quad = viewQuad,
                                score = detection.score,
                                analysisWidth = detection.analysisWidth,
                                analysisHeight = detection.analysisHeight
                            )
                        } else null
                    } else null

                runOnUiThread {
                    onDetection(overlayMapped)
                }
            } catch (e: Exception) {
                Log.w(TAG, "analyze failed", e)
            } finally {
                analyzing.set(false)
                imageProxy.close()
            }
        }

        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        provider.bindToLifecycle(this, selector, preview, imageCapture, analysis)
    }

    /** [detection] quad is already in overlay-normalized PreviewView space. */
    private fun onDetection(detection: DocumentEdgeDetector.Detection?) {
        if (!liveCapturePane.isVisible || capturing.get()) return

        val frame = edgeTracker.onFrame(detection)
        lastQuad = frame.displayQuad
        lastScore = frame.smoothedScore

        if (frame.displayQuad == null) {
            edgeOverlay.clear()
            tvLiveHint.text = "Align the page — fill most of the frame, then hold still"
            return
        }

        // Green lock outline when ready or locked — clearer “ready” for auto.
        edgeOverlay.setQuad(frame.displayQuad, frame.ready || frame.lockedForCapture)

        tvLiveHint.text = when {
            frame.lockedForCapture -> "Ready — capturing…"
            frame.holdProgress > 0 ->
                "Ready — hold still ${frame.holdProgress}/${frame.holdNeeded}"
            frame.ready -> "Ready — hold still (~1s) or tap ●"
            frame.visible -> "Page found — center it, then hold still"
            else -> "Align the page inside the frame"
        }

        // Auto only when tracker says locked. Manual shutter always OK.
        if (frame.lockedForCapture && autoArmed && !capturing.get()) {
            autoArmed = false
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
        val capture = imageCapture ?: return
        if (!capturing.compareAndSet(false, true)) return

        tvLiveHint.text = if (auto) "Auto-capturing…" else "Capturing…"
        val photoFile = File(cacheDir, "scan_${System.currentTimeMillis()}.jpg")
        val output = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        capture.takePicture(
            output,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // Do NOT use live overlay quads for warp — they are in PreviewView space,
                    // not JPEG sensor space. Always re-detect on the still.
                    stopLiveCamera()
                    lifecycleScope.launch {
                        val path = withContext(Dispatchers.IO) {
                            finalizeCapturedPage(photoFile)
                        }
                        capturing.set(false)
                        if (path == null) {
                            Toast.makeText(this@ScanActivity, "Capture failed", Toast.LENGTH_SHORT).show()
                            if (pages.isEmpty()) showEmptyState() else showReviewState()
                            return@launch
                        }
                        addOrReplacePage(path, replaceSelected = retakePending)
                        retakePending = false
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    capturing.set(false)
                    autoArmed = true
                    Log.e(TAG, "capture error", exception)
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
     * Decode capture with EXIF orientation, re-detect page on the still, perspective-warp
     * to a flat rectangle. Retries with stronger still params; bbox crop last resort.
     */
    private fun finalizeCapturedPage(photoFile: File): String? {
        val oriented = decodeCaptureOriented(photoFile) ?: return null
        return try {
            var working = oriented
            val outFile = File(cacheDir, "scan_page_${System.currentTimeMillis()}.jpg")

            val warped = deskewStill(working)
            if (warped != null) {
                if (working !== oriented && !working.isRecycled) working.recycle()
                working = warped
            } else {
                Log.w(TAG, "deskew skipped — Review may show camera perspective")
            }

            FileOutputStream(outFile).use { fos ->
                working.compress(Bitmap.CompressFormat.JPEG, 92, fos)
            }
            if (working !== oriented && !working.isRecycled) working.recycle()
            if (!oriented.isRecycled) oriented.recycle()
            if (photoFile.exists()) photoFile.delete()
            outFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "finalize failed", e)
            if (!oriented.isRecycled) oriented.recycle()
            photoFile.absolutePath.takeIf { photoFile.exists() }
        }
    }

    /**
     * Always try perspective correction on the still. Pass 1 = STILL, pass 2 = STILL_STRONG.
     * Never uses live overlay quads (wrong coordinate space).
     */
    private fun deskewStill(src: Bitmap): Bitmap? {
        val modes = listOf(
            DocumentEdgeDetector.Mode.STILL,
            DocumentEdgeDetector.Mode.STILL_STRONG
        )
        var bestQuad: Array<PointF>? = null
        var bestScore = -1f
        for (mode in modes) {
            val det = DocumentEdgeDetector.detectBitmap(src, mode) ?: continue
            Log.d(TAG, "still detect mode=$mode score=${det.score}")
            if (det.score < STILL_WARP_SCORE) continue
            val warped = PerspectiveHelper.warpDocument(src, det.quad)
            if (warped != null) {
                Log.d(TAG, "still warp ok mode=$mode score=${det.score}")
                return warped
            }
            if (det.score > bestScore) {
                bestScore = det.score
                bestQuad = det.quad
            }
        }
        // Poly warp failed but we have a quad — axis-aligned crop as last resort
        val quad = bestQuad ?: return null
        val box = PerspectiveHelper.boundingCrop(quad)
        val cropped = cropNormalized(src, box)
        if (cropped != null) Log.d(TAG, "still bbox crop fallback score=$bestScore")
        return cropped
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

    private fun addOrReplacePage(path: String, replaceSelected: Boolean) {
        if (replaceSelected && pages.isNotEmpty() && selectedIndex in pages.indices) {
            pages[selectedIndex] = ScanPage(sourcePath = path)
        } else {
            pages.add(ScanPage(sourcePath = path))
            selectedIndex = pages.lastIndex
        }
        cropMode = false
        showReviewState()
        updateCropUi()
        refreshThumbs()
        renderSelectedPreview()
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
                ScanPageRenderer.renderForPreview(page)
            }
            previewBitmap?.let { if (!it.isRecycled) it.recycle() }
            previewBitmap = bmp
            if (bmp == null) {
                Toast.makeText(this@ScanActivity, "Could not load page image", Toast.LENGTH_SHORT).show()
                return@launch
            }
            ivPreview.setImageBitmap(bmp)
            ivPreview.post {
                syncCropOverlayBounds()
                if (cropMode) syncCropOverlayFromPage()
            }
        }
    }

    private fun syncFilterChips(filter: ScanFilter) {
        chipOriginal.isChecked = filter == ScanFilter.ORIGINAL
        chipEnhance.isChecked = filter == ScanFilter.ENHANCE
        chipBw.isChecked = filter == ScanFilter.BW
    }

    private fun updateCropUi() {
        cropOverlay.isVisible = cropMode
        btnToggleCrop.text = if (cropMode) "Done crop" else "Crop"
        if (cropMode) {
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
                    syncCropOverlayBounds()
                    syncCropOverlayFromPage()
                }
            }
        }
    }

    private fun syncCropOverlayBounds() {
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
        val locOverlay = IntArray(2)
        ivPreview.getLocationInWindow(locIv)
        cropOverlay.getLocationInWindow(locOverlay)
        val ox = (locIv[0] - locOverlay[0]).toFloat()
        val oy = (locIv[1] - locOverlay[1]).toFloat()
        cropOverlay.imageBounds = RectF(ox + left, oy + top, ox + left + shownW, oy + top + shownH)
    }

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
                    val outFile = File(dir, "SmartDocAI_Scan_$stamp.pdf")
                    ImagePdfWriter.write(bitmaps, outFile)
                    bitmaps.forEach { if (!it.isRecycled) it.recycle() }

                    val uri = FileProvider.getUriForFile(
                        this@ScanActivity,
                        "${BuildConfig.APPLICATION_ID}.fileprovider",
                        outFile
                    )

                    val dao = AppDatabase.getDatabase(this@ScanActivity).scannedFileDao()
                    dao.insert(
                        ScannedFile(
                            fileName = outFile.name,
                            fileUri = uri.toString(),
                            content = "Scanned document · ${pages.size} page(s) · image PDF",
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

                AlertDialog.Builder(this@ScanActivity)
                    .setTitle("PDF saved")
                    .setMessage(
                        "${outFile.name}\n${pages.size} page(s) saved in SmartDocAI " +
                            "(app storage — not phone Downloads yet). Share now, or open later from Recents / Files."
                    )
                    .setPositiveButton("Share") { _, _ ->
                        sharePdf(uri, outFile.name)
                        finish()
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
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(contentResolver, name, uri)
        }
        startActivity(Intent.createChooser(share, "Share PDF"))
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
            holder.tv.text = "${position + 1}"
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
