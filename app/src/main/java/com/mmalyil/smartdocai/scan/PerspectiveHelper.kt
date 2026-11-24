package com.mmalyil.smartdocai.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object PerspectiveHelper {

    private const val TAG = "PerspectiveHelper"

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
     * Nudge each corner of a still quad toward the strongest nearby edge so the
     * warp hugs paper instead of leaving a desk halo. Still-only; live auto untouched.
     * Search window is small (~2.2% of min side) to avoid jumping onto text.
     */
    fun refineQuadToEdges(src: Bitmap, quadNorm: Array<PointF>): Array<PointF> {
        if (quadNorm.size < 4 || src.width < 64 || src.height < 64) return quadNorm
        if (!OpenCvDocumentDetector.ensureInit()) return quadNorm
        val rgba = Mat()
        val gray = Mat()
        val edges = Mat()
        return try {
            Utils.bitmapToMat(src, rgba)
            when (rgba.channels()) {
                4 -> Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
                3 -> Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGB2GRAY)
                else -> Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_BGRA2GRAY)
            }
            Imgproc.GaussianBlur(gray, gray, Size(3.0, 3.0), 0.0)
            Imgproc.Canny(gray, edges, 40.0, 120.0)
            val w = src.width.toFloat()
            val h = src.height.toFloat()
            val radius = (min(src.width, src.height) * 0.022f).roundToInt().coerceIn(6, 28)
            var cx = 0f
            var cy = 0f
            for (p in quadNorm) {
                cx += p.x * w
                cy += p.y * h
            }
            cx /= 4f
            cy /= 4f
            Array(4) { i ->
                val px = (quadNorm[i].x * w).roundToInt()
                val py = (quadNorm[i].y * h).roundToInt()
                val snapped = snapCornerToEdge(edges, px, py, cx, cy, radius)
                PointF(
                    (snapped.first / w).coerceIn(0f, 1f),
                    (snapped.second / h).coerceIn(0f, 1f)
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "refineQuadToEdges failed", e)
            quadNorm
        } finally {
            rgba.release()
            gray.release()
            edges.release()
        }
    }

    /**
     * Prefer the strongest edge response in a small window, biased slightly toward
     * the page centroid so we shrink desk margin rather than expand into print.
     */
    private fun snapCornerToEdge(
        edges: Mat,
        x0: Int,
        y0: Int,
        cx: Float,
        cy: Float,
        radius: Int
    ): Pair<Float, Float> {
        val cols = edges.cols()
        val rows = edges.rows()
        var bestX = x0
        var bestY = y0
        var bestScore = -1.0
        val dxC = cx - x0
        val dyC = cy - y0
        val len = hypot(dxC.toDouble(), dyC.toDouble()).toFloat().coerceAtLeast(1f)
        val inwardX = dxC / len
        val inwardY = dyC / len
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val x = x0 + dx
                val y = y0 + dy
                if (x < 1 || y < 1 || x >= cols - 1 || y >= rows - 1) continue
                val v = edges.get(y, x)?.get(0) ?: 0.0
                if (v < 1.0) continue
                // Prefer edges slightly inward (toward page) over outward desk
                val toward = (dx * inwardX + dy * inwardY).coerceIn(-radius.toFloat(), radius.toFloat())
                val score = v + toward * 2.5
                if (score > bestScore) {
                    bestScore = score
                    bestX = x
                    bestY = y
                }
            }
        }
        return bestX.toFloat() to bestY.toFloat()
    }

    /**
     * After warp, shave desk strips that still hug the border.
     * Uses page-body contrast (not darkness alone) so colored desks trim too,
     * while capping each side so dark headers/footers on the page are not eaten.
     */
    fun trimDarkMargins(src: Bitmap, maxFrac: Float = 0.12f): Bitmap {
        val w = src.width
        val h = src.height
        if (w < 64 || h < 64) return src
        val maxX = (w * maxFrac).toInt().coerceAtLeast(1)
        val maxY = (h * maxFrac).toInt().coerceAtLeast(1)
        val center = meanRect(src, w / 4, h / 4, 3 * w / 4, 3 * h / 4)
        // Desk usually differs from paper; require a clear gap vs page body.
        val darkThresh = (center - 24f).coerceAtLeast(65f)
        val lightDeskCeil = (center + 18f).coerceAtMost(245f)

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

        fun isDeskStrip(mean: Float): Boolean {
            // Classic dark desk, or wash/colored strip far from page body luma.
            if (mean < darkThresh) return true
            val delta = abs(mean - center)
            return delta > 22f && mean < lightDeskCeil
        }

        var left = 0
        while (left < maxX && isDeskStrip(colMean(left))) left++
        var right = w - 1
        while (right > w - 1 - maxX && isDeskStrip(colMean(right))) right--
        var top = 0
        while (top < maxY && isDeskStrip(rowMean(top))) top++
        var bottom = h - 1
        while (bottom > h - 1 - maxY && isDeskStrip(rowMean(bottom))) bottom--

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
     * Mild document lighting polish after warp: CLAHE on L + soft shadow lift.
     * Keeps paper readable under uneven lamp light without the harsh look of B&W.
     * Falls back to [src] if OpenCV is unavailable.
     */
    fun normalizeDocumentLighting(src: Bitmap): Bitmap {
        if (src.width < 32 || src.height < 32) return src
        if (!OpenCvDocumentDetector.ensureInit()) return src
        val rgba = Mat()
        val lab = Mat()
        val enhanced = Mat()
        val channels = ArrayList<Mat>(3)
        return try {
            Utils.bitmapToMat(src, rgba)
            if (rgba.channels() == 3) {
                Imgproc.cvtColor(rgba, rgba, Imgproc.COLOR_RGB2RGBA)
            }
            Imgproc.cvtColor(rgba, lab, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(lab, lab, Imgproc.COLOR_RGB2Lab)
            Core.split(lab, channels)
            if (channels.size < 3) return src
            val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
            clahe.apply(channels[0], channels[0])
            // Soft lift of darkest 15% of L without blowing highlights
            val l = channels[0]
            val mean = Core.mean(l).`val`[0]
            if (mean in 40.0..200.0) {
                val lifted = Mat()
                l.convertTo(lifted, CvType.CV_32F)
                val data = FloatArray(lifted.rows() * lifted.cols())
                lifted.get(0, 0, data)
                for (i in data.indices) {
                    val v = data[i]
                    if (v < 110f) {
                        data[i] = v + (110f - v) * 0.18f
                    }
                }
                lifted.put(0, 0, data)
                lifted.convertTo(l, CvType.CV_8U)
                lifted.release()
            }
            Core.merge(channels, lab)
            Imgproc.cvtColor(lab, enhanced, Imgproc.COLOR_Lab2RGB)
            Imgproc.cvtColor(enhanced, enhanced, Imgproc.COLOR_RGB2RGBA)
            val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(enhanced, out)
            out
        } catch (e: Exception) {
            Log.w(TAG, "normalizeDocumentLighting failed", e)
            src
        } finally {
            rgba.release()
            lab.release()
            enhanced.release()
            channels.forEach { it.release() }
        }
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
