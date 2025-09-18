package com.mmalyil.smartdocai.ui.walkthrough

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

data class SpotlightTarget(val rect: RectF, val tip: String)

class SpotlightOverlay(
    context: Context,
    private val steps: List<SpotlightTarget>,
    private val onFinish: () -> Unit
) : View(context) {

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC000000.toInt() }
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val tipBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF222222.toInt() }
    private val tipText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 40f }

    private var index = 0
    private lateinit var layerBmp: Bitmap
    private lateinit var layerCvs: Canvas

    init {
        visibility = GONE
        isClickable = false
        isFocusable = false
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun show() {
        if (steps.isEmpty()) return
        visibility = VISIBLE
        isClickable = true
        isFocusable = true
        bringToFront()
        requestLayout()
        invalidate()
    }

    fun hide() {
        visibility = GONE
        isClickable = false
        isFocusable = false
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (::layerBmp.isInitialized) layerBmp.recycle()
        if (w > 0 && h > 0) {
            layerBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            layerCvs = Canvas(layerBmp)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!::layerBmp.isInitialized || steps.isEmpty()) return

        layerBmp.eraseColor(Color.TRANSPARENT)
        layerCvs.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        val r = steps[index].rect
        layerCvs.drawRoundRect(r, 24f, 24f, clearPaint)
        canvas.drawBitmap(layerBmp, 0f, 0f, null)

        val tip = steps[index].tip
        if (tip.isNotEmpty()) {
            val pad = 24f
            val fm = tipText.fontMetrics
            val textH = fm.bottom - fm.top
            val textW = tipText.measureText(tip)
            val left = r.left
            val top = (r.bottom + 16f).coerceAtMost(height - textH - 2 * pad)
            val bubble = RectF(
                left,
                top,
                (left + textW + 2 * pad).coerceAtMost(width - 16f),
                top + textH + 2 * pad
            )
            canvas.drawRoundRect(bubble, 16f, 16f, tipBg)
            canvas.drawText(tip, bubble.left + pad, bubble.top + pad - fm.top, tipText)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (visibility != VISIBLE || steps.isEmpty()) return false
        if (event.action != MotionEvent.ACTION_UP) return true
        if (index < steps.lastIndex) {
            index++
            invalidate()
        } else {
            onFinish()
        }
        return true
    }
}