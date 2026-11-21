package com.mmalyil.smartdocai.scan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View

/**
 * Notes-style live page highlight: filled yellow/gold shade over the detected
 * document (not a line-only outline), with a soft gold stroke that brightens
 * when the hold is ready.
 */
class EdgeOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var quad: Array<PointF>? = null
    private var ready = false

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        // ~45% gold — readable shade over the page without hiding content
        color = 0x73F0C23A.toInt()
    }
    private val fillReadyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x88F5C542.toInt()
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
        strokeJoin = Paint.Join.ROUND
        color = 0xE6E8B84A.toInt()
    }
    private val strokeReadyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * resources.displayMetrics.density
        strokeJoin = Paint.Join.ROUND
        color = 0xFFF5C542.toInt()
    }
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFF5C542.toInt()
    }
    private val path = Path()

    fun setQuad(points: Array<PointF>?, stable: Boolean) {
        quad = points
        ready = stable
        postInvalidateOnAnimation()
    }

    fun clear() {
        quad = null
        ready = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val q = quad ?: return
        if (q.size < 4 || width == 0 || height == 0) return
        path.reset()
        path.moveTo(q[0].x * width, q[0].y * height)
        for (i in 1 until 4) {
            path.lineTo(q[i].x * width, q[i].y * height)
        }
        path.close()
        canvas.drawPath(path, if (ready) fillReadyPaint else fillPaint)
        canvas.drawPath(path, if (ready) strokeReadyPaint else strokePaint)
        val r = 5.5f * resources.displayMetrics.density
        for (i in 0 until 4) {
            canvas.drawCircle(q[i].x * width, q[i].y * height, r, cornerPaint)
        }
    }
}
