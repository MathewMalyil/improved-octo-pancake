package com.mmalyil.smartdocai.scan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Dimmed overlay with a draggable crop rectangle. Normalized crop [0,1] relative to imageBounds.
 */
class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Image display rect inside this view (same coords as the ImageView drawable bounds). */
    var imageBounds: RectF = RectF()
        set(value) {
            field = value
            invalidate()
        }

    var cropLeft = 0f
    var cropTop = 0f
    var cropRight = 1f
    var cropBottom = 1f

    var onCropChanged: ((l: Float, t: Float, r: Float, b: Float) -> Unit)? = null

    private val dimPaint = Paint().apply { color = 0x99000000.toInt() }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        color = 0xFFFFFFFF.toInt()
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
    }
    private val path = Path()
    private val cropRect = RectF()

    private enum class Handle { NONE, MOVE, L, T, R, B, TL, TR, BL, BR }

    private var active = Handle.NONE
    private var lastX = 0f
    private var lastY = 0f
    private val touchSlop = 28f * resources.displayMetrics.density

    fun setNormalizedCrop(l: Float, t: Float, r: Float, b: Float) {
        cropLeft = l.coerceIn(0f, 1f)
        cropTop = t.coerceIn(0f, 1f)
        cropRight = r.coerceIn(0f, 1f)
        cropBottom = b.coerceIn(0f, 1f)
        if (cropRight - cropLeft < 0.08f) cropRight = (cropLeft + 0.08f).coerceAtMost(1f)
        if (cropBottom - cropTop < 0.08f) cropBottom = (cropTop + 0.08f).coerceAtMost(1f)
        invalidate()
    }

    fun getNormalizedCrop(): FloatArray = floatArrayOf(cropLeft, cropTop, cropRight, cropBottom)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (imageBounds.isEmpty) return
        updateCropRect()

        path.reset()
        path.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        path.addRect(cropRect, Path.Direction.CCW)
        canvas.drawPath(path, dimPaint)
        canvas.drawRect(cropRect, borderPaint)

        val hs = 8f * resources.displayMetrics.density
        listOf(
            cropRect.left to cropRect.top,
            cropRect.right to cropRect.top,
            cropRect.left to cropRect.bottom,
            cropRect.right to cropRect.bottom
        ).forEach { (x, y) ->
            canvas.drawCircle(x, y, hs, handlePaint)
        }
    }

    private fun updateCropRect() {
        cropRect.set(
            imageBounds.left + cropLeft * imageBounds.width(),
            imageBounds.top + cropTop * imageBounds.height(),
            imageBounds.left + cropRight * imageBounds.width(),
            imageBounds.top + cropBottom * imageBounds.height()
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (imageBounds.isEmpty) return false
        updateCropRect()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                active = hitTest(event.x, event.y)
                lastX = event.x
                lastY = event.y
                return active != Handle.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                if (active == Handle.NONE) return false
                val dx = event.x - lastX
                val dy = event.y - lastY
                lastX = event.x
                lastY = event.y
                applyDrag(dx, dy)
                notifyCrop()
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                active = Handle.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun hitTest(x: Float, y: Float): Handle {
        fun near(px: Float, py: Float) = hypot(x - px, y - py) <= touchSlop
        return when {
            near(cropRect.left, cropRect.top) -> Handle.TL
            near(cropRect.right, cropRect.top) -> Handle.TR
            near(cropRect.left, cropRect.bottom) -> Handle.BL
            near(cropRect.right, cropRect.bottom) -> Handle.BR
            near(cropRect.left, cropRect.centerY()) -> Handle.L
            near(cropRect.right, cropRect.centerY()) -> Handle.R
            near(cropRect.centerX(), cropRect.top) -> Handle.T
            near(cropRect.centerX(), cropRect.bottom) -> Handle.B
            cropRect.contains(x, y) -> Handle.MOVE
            else -> Handle.NONE
        }
    }

    private fun applyDrag(dx: Float, dy: Float) {
        val iw = imageBounds.width().coerceAtLeast(1f)
        val ih = imageBounds.height().coerceAtLeast(1f)
        val ndx = dx / iw
        val ndy = dy / ih
        val minSize = 0.08f

        when (active) {
            Handle.MOVE -> {
                var l = cropLeft + ndx
                var t = cropTop + ndy
                var r = cropRight + ndx
                var b = cropBottom + ndy
                val w = r - l
                val h = b - t
                if (l < 0f) { l = 0f; r = w }
                if (t < 0f) { t = 0f; b = h }
                if (r > 1f) { r = 1f; l = 1f - w }
                if (b > 1f) { b = 1f; t = 1f - h }
                cropLeft = l; cropTop = t; cropRight = r; cropBottom = b
            }
            Handle.L, Handle.TL, Handle.BL -> {
                cropLeft = min(cropLeft + ndx, cropRight - minSize).coerceAtLeast(0f)
            }
            Handle.R, Handle.TR, Handle.BR -> {
                cropRight = max(cropRight + ndx, cropLeft + minSize).coerceAtMost(1f)
            }
            else -> {}
        }
        when (active) {
            Handle.T, Handle.TL, Handle.TR -> {
                cropTop = min(cropTop + ndy, cropBottom - minSize).coerceAtLeast(0f)
            }
            Handle.B, Handle.BL, Handle.BR -> {
                cropBottom = max(cropBottom + ndy, cropTop + minSize).coerceAtMost(1f)
            }
            else -> {}
        }
    }

    private fun notifyCrop() {
        onCropChanged?.invoke(cropLeft, cropTop, cropRight, cropBottom)
    }
}
