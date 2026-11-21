package com.mmalyil.smartdocai.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream

/**
 * Writes page bitmaps into a multi-page image PDF (each page is a drawn bitmap, not text).
 */
object ImagePdfWriter {

    /**
     * @param pages rendered page bitmaps in order
     * @param outFile destination .pdf file
     * @param watermark when true, light free footer (Pro saves with watermark=false)
     * @param maxPageWidthPts PDF page width in points (default ~A4 width)
     */
    fun write(
        pages: List<Bitmap>,
        outFile: File,
        watermark: Boolean = false,
        maxPageWidthPts: Int = 595,
        maxPageHeightPts: Int = 842
    ) {
        require(pages.isNotEmpty()) { "No pages to write" }
        outFile.parentFile?.mkdirs()

        val pdf = PdfDocument()
        try {
            pages.forEachIndexed { index, bitmap ->
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
