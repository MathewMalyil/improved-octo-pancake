package com.mmalyil.smartdocai.scan

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.camera.core.ImageProxy
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Lightweight document-quad detector for CameraX [ImageAnalysis].
 * Pure Kotlin (no OpenCV natives) — Canny-lite + contour → largest 4-gon.
 */
object DocumentEdgeDetector {

    data class Detection(
        /** TL, TR, BR, BL in normalized preview/analysis coords [0,1]. */
        val quad: Array<PointF>,
        val score: Float,
        val analysisWidth: Int,
        val analysisHeight: Int
    )

    private const val ANALYZE_WIDTH = 480
    /** Prefer a real page filling a good portion of the frame. */
    private const val MIN_AREA_RATIO = 0.22f
    private const val MAX_AREA_RATIO = 0.88f
    private const val MIN_RECT = 0.62f
    private const val MIN_SCORE = 0.50f

    fun detect(imageProxy: ImageProxy): Detection? {
        val grayFull = yPlaneToGray(imageProxy) ?: return null
        val srcW = imageProxy.width
        val srcH = imageProxy.height
        val scale = ANALYZE_WIDTH.toFloat() / max(srcW, 1)
        val w = max(32, (srcW * min(scale, 1f)).roundToInt())
        val h = max(32, (srcH * min(scale, 1f)).roundToInt())
        val gray = downsampleGray(grayFull, srcW, srcH, w, h)
        blur3x3(gray, w, h)
        blur3x3(gray, w, h) // extra blur → fewer noise edges
        val edges = cannyLite(gray, w, h)
        val quad = findBestQuad(edges, gray, w, h) ?: return null
        return scoreNormalized(quad, gray, w, h)
    }

    fun detectBitmap(source: Bitmap): Detection? {
        val scale = ANALYZE_WIDTH.toFloat() / max(source.width, 1)
        val w = max(32, (source.width * min(scale, 1f)).roundToInt())
        val h = max(32, (source.height * min(scale, 1f)).roundToInt())
        val small = Bitmap.createScaledBitmap(source, w, h, true)
        try {
            val gray = IntArray(w * h)
            val pixels = IntArray(w * h)
            small.getPixels(pixels, 0, w, 0, 0, w, h)
            for (i in pixels.indices) {
                val c = pixels[i]
                val r = (c shr 16) and 0xff
                val g = (c shr 8) and 0xff
                val b = c and 0xff
                gray[i] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            }
            blur3x3(gray, w, h)
            blur3x3(gray, w, h)
            val edges = cannyLite(gray, w, h)
            val quad = findBestQuad(edges, gray, w, h) ?: return null
            return scoreNormalized(quad, gray, w, h)
        } finally {
            if (small !== source && !small.isRecycled) small.recycle()
        }
    }

    private fun scoreNormalized(quad: Array<PointF>, gray: IntArray, w: Int, h: Int): Detection? {
        val area = polygonArea(quad)
        val frameArea = (w * h).toFloat()
        val areaRatio = area / frameArea
        if (areaRatio < MIN_AREA_RATIO || areaRatio > MAX_AREA_RATIO) return null
        val rectScore = rectangularity(quad)
        if (rectScore < MIN_RECT) return null
        val centerScore = centrality(quad, w, h)
        val contrastScore = interiorContrast(quad, gray, w, h)
        // Prefer large, centered, rectangular pages with page/bg separation
        val score = (
            areaRatio * 0.35f +
                rectScore * 0.30f +
                centerScore * 0.20f +
                contrastScore * 0.15f
            ).coerceIn(0f, 1f)
        if (score < MIN_SCORE) return null
        val norm = Array(4) { i ->
            PointF(
                (quad[i].x / w).coerceIn(0f, 1f),
                (quad[i].y / h).coerceIn(0f, 1f)
            )
        }
        orderQuadTlTrBrBl(norm)
        return Detection(norm, score, w, h)
    }

    fun iou(a: Array<PointF>, b: Array<PointF>): Float {
        val ab = boundingRect(a)
        val bb = boundingRect(b)
        val interL = max(ab[0], bb[0])
        val interT = max(ab[1], bb[1])
        val interR = min(ab[2], bb[2])
        val interB = min(ab[3], bb[3])
        val inter = max(0f, interR - interL) * max(0f, interB - interT)
        val areaA = max(0f, ab[2] - ab[0]) * max(0f, ab[3] - ab[1])
        val areaB = max(0f, bb[2] - bb[0]) * max(0f, bb[3] - bb[1])
        val union = areaA + areaB - inter
        return if (union <= 1e-6f) 0f else inter / union
    }

    private fun boundingRect(q: Array<PointF>): FloatArray {
        var minX = 1f
        var minY = 1f
        var maxX = 0f
        var maxY = 0f
        for (p in q) {
            minX = min(minX, p.x)
            minY = min(minY, p.y)
            maxX = max(maxX, p.x)
            maxY = max(maxY, p.y)
        }
        return floatArrayOf(minX, minY, maxX, maxY)
    }

    /** Luma-only buffer from CameraX YUV_420_888 — enough for edges, no NV21 quirks. */
    private fun yPlaneToGray(image: ImageProxy): IntArray? {
        return try {
            val plane = image.planes[0]
            val buffer = plane.buffer.duplicate()
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val width = image.width
            val height = image.height
            val out = IntArray(width * height)
            val row = ByteArray(rowStride)
            var i = 0
            for (y in 0 until height) {
                buffer.position(y * rowStride)
                buffer.get(row, 0, min(rowStride, buffer.remaining()))
                var x = 0
                while (x < width) {
                    out[i++] = row[x * pixelStride].toInt() and 0xff
                    x++
                }
            }
            out
        } catch (_: Exception) {
            null
        }
    }

    private fun downsampleGray(
        src: IntArray,
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int
    ): IntArray {
        val dst = IntArray(dstW * dstH)
        for (y in 0 until dstH) {
            val sy = (y * srcH) / dstH
            for (x in 0 until dstW) {
                val sx = (x * srcW) / dstW
                dst[y * dstW + x] = src[sy * srcW + sx]
            }
        }
        return dst
    }

    private fun blur3x3(gray: IntArray, w: Int, h: Int) {
        val copy = gray.copyOf()
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var sum = 0
                for (dy in -1..1) for (dx in -1..1) {
                    sum += copy[(y + dy) * w + (x + dx)]
                }
                gray[y * w + x] = sum / 9
            }
        }
    }

    private fun cannyLite(gray: IntArray, w: Int, h: Int): BooleanArray {
        val mag = FloatArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val gx =
                    (-gray[(y - 1) * w + (x - 1)] + gray[(y - 1) * w + (x + 1)] +
                        -2 * gray[y * w + (x - 1)] + 2 * gray[y * w + (x + 1)] +
                        -gray[(y + 1) * w + (x - 1)] + gray[(y + 1) * w + (x + 1)]).toFloat()
                val gy =
                    (-gray[(y - 1) * w + (x - 1)] - 2 * gray[(y - 1) * w + x] - gray[(y - 1) * w + (x + 1)] +
                        gray[(y + 1) * w + (x - 1)] + 2 * gray[(y + 1) * w + x] + gray[(y + 1) * w + (x + 1)]).toFloat()
                mag[y * w + x] = sqrt(gx * gx + gy * gy)
            }
        }
        var sum = 0.0
        var count = 0
        for (v in mag) {
            if (v > 0f) {
                sum += v
                count++
            }
        }
        val mean = if (count == 0) 0f else (sum / count).toFloat()
        // Higher thresholds → fewer false page edges on busy backgrounds
        val hi = max(55f, mean * 2.0f)
        val lo = hi * 0.50f
        val edges = BooleanArray(w * h)
        for (i in mag.indices) {
            edges[i] = mag[i] >= hi
        }
        // weak edges connected to strong
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                if (!edges[i] && mag[i] >= lo) {
                    var neighbor = false
                    for (dy in -1..1) for (dx in -1..1) {
                        if (edges[(y + dy) * w + (x + dx)]) neighbor = true
                    }
                    if (neighbor) edges[i] = true
                }
            }
        }
        return edges
    }

    private fun findBestQuad(edges: BooleanArray, gray: IntArray, w: Int, h: Int): Array<PointF>? {
        val visited = BooleanArray(w * h)
        var best: Array<PointF>? = null
        var bestScore = 0f
        val stackX = IntArray(w * h)
        val stackY = IntArray(w * h)

        for (y in 1 until h - 1 step 2) {
            for (x in 1 until w - 1 step 2) {
                val start = y * w + x
                if (!edges[start] || visited[start]) continue
                var sp = 0
                stackX[sp] = x
                stackY[sp] = y
                sp++
                visited[start] = true
                val pts = ArrayList<PointF>(256)
                var minX = x
                var maxX = x
                var minY = y
                var maxY = y
                while (sp > 0) {
                    sp--
                    val cx = stackX[sp]
                    val cy = stackY[sp]
                    pts.add(PointF(cx.toFloat(), cy.toFloat()))
                    minX = min(minX, cx)
                    maxX = max(maxX, cx)
                    minY = min(minY, cy)
                    maxY = max(maxY, cy)
                    for (dy in -1..1) for (dx in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val nx = cx + dx
                        val ny = cy + dy
                        if (nx <= 0 || ny <= 0 || nx >= w - 1 || ny >= h - 1) continue
                        val ni = ny * w + nx
                        if (!edges[ni] || visited[ni]) continue
                        visited[ni] = true
                        stackX[sp] = nx
                        stackY[sp] = ny
                        sp++
                    }
                }
                if (pts.size < 60) continue
                val bw = (maxX - minX + 1).toFloat()
                val bh = (maxY - minY + 1).toFloat()
                if (bw < w * 0.30f || bh < h * 0.30f) continue
                // Reject edge-hugging “frames” that are just the screen border
                if (minX < 2 && maxX > w - 3 && minY < 2 && maxY > h - 3) continue
                val quad = approxQuadFromBounds(minX, minY, maxX, maxY, pts) ?: continue
                val area = polygonArea(quad)
                val rect = rectangularity(quad)
                if (rect < 0.55f) continue
                val center = centrality(quad, w, h)
                val contrast = interiorContrast(quad, gray, w, h)
                val score = area * (0.45f + 0.30f * rect + 0.15f * center + 0.10f * contrast)
                if (score > bestScore) {
                    bestScore = score
                    best = quad
                }
            }
        }
        return best
    }

    /** 1 = centroid near frame center; penalize edge-biased quads. */
    private fun centrality(q: Array<PointF>, w: Int, h: Int): Float {
        var cx = 0f
        var cy = 0f
        for (p in q) {
            cx += p.x
            cy += p.y
        }
        cx /= 4f * w
        cy /= 4f * h
        val dx = abs(cx - 0.5f)
        val dy = abs(cy - 0.5f)
        return (1f - (dx + dy)).coerceIn(0f, 1f)
    }

    /**
     * Page vs background: mean luma inside quad vs a ring just outside.
     * Paper on a darker desk (or reverse) scores higher.
     */
    private fun interiorContrast(q: Array<PointF>, gray: IntArray, w: Int, h: Int): Float {
        val br = boundingRect(
            Array(4) { PointF(q[it].x / w, q[it].y / h) }
        )
        val l = (br[0] * w).roundToInt().coerceIn(1, w - 2)
        val t = (br[1] * h).roundToInt().coerceIn(1, h - 2)
        val r = (br[2] * w).roundToInt().coerceIn(l + 2, w - 1)
        val b = (br[3] * h).roundToInt().coerceIn(t + 2, h - 1)
        val padX = max(2, (r - l) / 10)
        val padY = max(2, (b - t) / 10)
        val il = (l + padX).coerceAtMost(r - 1)
        val it = (t + padY).coerceAtMost(b - 1)
        val ir = (r - padX).coerceAtLeast(il + 1)
        val ib = (b - padY).coerceAtLeast(it + 1)

        fun meanRect(x0: Int, y0: Int, x1: Int, y1: Int): Float {
            var s = 0L
            var n = 0
            var y = y0
            while (y < y1) {
                var x = x0
                while (x < x1) {
                    s += gray[y * w + x]
                    n++
                    x += 2
                }
                y += 2
            }
            return if (n == 0) 0f else s.toFloat() / n
        }

        val inside = meanRect(il, it, ir, ib)
        // Outside ring samples (clamped)
        val outL = meanRect(max(0, l - padX), t, l, b)
        val outR = meanRect(r, t, min(w, r + padX), b)
        val outT = meanRect(l, max(0, t - padY), r, t)
        val outB = meanRect(l, b, r, min(h, b + padY))
        val outside = (outL + outR + outT + outB) / 4f
        val delta = abs(inside - outside)
        return (delta / 80f).coerceIn(0f, 1f)
    }

    /**
     * Fit a quad by taking extreme points near the bounding-box corners of the blob.
     */
    private fun approxQuadFromBounds(
        minX: Int,
        minY: Int,
        maxX: Int,
        maxY: Int,
        pts: List<PointF>
    ): Array<PointF>? {
        fun farthest(tx: Float, ty: Float): PointF {
            var best = pts[0]
            var bestD = -1f
            for (p in pts) {
                val d = hypot(p.x - tx, p.y - ty)
                if (d > bestD) {
                    bestD = d
                    best = p
                }
            }
            return PointF(best.x, best.y)
        }
        // Corner targets of bbox
        val tl = farthest(minX.toFloat(), minY.toFloat())
        val tr = farthest(maxX.toFloat(), minY.toFloat())
        val br = farthest(maxX.toFloat(), maxY.toFloat())
        val bl = farthest(minX.toFloat(), maxY.toFloat())
        val quad = arrayOf(tl, tr, br, bl)
        // Reject degenerate
        if (polygonArea(quad) < 100f) return null
        if (hypot(tl.x - tr.x, tl.y - tr.y) < 8f) return null
        if (hypot(tr.x - br.x, tr.y - br.y) < 8f) return null
        return quad
    }

    private fun polygonArea(q: Array<PointF>): Float {
        var sum = 0f
        for (i in q.indices) {
            val j = (i + 1) % q.size
            sum += q[i].x * q[j].y - q[j].x * q[i].y
        }
        return abs(sum) * 0.5f
    }

    private fun rectangularity(q: Array<PointF>): Float {
        fun ang(a: PointF, b: PointF, c: PointF): Float {
            val bax = a.x - b.x
            val bay = a.y - b.y
            val bcx = c.x - b.x
            val bcy = c.y - b.y
            val dot = bax * bcx + bay * bcy
            val det = bax * bcy - bay * bcx
            return abs(kotlin.math.atan2(det, dot))
        }
        var score = 0f
        for (i in 0..3) {
            val a = q[i]
            val b = q[(i + 1) % 4]
            val c = q[(i + 2) % 4]
            val angle = ang(a, b, c)
            // prefer ~90° (π/2)
            score += 1f - (abs(angle - (Math.PI.toFloat() / 2f)) / (Math.PI.toFloat() / 2f)).coerceIn(0f, 1f)
        }
        return (score / 4f).coerceIn(0f, 1f)
    }

    private fun orderQuadTlTrBrBl(q: Array<PointF>) {
        val sorted = q.sortedWith(compareBy({ it.y }, { it.x }))
        val top = sorted.take(2).sortedBy { it.x }
        val bottom = sorted.takeLast(2).sortedBy { it.x }
        q[0] = top[0]
        q[1] = top[1]
        q[2] = bottom[1]
        q[3] = bottom[0]
    }
}
