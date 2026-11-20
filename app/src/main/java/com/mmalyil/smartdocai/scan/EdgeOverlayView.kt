package com.mmalyil.smartdocai.scan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View

/**
 * Draws a live document-edge highlight quad over [androidx.camera.view.PreviewView].
 */
class EdgeOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var quad: Array<PointF>? = null
    private var locked = false

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x33C8A04A
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * resources.displayMetrics.density
        color = 0xFFC8A04A.toInt()
    }
    private val lockedStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.5f * resources.displayMetrics.density
        color = 0xFF2E7D32.toInt()
    }
    private val path = Path()

    fun setQuad(points: Array<PointF>?, stable: Boolean) {
        quad = points
        locked = stable
        postInvalidateOnAnimation()
    }

    fun clear() {
        quad = null
        locked = false
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
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, if (locked) lockedStroke else strokePaint)
    }
}
