package com.mmalyil.smartdocai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.mmalyil.smartdocai.model.AppDatabase
import com.mmalyil.smartdocai.model.ScannedFile
import com.mmalyil.smartdocai.scan.CropOverlayView
import com.mmalyil.smartdocai.scan.ImagePdfWriter
import com.mmalyil.smartdocai.scan.ScanFilter
import com.mmalyil.smartdocai.scan.ScanPage
import com.mmalyil.smartdocai.scan.ScanPageRenderer
import com.mmalyil.smartdocai.util.DocumentEntitlement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Capture → review (crop / rotate / filters) → multi-page image PDF.
 * System camera + photo picker; output is bitmap pages drawn into [android.graphics.pdf.PdfDocument].
 */
class ScanActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_START_MODE = "start_mode"
        const val MODE_CAMERA = "camera"
        const val MODE_PHOTOS = "photos"

        const val EXTRA_PDF_URI = "pdfUri"
        const val EXTRA_PDF_PATH = "pdfPath"
        const val EXTRA_PAGE_COUNT = "scanPageCount"
        const val EXTRA_SCANNED_TEXT = "scannedText"
    }

    private lateinit var tvTitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var ivPreview: ImageView
    private lateinit var cropOverlay: CropOverlayView
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

    private val pages = mutableListOf<ScanPage>()
    private var selectedIndex = 0
    private var pendingCaptureFile: File? = null
    private var cropMode = false
    private var previewBitmap: Bitmap? = null
    private var saving = false

    private lateinit var thumbAdapter: ThumbAdapter

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val file = pendingCaptureFile
            pendingCaptureFile = null
            if (success && file != null && file.exists() && file.length() > 0L) {
                addOrReplacePage(file.absolutePath, replaceSelected = retakePending)
            } else {
                Toast.makeText(this, "No image captured", Toast.LENGTH_SHORT).show()
                if (pages.isEmpty()) showEmptyState()
            }
            retakePending = false
        }

    private var retakePending = false

    private val pickImagesLauncher =
        registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris ->
            if (uris.isNullOrEmpty()) {
                if (pages.isEmpty()) showEmptyState()
                return@registerForActivityResult
            }
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
            if (granted) launchCamera(replaceSelected = retakePending)
            else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
                retakePending = false
                if (pages.isEmpty()) showEmptyState()
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
                // Default: jump straight into camera when opened as Scan
                if (pages.isEmpty()) {
                    showEmptyState()
                    checkCameraPermissionAndStart(replaceSelected = false)
                }
            }
        }
    }

    private fun bindViews() {
        tvTitle = findViewById(R.id.tvScanTitle)
        tvStatus = findViewById(R.id.tvStatus)
        ivPreview = findViewById(R.id.ivPreview)
        cropOverlay = findViewById(R.id.cropOverlay)
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

        reviewViews = listOf(
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
        captureButton.setOnClickListener {
            checkCameraPermissionAndStart(replaceSelected = false)
        }
        btnFromPhotos.setOnClickListener { openPhotoPicker() }

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
        emptyPane.isVisible = true
        reviewViews.forEach { it.isVisible = false }
        tvTitle.text = "Scan document"
        tvStatus.text = "Capture pages, then review and Save PDF"
    }

    private fun showReviewState() {
        emptyPane.isVisible = false
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
            launchCamera(replaceSelected)
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera(replaceSelected: Boolean) {
        retakePending = replaceSelected
        try {
            val photoFile = File.createTempFile(
                "scan_${System.currentTimeMillis()}",
                ".jpg",
                cacheDir
            )
            pendingCaptureFile = photoFile
            val uri = FileProvider.getUriForFile(
                this,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                photoFile
            )
            tvStatus.text = if (replaceSelected) "Retake page…" else "Scanning page ${pages.size + 1}…"
            takePictureLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
            retakePending = false
        }
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
        // While cropping, show uncropped rotated base so handles match; filter still applied after
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
        // Crop overlay is sibling covering previewContainer; map into overlay coords
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
                    // Tools OCR path optional: leave blank — job is image PDF
                    putExtra(EXTRA_SCANNED_TEXT, "")
                }
                setResult(Activity.RESULT_OK, returnIntent)

                AlertDialog.Builder(this@ScanActivity)
                    .setTitle("PDF saved")
                    .setMessage("${outFile.name}\n${pages.size} page(s) saved to Files. Share or open it?")
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
            // Lightweight thumb decode
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
