package com.mmalyil.smartdocai

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.mmalyil.smartdocai.model.AppDatabase
import com.mmalyil.smartdocai.model.ScannedFile
import com.mmalyil.smartdocai.scan.ImagePdfWriter
import com.mmalyil.smartdocai.util.DocumentEntitlement
import com.mmalyil.smartdocai.util.ScanStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Pick 2+ PDFs (device SAF and/or app Files scans), review page thumbnails,
 * reorder / delete pages, save one merged image PDF into scans history.
 * Respects Document Pro watermark + CLEAN_SHARE gates from 3.15.0.
 */
class CombinePdfActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvPageCount: TextView
    private lateinit var tvHint: TextView
    private lateinit var tvLoading: TextView
    private lateinit var rvPages: RecyclerView
    private lateinit var emptyState: View
    private lateinit var loadingOverlay: View
    private lateinit var btnPickDevice: MaterialButton
    private lateinit var btnPickAppFiles: MaterialButton
    private lateinit var btnClearPages: MaterialButton
    private lateinit var btnSaveCombined: MaterialButton

    private val pages = mutableListOf<CombinePage>()
    private lateinit var adapter: CombinePageAdapter
    private var saving = false
    private var loading = false
    /** Count of source PDFs successfully opened this session (for 2+ pick guidance). */
    private var sourcePdfCount = 0

    private val pickDeviceLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNullOrEmpty()) return@registerForActivityResult
            if (pages.isEmpty() && uris.size < 2) {
                Toast.makeText(
                    this,
                    "Select at least 2 PDFs to combine",
                    Toast.LENGTH_LONG
                ).show()
                return@registerForActivityResult
            }
            uris.forEach { uri ->
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // Some providers do not support persistable grants; openFd still works.
                }
            }
            appendPdfsFromUris(uris)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_combine_pdf)

        tvStatus = findViewById(R.id.tvCombineStatus)
        tvPageCount = findViewById(R.id.tvPageCount)
        tvHint = findViewById(R.id.tvHint)
        tvLoading = findViewById(R.id.tvLoading)
        rvPages = findViewById(R.id.rvPages)
        emptyState = findViewById(R.id.emptyState)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        btnPickDevice = findViewById(R.id.btnPickDevice)
        btnPickAppFiles = findViewById(R.id.btnPickAppFiles)
        btnClearPages = findViewById(R.id.btnClearPages)
        btnSaveCombined = findViewById(R.id.btnSaveCombined)

        adapter = CombinePageAdapter(
            pages = pages,
            onRemove = { position -> removePageAt(position) }
        )
        rvPages.layoutManager = GridLayoutManager(this, 2)
        rvPages.adapter = adapter

        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN or
                ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                Collections.swap(pages, from, to)
                adapter.notifyItemMoved(from, to)
                refreshPageIndexes()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)
                adapter.notifyDataSetChanged()
                updateChrome()
            }
        }).attachToRecyclerView(rvPages)

        btnPickDevice.setOnClickListener {
            if (loading || saving) return@setOnClickListener
            pickDeviceLauncher.launch(arrayOf("application/pdf"))
        }
        btnPickAppFiles.setOnClickListener {
            if (loading || saving) return@setOnClickListener
            showAppFilesPicker()
        }
        btnClearPages.setOnClickListener {
            if (loading || saving) return@setOnClickListener
            clearAllPages()
        }
        btnSaveCombined.setOnClickListener {
            if (loading || saving) return@setOnClickListener
            saveCombinedPdf()
        }

        updateChrome()
    }

    private fun showAppFilesPicker() {
        lifecycleScope.launch {
            val all = withContext(Dispatchers.IO) {
                AppDatabase.getDatabase(this@CombinePdfActivity)
                    .scannedFileDao()
                    .getAllFilesOnce()
            }
            val pdfs = all.filter { ScanStorage.isPdfEntry(it) }
            if (pdfs.isEmpty()) {
                Toast.makeText(
                    this@CombinePdfActivity,
                    "No PDFs in Files yet. Scan first, or pick from device.",
                    Toast.LENGTH_LONG
                ).show()
                return@launch
            }

            val labels = pdfs.map { it.fileName }.toTypedArray()
            val checked = BooleanArray(pdfs.size)
            AlertDialog.Builder(this@CombinePdfActivity)
                .setTitle("Select PDFs from Files")
                .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                    checked[which] = isChecked
                }
                .setPositiveButton("Add") { _, _ ->
                    val selected = pdfs.filterIndexed { index, _ -> checked[index] }
                    if (selected.isEmpty()) {
                        Toast.makeText(
                            this@CombinePdfActivity,
                            "Select at least one PDF",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@setPositiveButton
                    }
                    if (pages.isEmpty() && selected.size < 2) {
                        Toast.makeText(
                            this@CombinePdfActivity,
                            "Select at least 2 PDFs to combine",
                            Toast.LENGTH_LONG
                        ).show()
                        return@setPositiveButton
                    }
                    appendPdfsFromScannedFiles(selected)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun appendPdfsFromScannedFiles(files: List<ScannedFile>) {
        val uris = files.mapNotNull { ScanStorage.contentUriForView(this, it) }
        if (uris.isEmpty()) {
            Toast.makeText(this, "Could not open selected Files PDFs", Toast.LENGTH_LONG).show()
            return
        }
        appendPdfsFromUris(uris)
    }

    private fun appendPdfsFromUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        loading = true
        setLoading(true, "Loading pages…")
        lifecycleScope.launch {
            try {
                val rendered = withContext(Dispatchers.IO) {
                    val out = mutableListOf<CombinePage>()
                    uris.forEach { uri ->
                        out += renderPdfPages(uri)
                    }
                    out
                }
                if (rendered.isEmpty()) {
                    Toast.makeText(
                        this@CombinePdfActivity,
                        "No pages found in selected PDFs",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    sourcePdfCount += uris.size
                    val start = pages.size
                    pages.addAll(rendered)
                    adapter.notifyItemRangeInserted(start, rendered.size)
                    refreshPageIndexes()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@CombinePdfActivity,
                    "Could not read PDF: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                loading = false
                setLoading(false)
                updateChrome()
            }
        }
    }

    private fun renderPdfPages(uri: Uri): List<CombinePage> {
        // PdfRenderer takes ownership of the PFD and closes it when closed.
        val pfd = contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("Cannot open $uri")
        PdfRenderer(pfd).use { renderer ->
            val result = ArrayList<CombinePage>(renderer.pageCount)
            for (i in 0 until renderer.pageCount) {
                renderer.openPage(i).use { page ->
                    val (w, h) = scaledPageSize(page.width, page.height, MAX_RENDER_WIDTH)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    result += CombinePage(
                        id = UUID.randomUUID().toString(),
                        bitmap = bmp
                    )
                }
            }
            return result
        }
    }

    private fun scaledPageSize(pageW: Int, pageH: Int, maxW: Int): Pair<Int, Int> {
        if (pageW <= 0 || pageH <= 0) return maxW to (maxW * 4 / 3)
        val scale = minOf(1f, maxW.toFloat() / pageW)
        val w = (pageW * scale).toInt().coerceAtLeast(1)
        val h = (pageH * scale).toInt().coerceAtLeast(1)
        return w to h
    }

    private fun removePageAt(position: Int) {
        if (position !in pages.indices) return
        val removed = pages.removeAt(position)
        if (!removed.bitmap.isRecycled) removed.bitmap.recycle()
        adapter.notifyItemRemoved(position)
        refreshPageIndexes()
        updateChrome()
    }

    private fun clearAllPages() {
        pages.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        pages.clear()
        sourcePdfCount = 0
        adapter.notifyDataSetChanged()
        updateChrome()
    }

    private fun refreshPageIndexes() {
        adapter.notifyItemRangeChanged(0, pages.size)
    }

    private fun updateChrome() {
        val count = pages.size
        tvPageCount.text = when (count) {
            0 -> "0 pages"
            1 -> "1 page"
            else -> "$count pages"
        }
        val hasPages = count > 0
        emptyState.isVisible = !hasPages
        rvPages.isVisible = hasPages
        tvHint.isVisible = hasPages
        btnClearPages.isEnabled = hasPages && !loading && !saving
        // Need at least 1 page to save, but product requires 2+ source PDFs —
        // after reorder/delete, allow save of remaining pages if ≥1; gate entry by
        // requiring users picked content (status copy still says 2+ PDFs).
        btnSaveCombined.isEnabled = count >= 1 && !loading && !saving
        tvStatus.text = when {
            count == 0 -> "Pick 2 or more PDFs, then reorder pages and save."
            count == 1 -> "1 page ready — add more PDFs or save."
            else -> "Drag to reorder, remove pages you don’t need, then save."
        }
    }

    private fun setLoading(show: Boolean, message: String? = null) {
        loadingOverlay.isVisible = show
        if (message != null) tvLoading.text = message
        btnPickDevice.isEnabled = !show
        btnPickAppFiles.isEnabled = !show
        btnClearPages.isEnabled = !show && pages.isNotEmpty()
        btnSaveCombined.isEnabled = !show && pages.isNotEmpty()
    }

    private fun saveCombinedPdf() {
        if (saving) return
        if (pages.isEmpty()) {
            Toast.makeText(this, "Add at least one page first", Toast.LENGTH_SHORT).show()
            return
        }

        // Same multi-page soft cap as scan → PDF (2/day free).
        val multiPage = pages.size > 1
        if (multiPage && !DocumentEntitlement.canUse(this, DocumentEntitlement.Feature.SCAN_TO_PDF)) {
            DocumentEntitlement.openPaywall(
                this,
                DocumentEntitlement.freeLimitMessage(this, DocumentEntitlement.Feature.SCAN_TO_PDF)
            )
            return
        }

        saving = true
        setLoading(true, "Building PDF…")

        lifecycleScope.launch {
            try {
                val pageCount = pages.size
                val bitmaps = pages.map { it.bitmap }
                val result = withContext(Dispatchers.IO) {
                    val dir = ScanStorage.scansDir(this@CombinePdfActivity)
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val outFile = File(dir, "SmartDocAI_Combine_$stamp.pdf")
                    ImagePdfWriter.write(
                        bitmaps,
                        outFile,
                        watermark = DocumentEntitlement.shouldWatermarkPdf(this@CombinePdfActivity)
                    )

                    val uri = FileProvider.getUriForFile(
                        this@CombinePdfActivity,
                        "${BuildConfig.APPLICATION_ID}.fileprovider",
                        outFile
                    )

                    AppDatabase.getDatabase(this@CombinePdfActivity).scannedFileDao().insert(
                        ScannedFile(
                            fileName = outFile.name,
                            fileUri = uri.toString(),
                            content = "Combined PDF · $pageCount page(s) · image PDF",
                            aiResponse = null
                        )
                    )

                    if (multiPage) {
                        DocumentEntitlement.recordUse(
                            this@CombinePdfActivity,
                            DocumentEntitlement.Feature.SCAN_TO_PDF
                        )
                    }

                    Pair(outFile, uri)
                }

                val (outFile, uri) = result
                saving = false
                setLoading(false)
                updateChrome()

                val freeMark = DocumentEntitlement.shouldWatermarkPdf(this@CombinePdfActivity)
                AlertDialog.Builder(this@CombinePdfActivity)
                    .setTitle("PDF saved")
                    .setMessage(
                        "${outFile.name}\n$pageCount page(s) saved in SmartDocAI " +
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
                saving = false
                setLoading(false)
                updateChrome()
                Toast.makeText(
                    this@CombinePdfActivity,
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
        pages.forEach { if (!it.bitmap.isRecycled) it.bitmap.recycle() }
        pages.clear()
        super.onDestroy()
    }

    data class CombinePage(
        val id: String,
        val bitmap: Bitmap
    )

    private class CombinePageAdapter(
        private val pages: List<CombinePage>,
        private val onRemove: (Int) -> Unit
    ) : RecyclerView.Adapter<CombinePageAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val iv: ImageView = view.findViewById(R.id.ivPage)
            val tv: TextView = view.findViewById(R.id.tvPageIndex)
            val btnRemove: TextView = view.findViewById(R.id.btnRemovePage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_combine_page, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val page = pages[position]
            holder.iv.setImageBitmap(page.bitmap)
            holder.tv.text = (position + 1).toString()
            holder.btnRemove.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onRemove(pos)
            }
        }

        override fun getItemCount(): Int = pages.size
    }

    companion object {
        private const val MAX_RENDER_WIDTH = 1080
    }
}
