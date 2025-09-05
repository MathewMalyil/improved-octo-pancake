package com.mmalyil.smartdocai.ui.walkthrough
// com/mmalyil/smartdocai/ui/walkthrough/SpotlightOverlay.kt


import android.content.Context
import android.graphics.*
import android.view.View

data class SpotlightTarget(val rect: RectF, val tip: String)

class SpotlightOverlay(
    context: Context,
    private val steps: List<SpotlightTarget>,
    private val onFinish: () -> Unit
) : View(context) {

    private val scrimPaint = Paint().apply { color = 0xCC000000.toInt() }
    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        isAntiAlias = true
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE; textSize = 40f; isAntiAlias = true
    }
    private val tipBgPaint = Paint().apply { color = 0xFF222222.toInt() }

    private var index = 0
    private lateinit var layerBitmap: Bitmap
    private lateinit var layerCanvas: Canvas

    init {
        isClickable = true
        setOnClickListener {
            if (index < steps.lastIndex) { index++; invalidate() } else { onFinish() }
        }
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (::layerBitmap.isInitialized) layerBitmap.recycle()
        layerBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        layerCanvas = Canvas(layerBitmap)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // draw scrim
        layerBitmap.eraseColor(Color.TRANSPARENT)
        layerCanvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        // spotlight hole (rounded rect)
        val r = steps[index].rect
        layerCanvas.drawRoundRect(r, 24f, 24f, clearPaint)

        // blit
        canvas.drawBitmap(layerBitmap, 0f, 0f, null)

        // tip bubble
        val padding = 24f
        val tip = steps[index].tip
        val textWidth = textPaint.measureText(tip)
        val textHeight = textPaint.fontMetrics.bottom - textPaint.fontMetrics.top
        val bubbleLeft = r.left
        val bubbleTop = (r.bottom + 16f).coerceAtMost(height - textHeight - 2 * padding)
        val bw = (textWidth + 2 * padding)
        val bh = (textHeight + 2 * padding)

        val bubbleRect = RectF(
            bubbleLeft,
            bubbleTop,
            (bubbleLeft + bw).coerceAtMost(width.toFloat() - 16f),
            bubbleTop + bh
        )
        canvas.drawRoundRect(bubbleRect, 16f, 16f, tipBgPaint)
        canvas.drawText(
            tip,
            bubbleRect.left + padding,
            bubbleRect.top + padding - textPaint.fontMetrics.top,
            textPaint
        )
    }
}