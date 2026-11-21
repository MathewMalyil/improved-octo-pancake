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
 * Lightweight document-quad detector for CameraX [ImageAnalysis] and still captures.
 * Pure Kotlin (no OpenCV natives) — Canny-lite + contour → largest 4-gon.
 */
object DocumentEdgeDetector {

    enum class Mode {
        /** Live preview — balanced speed / false positives. */
        LIVE,
        /** Still capture — higher res, moderate sensitivity. */
        STILL,
        /** Still retry — stronger edges, looser geometry for deskew. */
        STILL_STRONG
    }

    data class Detection(
        /** TL, TR, BR, BL in normalized coords [0,1]. */
        val quad: Array<PointF>,
        val score: Float,
        val analysisWidth: Int,
        val analysisHeight: Int
    )

    private data class Params(
        val analyzeWidth: Int,
        val blurPasses: Int,
        val cannyMeanMul: Float,
        val cannyFloor: Float,
        val minAreaRatio: Float,
        val maxAreaRatio: Float,
        val minRect: Float,
        val minScore: Float,
        val minBlobPts: Int,
        val minBlobFrac: Float,
        val step: Int
    )

    private val LIVE = Params(
        analyzeWidth = 480,
        blurPasses = 1,
        cannyMeanMul = 1.55f,
        cannyFloor = 36f,
        minAreaRatio = 0.12f,
        maxAreaRatio = 0.94f,
        minRect = 0.48f,
        minScore = 0.38f,
        minBlobPts = 40,
        minBlobFrac = 0.22f,
        step = 2
    )

    private val STILL = Params(
        analyzeWidth = 720,
        blurPasses = 1,
        cannyMeanMul = 1.55f,
        cannyFloor = 36f,
        minAreaRatio = 0.12f,
        maxAreaRatio = 0.95f,
        minRect = 0.48f,
        minScore = 0.38f,
        minBlobPts = 40,
        minBlobFrac = 0.20f,
        step = 1
    )

    private val STILL_STRONG = Params(
        analyzeWidth = 960,
        blurPasses = 1,
        cannyMeanMul = 1.25f,
        cannyFloor = 28f,
        minAreaRatio = 0.08f,
        maxAreaRatio = 0.97f,
        minRect = 0.40f,
        minScore = 0.30f,
        minBlobPts = 30,
        minBlobFrac = 0.15f,
        step = 1
    )

    fun detect(imageProxy: ImageProxy): Detection? {
        val grayFull = imageProxyToGray(imageProxy) ?: return null
        val srcW = imageProxy.width
        val srcH = imageProxy.height
        // Canny first; paper-mask fallback for white page on dark desk (LIVE)
        return detectGray(grayFull, srcW, srcH, LIVE)
            ?: run {
                val scale = LIVE.analyzeWidth.toFloat() / max(srcW, 1)
                val w = max(32, (srcW * min(scale, 1f)).roundToInt())
                val h = max(32, (srcH * min(scale, 1f)).roundToInt())
                val gray = if (w == srcW && h == srcH) grayFull else downsampleGray(grayFull, srcW, srcH, w, h)
                detectPaperMask(gray, w, h, LIVE)
            }
    }

    /** Supports CameraX RGBA_8888 and YUV_420_888. */
    private fun imageProxyToGray(image: ImageProxy): IntArray? {
        val plane = image.planes.getOrNull(0) ?: return null
        return if (plane.pixelStride == 4 || image.planes.size == 1 && image.format != 35) {
            rgbaToGray(image)
        } else {
            yPlaneToGray(image)
        }
    }

    private fun rgbaToGray(image: ImageProxy): IntArray? {
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
                    val si = x * pixelStride
                    val r = row[si].toInt() and 0xff
                    val g = row[si + 1].toInt() and 0xff
                    val b = row[si + 2].toInt() and 0xff
                    out[i++] = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                    x++
                }
            }
            out
        } catch (_: Exception) {
            null
        }
    }

    fun detectBitmap(source: Bitmap, mode: Mode = Mode.STILL): Detection? {
        val params = when (mode) {
            Mode.LIVE -> LIVE
            Mode.STILL -> STILL
            Mode.STILL_STRONG -> STILL_STRONG
        }
        val scale = params.analyzeWidth.toFloat() / max(source.width, 1)
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
            return detectGray(gray, w, h, params)
                ?: detectPaperMask(gray, w, h, params)
        } finally {
            if (small !== source && !small.isRecycled) small.recycle()
        }
    }

    private fun detectGray(grayIn: IntArray, srcW: Int, srcH: Int, params: Params): Detection? {
        val scale = params.analyzeWidth.toFloat() / max(srcW, 1)
        val w = max(32, (srcW * min(scale, 1f)).roundToInt())
        val h = max(32, (srcH * min(scale, 1f)).roundToInt())
        val gray = if (w == srcW && h == srcH) grayIn.copyOf() else downsampleGray(grayIn, srcW, srcH, w, h)
        repeat(params.blurPasses) { blur3x3(gray, w, h) }
        val edges = cannyLite(gray, w, h, params.cannyMeanMul, params.cannyFloor)
        val quad = findBestQuad(edges, gray, w, h, params) ?: return null
        return scoreNormalized(quad, gray, w, h, params)
    }

    /**
     * Fallback for white page on dark desk: threshold bright region → corners.
     * Catches pages when Canny contour is fragmented by print/text.
     */
    private fun detectPaperMask(gray: IntArray, w: Int, h: Int, params: Params): Detection? {
        var sum = 0L
        var maxL = 0
        for (v in gray) {
            sum += v
            if (v > maxL) maxL = v
        }
        val mean = sum.toFloat() / gray.size
        // Aggressive: for dark desks (low mean) use mid threshold; else Otsu-ish
        val thr = when {
            mean < 70f -> max(55, ((mean + maxL) / 2f).roundToInt()).coerceAtMost(140)
            mean < 120f -> ((mean + 180f) * 0.45f).roundToInt().coerceIn(80, 170)
            else -> ((mean + 200f) * 0.5f).roundToInt().coerceIn(110, 200)
        }
        val mask = BooleanArray(w * h)
        var brightCount = 0
        for (i in gray.indices) {
            val on = gray[i] >= thr
            mask[i] = on
            if (on) brightCount++
        }
        // If almost nothing bright, try inverted (dark page on light desk)
        val invert = brightCount < gray.size * 0.05 || brightCount > gray.size * 0.92
        if (invert) {
            val darkThr = (mean * 0.85f).roundToInt().coerceIn(40, 140)
            brightCount = 0
            for (i in gray.indices) {
                val on = gray[i] <= darkThr
                mask[i] = on
                if (on) brightCount++
            }
        }
        android.util.Log.d(
            "DocEdgeDetect",
            "paperMask mean=${"%.0f".format(mean)} max=$maxL thr=$thr bright=$brightCount invert=$invert"
        )

        // Largest bright component (skip border-touching full frames)
        val visited = BooleanArray(w * h)
        var bestPts: ArrayList<PointF>? = null
        var bestArea = 0
        val stackX = IntArray(w * h)
        val stackY = IntArray(w * h)

        for (y in 1 until h - 1 step 2) {
            for (x in 1 until w - 1 step 2) {
                val start = y * w + x
                if (!mask[start] || visited[start]) continue
                var sp = 0
                stackX[sp] = x
                stackY[sp] = y
                sp++
                visited[start] = true
                val pts = ArrayList<PointF>(512)
                var minX = x
                var maxX = x
                var minY = y
                var maxY = y
                var area = 0
                while (sp > 0) {
                    sp--
                    val cx = stackX[sp]
                    val cy = stackY[sp]
                    pts.add(PointF(cx.toFloat(), cy.toFloat()))
                    area++
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
                        if (!mask[ni] || visited[ni]) continue
                        visited[ni] = true
                        stackX[sp] = nx
                        stackY[sp] = ny
                        sp++
                    }
                }
                val bw = maxX - minX + 1
                val bh = maxY - minY + 1
                if (area < w * h * params.minAreaRatio * 0.5f) continue
                if (bw < w * params.minBlobFrac || bh < h * params.minBlobFrac) continue
                if (minX < 2 && maxX > w - 3 && minY < 2 && maxY > h - 3 &&
                    area > w * h * 0.92f
                ) continue
                if (area > bestArea) {
                    bestArea = area
                    bestPts = pts
                }
            }
        }
        val pts = bestPts ?: return null
        val quad = cornersFromPoints(pts) ?: return null
        return scoreNormalized(quad, gray, w, h, params)
    }

    private fun scoreNormalized(
        quad: Array<PointF>,
        gray: IntArray,
        w: Int,
        h: Int,
        params: Params
    ): Detection? {
        val area = polygonArea(quad)
        val frameArea = (w * h).toFloat()
        val areaRatio = area / frameArea
        if (areaRatio < params.minAreaRatio || areaRatio > params.maxAreaRatio) return null
        val rectScore = rectangularity(quad)
        if (rectScore < params.minRect) return null
        val centerScore = centrality(quad, w, h)
        val contrastScore = interiorContrast(quad, gray, w, h)
        // Prefer large, centered, rectangular pages with page/bg separation
        val score = (
            areaRatio * 0.32f +
                rectScore * 0.28f +
                centerScore * 0.18f +
                contrastScore * 0.22f
            ).coerceIn(0f, 1f)
        if (score < params.minScore) return null
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

    private fun cannyLite(
        gray: IntArray,
        w: Int,
        h: Int,
        meanMul: Float,
        floor: Float
    ): BooleanArray {
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
        val hi = max(floor, mean * meanMul)
        val lo = hi * 0.45f
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

    private fun findBestQuad(
        edges: BooleanArray,
        gray: IntArray,
        w: Int,
        h: Int,
        params: Params
    ): Array<PointF>? {
        val visited = BooleanArray(w * h)
        var best: Array<PointF>? = null
        var bestScore = 0f
        val stackX = IntArray(w * h)
        val stackY = IntArray(w * h)
        val step = params.step.coerceAtLeast(1)

        for (y in 1 until h - 1 step step) {
            for (x in 1 until w - 1 step step) {
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
                if (pts.size < params.minBlobPts) continue
                val bw = (maxX - minX + 1).toFloat()
                val bh = (maxY - minY + 1).toFloat()
                if (bw < w * params.minBlobFrac || bh < h * params.minBlobFrac) continue
                // Reject edge-hugging “frames” that are just the screen border
                if (minX < 2 && maxX > w - 3 && minY < 2 && maxY > h - 3) continue
                val quad = cornersFromPoints(pts) ?: continue
                val area = polygonArea(quad)
                val rect = rectangularity(quad)
                if (rect < params.minRect * 0.9f) continue
                val center = centrality(quad, w, h)
                val contrast = interiorContrast(quad, gray, w, h)
                val score = area * (0.40f + 0.30f * rect + 0.15f * center + 0.15f * contrast)
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
        val outL = meanRect(max(0, l - padX), t, l, b)
        val outR = meanRect(r, t, min(w, r + padX), b)
        val outT = meanRect(l, max(0, t - padY), r, t)
        val outB = meanRect(l, b, r, min(h, b + padY))
        val outside = (outL + outR + outT + outB) / 4f
        val delta = abs(inside - outside)
        return (delta / 70f).coerceIn(0f, 1f)
    }

    /**
     * Robust document corners from contour / mask points using sum/diff extremes
     * (works under perspective better than bbox-farthest alone).
     */
    private fun cornersFromPoints(pts: List<PointF>): Array<PointF>? {
        if (pts.size < 4) return null
        var tl = pts[0]
        var tr = pts[0]
        var br = pts[0]
        var bl = pts[0]
        var minSum = Float.POSITIVE_INFINITY
        var maxSum = Float.NEGATIVE_INFINITY
        var minDiff = Float.POSITIVE_INFINITY
        var maxDiff = Float.NEGATIVE_INFINITY
        for (p in pts) {
            val sum = p.x + p.y
            val diff = p.x - p.y
            if (sum < minSum) {
                minSum = sum
                tl = p
            }
            if (sum > maxSum) {
                maxSum = sum
                br = p
            }
            if (diff > maxDiff) {
                maxDiff = diff
                tr = p
            }
            if (diff < minDiff) {
                minDiff = diff
                bl = p
            }
        }
        val quad = arrayOf(
            PointF(tl.x, tl.y),
            PointF(tr.x, tr.y),
            PointF(br.x, br.y),
            PointF(bl.x, bl.y)
        )
        if (polygonArea(quad) < 100f) return null
        if (hypot(tl.x - tr.x, tl.y - tr.y) < 8f) return null
        if (hypot(tr.x - br.x, tr.y - br.y) < 8f) return null
        if (hypot(br.x - bl.x, br.y - bl.y) < 8f) return null
        if (hypot(bl.x - tl.x, bl.y - tl.y) < 8f) return null
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
            score += 1f - (abs(angle - (Math.PI.toFloat() / 2f)) / (Math.PI.toFloat() / 2f)).coerceIn(0f, 1f)
        }
        return (score / 4f).coerceIn(0f, 1f)
    }

    /** Order by sum/diff extremes — stable under camera perspective. */
    private fun orderQuadTlTrBrBl(q: Array<PointF>) {
        var tl = q[0]
        var tr = q[0]
        var br = q[0]
        var bl = q[0]
        var minSum = Float.POSITIVE_INFINITY
        var maxSum = Float.NEGATIVE_INFINITY
        var minDiff = Float.POSITIVE_INFINITY
        var maxDiff = Float.NEGATIVE_INFINITY
        for (p in q) {
            val sum = p.x + p.y
            val diff = p.x - p.y
            if (sum < minSum) {
                minSum = sum
                tl = p
            }
            if (sum > maxSum) {
                maxSum = sum
                br = p
            }
            if (diff > maxDiff) {
                maxDiff = diff
                tr = p
            }
            if (diff < minDiff) {
                minDiff = diff
                bl = p
            }
        }
        q[0] = tl
        q[1] = tr
        q[2] = br
        q[3] = bl
    }
}
