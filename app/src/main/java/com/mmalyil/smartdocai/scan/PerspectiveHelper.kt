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
     * Shrink a normalized TL,TR,BR,BL quad toward its centroid.
     * Used on still deskew so Review/PDF hugs paper instead of desk margin.
     */
    fun insetQuad(quadNorm: Array<PointF>, fraction: Float): Array<PointF> {
        if (quadNorm.size < 4 || fraction <= 0f) return quadNorm
        val f = fraction.coerceIn(0f, 0.2f)
        var cx = 0f
        var cy = 0f
        for (p in quadNorm) {
            cx += p.x
            cy += p.y
        }
        cx /= quadNorm.size
        cy /= quadNorm.size
        return Array(quadNorm.size) { i ->
            val p = quadNorm[i]
            PointF(
                (p.x + (cx - p.x) * f).coerceIn(0f, 1f),
                (p.y + (cy - p.y) * f).coerceIn(0f, 1f)
            )
        }
    }

    /**
     * After warp, shave dark desk strips that still hug the border.
     * Caps each side so a dark header/footer on the page itself is not eaten.
     */
    fun trimDarkMargins(src: Bitmap, maxFrac: Float = 0.10f): Bitmap {
        val w = src.width
        val h = src.height
        if (w < 64 || h < 64) return src
        val maxX = (w * maxFrac).toInt().coerceAtLeast(1)
        val maxY = (h * maxFrac).toInt().coerceAtLeast(1)
        val center = meanRect(src, w / 4, h / 4, 3 * w / 4, 3 * h / 4)
        // Desk is usually darker than paper; require a clear gap vs page body.
        val thresh = (center - 28f).coerceAtLeast(70f)

        fun colMean(x: Int): Float {
            var s = 0.0
            var n = 0
            var y = 0
            while (y < h) {
                val c = src.getPixel(x, y)
                s += 0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)
                n++
                y += 4
            }
            return (s / max(n, 1)).toFloat()
        }

        fun rowMean(y: Int): Float {
            var s = 0.0
            var n = 0
            var x = 0
            while (x < w) {
                val c = src.getPixel(x, y)
                s += 0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)
                n++
                x += 4
            }
            return (s / max(n, 1)).toFloat()
        }

        var left = 0
        while (left < maxX && colMean(left) < thresh) left++
        var right = w - 1
        while (right > w - 1 - maxX && colMean(right) < thresh) right--
        var top = 0
        while (top < maxY && rowMean(top) < thresh) top++
        var bottom = h - 1
        while (bottom > h - 1 - maxY && rowMean(bottom) < thresh) bottom--

        val cw = right - left + 1
        val ch = bottom - top + 1
        if (cw < w * 0.7f || ch < h * 0.7f) return src
        if (left == 0 && top == 0 && right == w - 1 && bottom == h - 1) return src
        return Bitmap.createBitmap(src, left, top, cw, ch)
    }

    private fun meanRect(src: Bitmap, l: Int, t: Int, r: Int, b: Int): Float {
        var s = 0.0
        var n = 0
        var y = t.coerceAtLeast(0)
        val yMax = b.coerceAtMost(src.height)
        val x0 = l.coerceAtLeast(0)
        val x1 = r.coerceAtMost(src.width)
        while (y < yMax) {
            var x = x0
            while (x < x1) {
                val c = src.getPixel(x, y)
                s += 0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)
                n++
                x += 8
            }
            y += 8
        }
        return (s / max(n, 1)).toFloat()
    }

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
        // No outward pad — bbox fallback should hug the detected page.
        return floatArrayOf(
            minX.coerceIn(0f, 0.9f),
            minY.coerceIn(0f, 0.9f),
            maxX.coerceIn(0.1f, 1f),
            maxY.coerceIn(0.1f, 1f)
        )
    }
}
