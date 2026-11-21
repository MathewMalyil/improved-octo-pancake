package com.mmalyil.smartdocai.scan

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.PointF
import android.util.Log
import androidx.camera.core.ImageProxy
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * OpenCV-backed document quad detector for live CameraX frames and stills.
 * Prefer this over the pure-Kotlin heuristic when OpenCV loads.
 */
object OpenCvDocumentDetector {

    private const val TAG = "OpenCvDocDetect"
    private const val ANALYZE_W = 480

    @Volatile
    private var ready = false

    fun ensureInit(): Boolean {
        if (ready) return true
        return try {
            ready = OpenCVLoader.initLocal()
            Log.i(TAG, "OpenCV initLocal=$ready")
            ready
        } catch (e: Exception) {
            Log.e(TAG, "OpenCV init failed", e)
            false
        }
    }

    fun isReady(): Boolean = ready

    fun detect(imageProxy: ImageProxy): DocumentEdgeDetector.Detection? {
        if (!ensureInit()) return null
        val grayFull = imageProxyToGrayMat(imageProxy) ?: return null
        return try {
            detectGrayMat(grayFull)
        } finally {
            grayFull.release()
        }
    }

    fun detectBitmap(source: Bitmap, analyzeWidth: Int = ANALYZE_W): DocumentEdgeDetector.Detection? {
        if (!ensureInit()) return null
        val rgba = Mat()
        val gray = Mat()
        return try {
            Utils.bitmapToMat(source, rgba)
            when (rgba.channels()) {
                4 -> Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
                3 -> Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGB2GRAY)
                1 -> rgba.copyTo(gray)
                else -> Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_BGRA2GRAY)
            }
            detectGrayMat(gray, analyzeWidth.coerceIn(320, 1280))
        } catch (e: Exception) {
            Log.w(TAG, "detectBitmap failed", e)
            null
        } finally {
            rgba.release()
            gray.release()
        }
    }

    private fun detectGrayMat(graySrc: Mat, analyzeWidth: Int = ANALYZE_W): DocumentEdgeDetector.Detection? {
        val scale = analyzeWidth.toDouble() / max(graySrc.cols(), 1)
        val w = max(32, (graySrc.cols() * min(scale, 1.0)).toInt())
        val h = max(32, (graySrc.rows() * min(scale, 1.0)).toInt())
        val gray = Mat()
        Imgproc.resize(graySrc, gray, Size(w.toDouble(), h.toDouble()))

        val mean = org.opencv.core.Core.mean(gray).`val`[0]
        Log.d(TAG, "frame ${w}x$h meanLuma=${"%.1f".format(mean)}")
        if (mean < 15.0) {
            // Near-black analysis buffer (lens covered / preview not lit)
            gray.release()
            Log.w(TAG, "frame too dark mean=$mean — skip")
            return null
        }

        val strategies = listOf(
            { findByCanny(gray, cannyLo = 40.0, cannyHi = 120.0) },
            { findByAdaptive(gray, brightPaper = true) },
            { findByCanny(gray, cannyLo = 20.0, cannyHi = 80.0) },
            { findByAdaptive(gray, brightPaper = false) },
            { findByOtsu(gray, brightPaper = true) }
        )

        var best: Array<PointF>? = null
        var bestScore = 0f
        var bestName = ""
        val names = listOf("canny120", "adaptBright", "canny80", "adaptDark", "otsuBright")
        for ((i, strat) in strategies.withIndex()) {
            val quad = try {
                strat()
            } catch (e: Exception) {
                Log.w(TAG, "strategy ${names[i]} failed", e)
                null
            } ?: continue
            val score = scoreQuad(quad, gray, liveStrict = analyzeWidth <= ANALYZE_W + 40)
            if (score > bestScore) {
                bestScore = score
                best = quad
                bestName = names[i]
            }
        }
        gray.release()

        val quad = best ?: run {
            Log.d(TAG, "no quad mean=$mean")
            return null
        }
        val minAccept = if (analyzeWidth <= ANALYZE_W + 40) 0.32f else 0.28f
        if (bestScore < minAccept) {
            Log.d(TAG, "weak score=$bestScore strategy=$bestName live=${analyzeWidth <= ANALYZE_W + 40}")
            return null
        }
        orderTlTrBrBl(quad)
        Log.i(TAG, "HIT strategy=$bestName score=${"%.2f".format(bestScore)} mean=${"%.0f".format(mean)}")
        return DocumentEdgeDetector.Detection(quad, bestScore, w, h)
    }

    private fun findByCanny(gray: Mat, cannyLo: Double, cannyHi: Double): Array<PointF>? {
        val blur = Mat()
        val edges = Mat()
        try {
            Imgproc.GaussianBlur(gray, blur, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blur, edges, cannyLo, cannyHi)
            return largestQuadFromEdges(edges, gray.cols(), gray.rows())
        } finally {
            blur.release()
            edges.release()
        }
    }

    private fun findByAdaptive(gray: Mat, brightPaper: Boolean): Array<PointF>? {
        val bin = Mat()
        val morph = Mat()
        try {
            Imgproc.adaptiveThreshold(
                gray, bin, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                31, 8.0
            )
            if (!brightPaper) org.opencv.core.Core.bitwise_not(bin, bin)
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
            Imgproc.morphologyEx(bin, morph, Imgproc.MORPH_CLOSE, kernel)
            kernel.release()
            return largestQuadFromBinary(morph, gray.cols(), gray.rows())
        } finally {
            bin.release()
            morph.release()
        }
    }

    private fun findByOtsu(gray: Mat, brightPaper: Boolean): Array<PointF>? {
        val bin = Mat()
        try {
            Imgproc.threshold(gray, bin, 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
            if (!brightPaper) org.opencv.core.Core.bitwise_not(bin, bin)
            return largestQuadFromBinary(bin, gray.cols(), gray.rows())
        } finally {
            bin.release()
        }
    }

    private fun largestQuadFromEdges(edges: Mat, w: Int, h: Int): Array<PointF>? {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        try {
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
            return pickBestQuad(contours, w, h)
        } finally {
            hierarchy.release()
            contours.forEach { it.release() }
        }
    }

    private fun largestQuadFromBinary(bin: Mat, w: Int, h: Int): Array<PointF>? {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        try {
            Imgproc.findContours(bin, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            return pickBestQuad(contours, w, h)
        } finally {
            hierarchy.release()
            contours.forEach { it.release() }
        }
    }

    private fun pickBestQuad(contours: List<MatOfPoint>, w: Int, h: Int): Array<PointF>? {
        val frameArea = (w * h).toDouble()
        // Live analyze (~480w) uses a higher floor to skip tiny partial desk fragments.
        val minFrac = if (w <= ANALYZE_W + 40) 0.12 else 0.08
        val maxFrac = if (w <= ANALYZE_W + 40) 0.94 else 0.96
        var best: Array<PointF>? = null
        var bestArea = 0.0
        for (c in contours) {
            val area = Imgproc.contourArea(c)
            if (area < frameArea * minFrac || area > frameArea * maxFrac) continue
            val peri = Imgproc.arcLength(MatOfPoint2f(*c.toArray()), true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(MatOfPoint2f(*c.toArray()), approx, 0.02 * peri, true)
            val pts = approx.toArray()
            approx.release()
            if (pts.size != 4) continue
            val mop = MatOfPoint(*pts)
            val convex = Imgproc.isContourConvex(mop)
            mop.release()
            if (!convex) continue
            if (area > bestArea) {
                bestArea = area
                best = Array(4) { i ->
                    PointF(
                        (pts[i].x / w).toFloat().coerceIn(0f, 1f),
                        (pts[i].y / h).toFloat().coerceIn(0f, 1f)
                    )
                }
            }
        }
        return best
    }

    private fun scoreQuad(quad: Array<PointF>, gray: Mat, liveStrict: Boolean = false): Float {
        val area = polygonArea(quad)
        val minA = if (liveStrict) 0.14f else 0.08f
        val maxA = if (liveStrict) 0.92f else 0.96f
        if (area < minA || area > maxA) return 0f
        val rect = rectangularity(quad)
        val minRect = if (liveStrict) 0.42f else 0.35f
        if (rect < minRect) return 0f
        var cx = 0f
        var cy = 0f
        for (p in quad) {
            cx += p.x
            cy += p.y
        }
        cx /= 4f
        cy /= 4f
        val center = (1f - (abs(cx - 0.5f) + abs(cy - 0.5f))).coerceIn(0f, 1f)
        // Live: weight rectangularity a bit more (reject skewed partial edges).
        return if (liveStrict) {
            (area * 0.30f + rect * 0.48f + center * 0.22f).coerceIn(0f, 1f)
        } else {
            (area * 0.35f + rect * 0.40f + center * 0.25f).coerceIn(0f, 1f)
        }
    }

    /**
     * Convert CameraX [ImageProxy] to grayscale Mat.
     * Supports RGBA_8888 (preferred) and YUV_420_888 Y-plane.
     */
    private fun imageProxyToGrayMat(image: ImageProxy): Mat? {
        return try {
            when (image.format) {
                ImageFormat.FLEX_RGBA_8888, 1 /* RGBA from CameraX */, PixelFormat_RGBA() -> {
                    rgbaProxyToGray(image)
                }
                ImageFormat.YUV_420_888 -> yPlaneToMat(image)
                else -> {
                    // CameraX OUTPUT_IMAGE_FORMAT_RGBA_8888 often reports format=1
                    if (image.planes.size == 1 && image.planes[0].pixelStride == 4) {
                        rgbaProxyToGray(image)
                    } else {
                        yPlaneToMat(image)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "imageProxyToGrayMat failed fmt=${image.format}", e)
            null
        }
    }

    private fun PixelFormat_RGBA(): Int = 1

    private fun rgbaProxyToGray(image: ImageProxy): Mat? {
        val plane = image.planes[0]
        val buffer = plane.buffer.duplicate()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height
        val rgba = Mat(height, width, CvType.CV_8UC4)
        val row = ByteArray(rowStride)
        val outRow = ByteArray(width * 4)
        for (y in 0 until height) {
            buffer.position(y * rowStride)
            val toRead = min(rowStride, buffer.remaining())
            if (toRead <= 0) break
            buffer.get(row, 0, toRead)
            if (pixelStride == 4 && rowStride >= width * 4) {
                System.arraycopy(row, 0, outRow, 0, width * 4)
            } else {
                var x = 0
                while (x < width) {
                    val si = x * pixelStride
                    outRow[x * 4] = row[si]
                    outRow[x * 4 + 1] = row[si + 1]
                    outRow[x * 4 + 2] = row[si + 2]
                    outRow[x * 4 + 3] = if (si + 3 < row.size) row[si + 3] else 0xFF.toByte()
                    x++
                }
            }
            rgba.put(y, 0, outRow)
        }
        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        rgba.release()
        return gray
    }

    private fun yPlaneToMat(image: ImageProxy): Mat? {
        return try {
            val plane = image.planes[0]
            val buffer = plane.buffer.duplicate()
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val width = image.width
            val height = image.height
            val mat = Mat(height, width, CvType.CV_8UC1)
            val row = ByteArray(rowStride)
            val outRow = ByteArray(width)
            for (y in 0 until height) {
                buffer.position(y * rowStride)
                buffer.get(row, 0, min(rowStride, buffer.remaining()))
                if (pixelStride == 1) {
                    System.arraycopy(row, 0, outRow, 0, width)
                } else {
                    var x = 0
                    while (x < width) {
                        outRow[x] = row[x * pixelStride]
                        x++
                    }
                }
                mat.put(y, 0, outRow)
            }
            mat
        } catch (e: Exception) {
            Log.w(TAG, "yPlaneToMat failed", e)
            null
        }
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
            val angle = ang(q[i], q[(i + 1) % 4], q[(i + 2) % 4])
            score += 1f - (abs(angle - (Math.PI.toFloat() / 2f)) / (Math.PI.toFloat() / 2f)).coerceIn(0f, 1f)
        }
        val sides = FloatArray(4) { i ->
            hypot(
                (q[(i + 1) % 4].x - q[i].x).toDouble(),
                (q[(i + 1) % 4].y - q[i].y).toDouble()
            ).toFloat()
        }
        if (sides.any { it < 0.05f }) return 0f
        return (score / 4f).coerceIn(0f, 1f)
    }

    private fun orderTlTrBrBl(q: Array<PointF>) {
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
