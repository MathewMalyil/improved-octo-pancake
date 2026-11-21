package com.mmalyil.smartdocai.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Writes page bitmaps into a multi-page image PDF (each page is a drawn bitmap, not text).
 */
object ImagePdfWriter {

    /**
     * PDF size / quality preset for Save.
     * [SMALLER] downscales and JPEG-recompresses pages (Pro perk in UI).
     */
    enum class Compression {
        /** Full-ish page images — default free + Pro. */
        NORMAL,
        /** Smaller file: downscale + JPEG quality (still readable for invoices / ID). */
        SMALLER;

        val maxBitmapWidth: Int
            get() = when (this) {
                NORMAL -> 1600
                SMALLER -> 1000
            }

        val jpegQuality: Int
            get() = when (this) {
                NORMAL -> 92
                SMALLER -> 68
            }

        val maxPageWidthPts: Int
            get() = when (this) {
                NORMAL -> 595
                SMALLER -> 480
            }

        val maxPageHeightPts: Int
            get() = when (this) {
                NORMAL -> 842
                SMALLER -> 680
            }
    }

    /**
     * @param pages rendered page bitmaps in order
     * @param outFile destination .pdf file
     * @param watermark when true, light free footer (Pro saves with watermark=false)
     * @param compression [Compression.NORMAL] or [Compression.SMALLER]
     */
    fun write(
        pages: List<Bitmap>,
        outFile: File,
        watermark: Boolean = false,
        compression: Compression = Compression.NORMAL,
        maxPageWidthPts: Int = compression.maxPageWidthPts,
        maxPageHeightPts: Int = compression.maxPageHeightPts
    ) {
        require(pages.isNotEmpty()) { "No pages to write" }
        outFile.parentFile?.mkdirs()

        val prepared = ArrayList<Bitmap>(pages.size)
        val owned = ArrayList<Bitmap>(pages.size) // bitmaps we created and must recycle
        try {
            pages.forEach { src ->
                val p = preparePage(src, compression)
                prepared += p
                if (p !== src) owned += p
            }

            val pdf = PdfDocument()
            try {
                prepared.forEachIndexed { index, bitmap ->
                    val (pageW, pageH) = fitPageSize(
                        bitmap.width,
                        bitmap.height,
                        maxPageWidthPts,
                        maxPageHeightPts
                    )
                    val info = PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create()
                    val page = pdf.startPage(info)
                    drawBitmapFit(page.canvas, bitmap, pageW, pageH)
                    if (watermark) {
                        drawFreeWatermark(page.canvas, pageW, pageH)
                    }
                    pdf.finishPage(page)
                }
                FileOutputStream(outFile).use { pdf.writeTo(it) }
            } finally {
                pdf.close()
            }
        } finally {
            owned.forEach { if (!it.isRecycled) it.recycle() }
        }
    }

    /**
     * Downscale and optionally JPEG-recompress a page for the chosen [compression].
     * Returns [src] unchanged when no work is needed (caller must not recycle it).
     */
    fun preparePage(src: Bitmap, compression: Compression): Bitmap {
        if (src.isRecycled || src.width <= 0 || src.height <= 0) return src

        var working: Bitmap = src
        var ownedScaled: Bitmap? = null

        val maxW = compression.maxBitmapWidth
        if (working.width > maxW) {
            val scale = maxW.toFloat() / working.width
            val w = maxW
            val h = (working.height * scale).toInt().coerceAtLeast(1)
            ownedScaled = Bitmap.createScaledBitmap(working, w, h, true)
            working = ownedScaled
        }

        // JPEG round-trip shrinks detail for SMALLER; light pass for NORMAL only if we scaled.
        val needsJpeg = compression == Compression.SMALLER || ownedScaled != null
        if (!needsJpeg) return src

        return try {
            val baos = ByteArrayOutputStream()
            working.compress(Bitmap.CompressFormat.JPEG, compression.jpegQuality, baos)
            val bytes = baos.toByteArray()
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: working
            if (ownedScaled != null && decoded !== ownedScaled && !ownedScaled.isRecycled) {
                ownedScaled.recycle()
            }
            if (decoded === src) src else decoded
        } catch (_: Exception) {
            working
        }
    }

    private fun drawFreeWatermark(canvas: Canvas, pageW: Int, pageH: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x66807040.toInt()
            textSize = 11f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "SmartDocAI Free · Unlock Pro for clean export",
            pageW / 2f,
            pageH - 14f,
            paint
        )
    }

    private fun fitPageSize(
        bmpW: Int,
        bmpH: Int,
        maxW: Int,
        maxH: Int
    ): Pair<Int, Int> {
        if (bmpW <= 0 || bmpH <= 0) return maxW to maxH
        val scale = minOf(maxW.toFloat() / bmpW, maxH.toFloat() / bmpH, 1f)
        // Prefer native aspect: scale up to fill A4-ish box while keeping aspect
        val fill = minOf(maxW.toFloat() / bmpW, maxH.toFloat() / bmpH)
        val s = if (fill > 0f) fill else scale
        val w = (bmpW * s).toInt().coerceAtLeast(1)
        val h = (bmpH * s).toInt().coerceAtLeast(1)
        return w to h
    }

    private fun drawBitmapFit(canvas: Canvas, bitmap: Bitmap, pageW: Int, pageH: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val scale = minOf(pageW.toFloat() / bitmap.width, pageH.toFloat() / bitmap.height)
        val dw = bitmap.width * scale
        val dh = bitmap.height * scale
        val left = (pageW - dw) / 2f
        val top = (pageH - dh) / 2f
        canvas.drawBitmap(
            bitmap,
            null,
            android.graphics.RectF(left, top, left + dw, top + dh),
            paint
        )
    }
}
