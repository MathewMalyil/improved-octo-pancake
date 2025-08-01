package com.mmalyil.smartdocai


import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class PalmOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val paint = Paint().apply {
        color = Color.CYAN
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }

    private var linesToDraw: List<Pair<PointF, PointF>> = emptyList()

    fun setLines(lines: List<Pair<PointF, PointF>>) {
        linesToDraw = lines
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (line in linesToDraw) {
            canvas.drawLine(line.first.x, line.first.y, line.second.x, line.second.y, paint)
        }
    }
}