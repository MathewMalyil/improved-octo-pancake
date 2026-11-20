package com.mmalyil.smartdocai.scan

import android.graphics.PointF
import androidx.annotation.OptIn
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory

/**
 * Maps document quads from ImageAnalysis buffer space into PreviewView / overlay
 * normalized [0,1] coordinates, accounting for rotation, aspect ratio, and
 * FILL_CENTER crop (the usual cause of “edge on another plane”).
 *
 * Call while [imageProxy] is still open — [PreviewView.getOutputTransform] must be ready.
 */
object PreviewCoordMapper {

    private val proxyFactory = ImageProxyTransformFactory().apply {
        isUsingCropRect = true
        isUsingRotationDegrees = true
    }

    /**
     * @param quadNorm detection in analysis-buffer normalized space (Y-plane detector).
     * @return overlay-normalized TL,TR,BR,BL or null if transform unavailable.
     */
    @OptIn(TransformExperimental::class)
    fun analysisQuadToOverlay(
        imageProxy: ImageProxy,
        previewView: PreviewView,
        overlayWidth: Int,
        overlayHeight: Int,
        quadNorm: Array<PointF>
    ): Array<PointF>? {
        if (overlayWidth <= 0 || overlayHeight <= 0) return null
        val target = previewView.outputTransform
            ?: return fallbackMap(
                quadNorm,
                imageProxy.width,
                imageProxy.height,
                imageProxy.imageInfo.rotationDegrees,
                overlayWidth,
                overlayHeight
            )

        val source = proxyFactory.getOutputTransform(imageProxy)
        val bufW = imageProxy.width.toFloat().coerceAtLeast(1f)
        val bufH = imageProxy.height.toFloat().coerceAtLeast(1f)

        val pts = FloatArray(8)
        for (i in 0..3) {
            pts[i * 2] = quadNorm[i].x * bufW
            pts[i * 2 + 1] = quadNorm[i].y * bufH
        }

        return try {
            CoordinateTransform(source, target).mapPoints(pts)
            val out = Array(4) { i ->
                PointF(
                    (pts[i * 2] / overlayWidth).coerceIn(-0.05f, 1.05f)
                        .coerceIn(0f, 1f),
                    (pts[i * 2 + 1] / overlayHeight).coerceIn(-0.05f, 1.05f)
                        .coerceIn(0f, 1f)
                )
            }
            orderTlTrBrBl(out)
            out
        } catch (_: Exception) {
            fallbackMap(
                quadNorm,
                imageProxy.width,
                imageProxy.height,
                imageProxy.imageInfo.rotationDegrees,
                overlayWidth,
                overlayHeight
            )
        }
    }

    /**
     * Approximate FILL_CENTER: rotate buffer into display orientation, scale to cover
     * the view, then normalize by view size (accounts for crop letterboxing).
     */
    fun fallbackMap(
        quadNorm: Array<PointF>,
        bufferW: Int,
        bufferH: Int,
        rotationDegrees: Int,
        viewW: Int,
        viewH: Int
    ): Array<PointF>? {
        if (viewW <= 0 || viewH <= 0 || bufferW <= 0 || bufferH <= 0) return null
        val rot = (rotationDegrees % 360 + 360) % 360
        val srcW = if (rot == 90 || rot == 270) bufferH.toFloat() else bufferW.toFloat()
        val srcH = if (rot == 90 || rot == 270) bufferW.toFloat() else bufferH.toFloat()

        val scale = maxOf(viewW / srcW, viewH / srcH)
        val scaledW = srcW * scale
        val scaledH = srcH * scale
        val dx = (viewW - scaledW) / 2f
        val dy = (viewH - scaledH) / 2f

        fun rotPoint(nx: Float, ny: Float): PointF {
            val x = nx * bufferW
            val y = ny * bufferH
            return when (rot) {
                90 -> PointF(bufferH - y, x)
                180 -> PointF(bufferW - x, bufferH - y)
                270 -> PointF(y, bufferW - x)
                else -> PointF(x, y)
            }
        }

        val out = Array(4) { i ->
            val rp = rotPoint(quadNorm[i].x, quadNorm[i].y)
            val vx = rp.x * scale + dx
            val vy = rp.y * scale + dy
            PointF(
                (vx / viewW).coerceIn(0f, 1f),
                (vy / viewH).coerceIn(0f, 1f)
            )
        }
        orderTlTrBrBl(out)
        return out
    }

    private fun orderTlTrBrBl(q: Array<PointF>) {
        val sorted = q.sortedWith(compareBy({ it.y }, { it.x }))
        val top = sorted.take(2).sortedBy { it.x }
        val bottom = sorted.takeLast(2).sortedBy { it.x }
        q[0] = top[0]
        q[1] = top[1]
        q[2] = bottom[1]
        q[3] = bottom[0]
    }
}
