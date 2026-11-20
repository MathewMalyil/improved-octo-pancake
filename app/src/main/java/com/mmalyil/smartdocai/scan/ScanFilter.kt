package com.mmalyil.smartdocai.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

enum class ScanFilter(val label: String) {
    ORIGINAL("Original"),
    ENHANCE("Auto enhance"),
    BW("B&W");
}

object ScanImageFilters {

    fun apply(source: Bitmap, filter: ScanFilter): Bitmap {
        if (filter == ScanFilter.ORIGINAL) return source
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(matrixFor(filter))
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    private fun matrixFor(filter: ScanFilter): ColorMatrix = when (filter) {
        ScanFilter.ORIGINAL -> ColorMatrix()
        ScanFilter.ENHANCE -> ColorMatrix().apply {
            setSaturation(1.15f)
            // Mild contrast + brightness boost for document scans
            val contrast = 1.25f
            val translate = (-0.5f * contrast + 0.5f) * 255f + 12f
            val scale = floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
            postConcat(ColorMatrix(scale))
        }
        ScanFilter.BW -> ColorMatrix().apply {
            setSaturation(0f)
            val contrast = 1.35f
            val translate = (-0.5f * contrast + 0.5f) * 255f
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
    }
}
