package com.mmalyil.smartdocai.scan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/**
 * Notes-style review crop: filled gold shade + four independently draggable
 * corner dots (TL, TR, BR, BL) in image-normalized coordinates.
 */
class QuadAdjustOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var imageBounds: RectF = RectF()
        set(value) {
            field = value
            invalidate()
        }

    var onQuadChanged: ((Array<PointF>) -> Unit)? = null

    /** TL, TR, BR, BL normalized to the image. */
    private var quad: Array<PointF> = defaultQuad()
    private var activeCorner = -1
    private val touchSlop = 32f * resources.displayMetrics.density

    private val dimPaint = Paint().apply { color = 0x99000000.toInt() }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x66F0C23A.toInt()
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
        color = 0xFFF5C542.toInt()
    }
    private val cornerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFF5C542.toInt()
    }
    private val cornerStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        color = 0xFFFFFFFF.toInt()
    }
    private val path = Path()
    private val screenPts = Array(4) { PointF() }

    fun setNormalizedQuad(points: Array<PointF>) {
        if (points.size < 4) return
        quad = Array(4) { i ->
            PointF(points[i].x.coerceIn(0f, 1f), points[i].y.coerceIn(0f, 1f))
        }
        invalidate()
    }

    fun getNormalizedQuad(): Array<PointF> =
        Array(4) { PointF(quad[it].x, quad[it].y) }

    fun resetToFull() {
        setNormalizedQuad(defaultQuad())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (imageBounds.isEmpty) return
        updateScreenPts()

        path.reset()
        path.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        path.moveTo(screenPts[0].x, screenPts[0].y)
        for (i in 1 until 4) path.lineTo(screenPts[i].x, screenPts[i].y)
        path.close()
        canvas.drawPath(path, dimPaint)

        path.reset()
        path.moveTo(screenPts[0].x, screenPts[0].y)
        for (i in 1 until 4) path.lineTo(screenPts[i].x, screenPts[i].y)
        path.close()
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)

        val r = 11f * resources.displayMetrics.density
        for (p in screenPts) {
            canvas.drawCircle(p.x, p.y, r, cornerFill)
            canvas.drawCircle(p.x, p.y, r, cornerStroke)
        }
    }

    private fun updateScreenPts() {
        for (i in 0 until 4) {
            screenPts[i].x = imageBounds.left + quad[i].x * imageBounds.width()
            screenPts[i].y = imageBounds.top + quad[i].y * imageBounds.height()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (imageBounds.isEmpty) return false
        updateScreenPts()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeCorner = hitCorner(event.x, event.y)
                return activeCorner >= 0
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeCorner < 0) return false
                val nx = ((event.x - imageBounds.left) / imageBounds.width()).coerceIn(0.02f, 0.98f)
                val ny = ((event.y - imageBounds.top) / imageBounds.height()).coerceIn(0.02f, 0.98f)
                quad[activeCorner].x = nx
                quad[activeCorner].y = ny
                onQuadChanged?.invoke(getNormalizedQuad())
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeCorner = -1
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun hitCorner(x: Float, y: Float): Int {
        var best = -1
        var bestD = touchSlop
        for (i in 0 until 4) {
            val d = hypot(x - screenPts[i].x, y - screenPts[i].y)
            if (d <= bestD) {
                bestD = d
                best = i
            }
        }
        return best
    }

    companion object {
        fun defaultQuad(): Array<PointF> = arrayOf(
            PointF(0.08f, 0.08f),
            PointF(0.92f, 0.08f),
            PointF(0.92f, 0.92f),
            PointF(0.08f, 0.92f)
        )
    }
}
