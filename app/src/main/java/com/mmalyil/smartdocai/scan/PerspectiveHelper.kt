package com.mmalyil.smartdocai.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

object PerspectiveHelper {

    /**
     * Warp [src] so [quadNorm] (normalized TL,TR,BR,BL) becomes an upright rectangle.
     * Returns null if the quad is unusable or nearly degenerate.
     */
    fun warpDocument(src: Bitmap, quadNorm: Array<PointF>): Bitmap? {
        if (quadNorm.size < 4) return null
        val tl = PointF(quadNorm[0].x * src.width, quadNorm[0].y * src.height)
        val tr = PointF(quadNorm[1].x * src.width, quadNorm[1].y * src.height)
        val br = PointF(quadNorm[2].x * src.width, quadNorm[2].y * src.height)
        val bl = PointF(quadNorm[3].x * src.width, quadNorm[3].y * src.height)

        val widthA = hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble())
        val widthB = hypot((br.x - bl.x).toDouble(), (br.y - bl.y).toDouble())
        val heightA = hypot((bl.x - tl.x).toDouble(), (bl.y - tl.y).toDouble())
        val heightB = hypot((br.x - tr.x).toDouble(), (br.y - tr.y).toDouble())
        if (widthA < 32.0 || widthB < 32.0 || heightA < 32.0 || heightB < 32.0) return null

        // Reject near-collinear / crossed quads that produce garbage warps
        val area = abs(
            (tr.x - tl.x) * (bl.y - tl.y) - (bl.x - tl.x) * (tr.y - tl.y)
        ) * 0.5f + abs(
            (br.x - tr.x) * (bl.y - br.y) - (bl.x - br.x) * (tr.y - br.y)
        ) * 0.5f
        if (area < src.width * src.height * 0.04f) return null

        val outW = max(widthA, widthB).roundToInt().coerceIn(64, 4096)
        val outH = max(heightA, heightB).roundToInt().coerceIn(64, 4096)

        val srcPts = floatArrayOf(tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y)
        val dstPts = floatArrayOf(
            0f, 0f,
            outW.toFloat(), 0f,
            outW.toFloat(), outH.toFloat(),
            0f, outH.toFloat()
        )
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)) return null

        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(src, matrix, paint)
        return out
    }

    fun boundingCrop(quadNorm: Array<PointF>): FloatArray {
        var minX = 1f
        var minY = 1f
        var maxX = 0f
        var maxY = 0f
        for (p in quadNorm) {
            minX = minOf(minX, p.x)
            minY = minOf(minY, p.y)
            maxX = maxOf(maxX, p.x)
            maxY = maxOf(maxY, p.y)
        }
        val pad = 0.008f
        return floatArrayOf(
            (minX - pad).coerceIn(0f, 0.9f),
            (minY - pad).coerceIn(0f, 0.9f),
            (maxX + pad).coerceIn(0.1f, 1f),
            (maxY + pad).coerceIn(0.1f, 1f)
        )
    }
}
