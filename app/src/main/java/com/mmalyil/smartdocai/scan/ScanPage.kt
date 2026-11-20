package com.mmalyil.smartdocai.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

data class ScanPage(
    val id: String = UUID.randomUUID().toString(),
    val sourcePath: String,
    var rotationDegrees: Int = 0,
    var filter: ScanFilter = ScanFilter.ORIGINAL,
    /** Normalized crop rect in [0,1] relative to the rotated bitmap. */
    var cropLeft: Float = 0f,
    var cropTop: Float = 0f,
    var cropRight: Float = 1f,
    var cropBottom: Float = 1f
) {
    fun resetCrop() {
        cropLeft = 0f
        cropTop = 0f
        cropRight = 1f
        cropBottom = 1f
    }

    fun rotateBy(delta: Int) {
        rotationDegrees = ((rotationDegrees + delta) % 360 + 360) % 360
        // Crop rect is relative to rotated image; reset to avoid confusing orientation
        resetCrop()
    }
}

object ScanPageRenderer {

    private const val MAX_EDGE = 2048

    fun loadBaseBitmap(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        val longest = max(bounds.outWidth, bounds.outHeight)
        while (longest / sample > MAX_EDGE) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, opts)
    }

    fun renderForPreview(page: ScanPage): Bitmap? {
        val base = loadBaseBitmap(page.sourcePath) ?: return null
        return try {
            applyTransforms(base, page, forExport = false)
        } finally {
            if (!base.isRecycled) {
                // applyTransforms may return a new bitmap; recycle base if unused
            }
        }
    }

    fun renderForExport(page: ScanPage): Bitmap? {
        val base = loadBaseBitmap(page.sourcePath) ?: return null
        return applyTransforms(base, page, forExport = true)
    }

    private fun applyTransforms(base: Bitmap, page: ScanPage, forExport: Boolean): Bitmap {
        var current = base
        var owned = false

        if (page.rotationDegrees % 360 != 0) {
            val matrix = Matrix().apply { postRotate(page.rotationDegrees.toFloat()) }
            val rotated = Bitmap.createBitmap(current, 0, 0, current.width, current.height, matrix, true)
            if (owned && current !== base && !current.isRecycled) current.recycle()
            current = rotated
            owned = true
        }

        val l = (page.cropLeft.coerceIn(0f, 1f) * current.width).roundToInt().coerceIn(0, current.width - 1)
        val t = (page.cropTop.coerceIn(0f, 1f) * current.height).roundToInt().coerceIn(0, current.height - 1)
        val r = (page.cropRight.coerceIn(0f, 1f) * current.width).roundToInt().coerceIn(l + 1, current.width)
        val b = (page.cropBottom.coerceIn(0f, 1f) * current.height).roundToInt().coerceIn(t + 1, current.height)
        if (l > 0 || t > 0 || r < current.width || b < current.height) {
            val cropped = Bitmap.createBitmap(current, l, t, r - l, b - t)
            if (owned && !current.isRecycled) current.recycle()
            current = cropped
            owned = true
        }

        val filtered = ScanImageFilters.apply(current, page.filter)
        if (filtered !== current) {
            if (owned && !current.isRecycled) current.recycle()
            current = filtered
            owned = true
        }

        // If we never allocated a new bitmap, copy so caller can recycle safely without nuking cache
        if (!owned) {
            current = current.copy(Bitmap.Config.ARGB_8888, false)
        }
        return current
    }

    fun copyUriToCache(context: Context, uri: Uri, prefix: String = "scan_import"): File? {
        return try {
            val out = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            out
        } catch (_: Exception) {
            null
        }
    }
}
