package com.mmalyil.smartdocai.scan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Dimmed full-frame with a landscape ID/Aadhaar card cutout (~85.6×54 mm aspect).
 * Enough guidance when full page-edge detect is awkward on a small card.
 */
class CardGuideOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Standard ID-1 / Aadhaar landscape aspect (width / height). */
    private val cardAspect = 85.6f / 54f

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x99000000.toInt()
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
        color = 0xFFF5C542.toInt()
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF7F0E0.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 14f * resources.displayMetrics.scaledDensity
    }

    private val cardRect = RectF()
    private val dimPath = Path()
    private var sideLabel: String = "Front of ID"

    fun setSideLabel(label: String) {
        sideLabel = label
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val padH = width * 0.08f
        val maxW = width - padH * 2f
        val maxH = height * 0.42f
        var cardW = maxW
        var cardH = cardW / cardAspect
        if (cardH > maxH) {
            cardH = maxH
            cardW = cardH * cardAspect
        }
        val left = (width - cardW) / 2f
        val top = (height - cardH) / 2f - height * 0.04f
        cardRect.set(left, top, left + cardW, top + cardH)

        val radius = 12f * resources.displayMetrics.density
        dimPath.reset()
        dimPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        dimPath.addRoundRect(cardRect, radius, radius, Path.Direction.CCW)
        dimPath.fillType = Path.FillType.EVEN_ODD
        canvas.drawPath(dimPath, dimPaint)
        canvas.drawRoundRect(cardRect, radius, radius, strokePaint)

        canvas.drawText(
            sideLabel,
            width / 2f,
            (cardRect.top - 14f * resources.displayMetrics.density).coerceAtLeast(
                28f * resources.displayMetrics.density
            ),
            labelPaint
        )
    }
}
