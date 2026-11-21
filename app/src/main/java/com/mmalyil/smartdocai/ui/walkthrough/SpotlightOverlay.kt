package com.mmalyil.smartdocai.ui.walkthrough

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

data class SpotlightTarget(val rect: RectF, val tip: String)

/**
 * Full-screen coach scrim.
 *
 * 3.19.9 hide+post-detach was not enough on device: a blank transparent
 * click-eater could remain after the last tip. Teardown must disable hit-testing
 * and detach from the parent before invoking [onFinish].
 */
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
    private var finished = false
    private var finishPosted = false
    private lateinit var layerBmp: Bitmap
    private lateinit var layerCvs: Canvas

    init {
        // Stable a11y marker for adb / uiautomator proofs
        contentDescription = CONTENT_DESC
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        visibility = GONE
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        isEnabled = false
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun show() {
        if (steps.isEmpty() || finished) return
        visibility = VISIBLE
        alpha = 1f
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
        isEnabled = true
        bringToFront()
        requestLayout()
        invalidate()
    }

    fun hide() {
        visibility = GONE
        alpha = 0f
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        isEnabled = false
    }

    /**
     * Disable hit-testing, clear listeners/callbacks, GONE, detach from parent.
     * Safe to call more than once.
     */
    fun dismissCompletely() {
        finished = true
        setOnTouchListener(null)
        setOnClickListener(null)
        setOnLongClickListener(null)
        setOnKeyListener(null)
        hide()
        setLayerType(LAYER_TYPE_NONE, null)
        // Drop any queued show/invalidate work on this view
        handler?.removeCallbacksAndMessages(null)
        val p = parent as? ViewGroup
        if (p != null) {
            p.removeView(this)
        }
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
        if (finished || visibility != VISIBLE) return
        if (!::layerBmp.isInitialized || steps.isEmpty()) return
        if (index !in steps.indices) return

        layerBmp.eraseColor(Color.TRANSPARENT)
        layerCvs.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        val r = steps[index].rect
        layerCvs.drawRoundRect(r, 24f, 24f, clearPaint)
        canvas.drawBitmap(layerBmp, 0f, 0f, null)

        val tip = steps[index].tip
        if (tip.isNotEmpty()) {
            val pad = 24f
            val fm = tipText.fontMetrics
            val lineH = fm.bottom - fm.top
            val maxBubbleW = (width - 32f).coerceAtLeast(120f)
            val maxTextW = maxBubbleW - 2 * pad
            val lines = wrapTipLines(tip, tipText, maxTextW)
            val textBlockH = lineH * lines.size
            val textBlockW = lines.maxOf { tipText.measureText(it) }
            val left = r.left.coerceIn(16f, (width - textBlockW - 2 * pad - 16f).coerceAtLeast(16f))
            var top = r.bottom + 16f
            if (top + textBlockH + 2 * pad > height - 16f) {
                top = (r.top - textBlockH - 2 * pad - 16f).coerceAtLeast(16f)
            }
            val bubble = RectF(
                left,
                top,
                (left + textBlockW + 2 * pad).coerceAtMost(width - 16f),
                top + textBlockH + 2 * pad
            )
            canvas.drawRoundRect(bubble, 16f, 16f, tipBg)
            var y = bubble.top + pad - fm.top
            for (line in lines) {
                canvas.drawText(line, bubble.left + pad, y, tipText)
                y += lineH
            }
        }
    }

    /** Simple wrap so long coach tips stay readable (no layout rewrite). */
    private fun wrapTipLines(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return emptyList()
        if (paint.measureText(text) <= maxWidth) return listOf(text)
        val words = text.split(' ')
        val lines = ArrayList<String>()
        var cur = StringBuilder()
        for (w in words) {
            val candidate = if (cur.isEmpty()) w else "$cur $w"
            if (paint.measureText(candidate) <= maxWidth) {
                cur = StringBuilder(candidate)
            } else {
                if (cur.isNotEmpty()) lines.add(cur.toString())
                cur = StringBuilder(w)
            }
        }
        if (cur.isNotEmpty()) lines.add(cur.toString())
        return lines.ifEmpty { listOf(text) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Never eat touches after finish — even if still attached for a frame.
        if (finished || visibility != VISIBLE || !isClickable || steps.isEmpty()) return false
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> true
            MotionEvent.ACTION_CANCEL -> {
                // Treat cancel like a no-op advance; still consume while active.
                true
            }
            MotionEvent.ACTION_UP -> {
                if (index < steps.lastIndex) {
                    index++
                    invalidate()
                } else {
                    finishFromTouch()
                }
                true
            }
            else -> true
        }
    }

    private fun finishFromTouch() {
        if (finishPosted) return
        finishPosted = true
        // Tear down NOW (before UP dispatch completes) so nothing transparent
        // remains to catch the next tap. Host callback runs next frame on a
        // still-living view (decor / main looper) — not on this detached view.
        dismissCompletely()
        val cb = onFinish
        val activity = context.findActivity()
        val runner = Runnable {
            try {
                cb()
            } catch (_: Exception) {
            }
        }
        when {
            activity?.window?.decorView?.post(runner) == true -> Unit
            else -> Handler(Looper.getMainLooper()).post(runner)
        }
    }

    companion object {
        const val CONTENT_DESC = "spotlight_coach_overlay"

        /** Strip any [SpotlightOverlay] direct children (and one nested level). */
        fun stripFrom(root: ViewGroup?) {
            if (root == null) return
            for (i in root.childCount - 1 downTo 0) {
                when (val c = root.getChildAt(i)) {
                    is SpotlightOverlay -> c.dismissCompletely()
                    is ViewGroup -> {
                        for (j in c.childCount - 1 downTo 0) {
                            val inner = c.getChildAt(j)
                            if (inner is SpotlightOverlay) inner.dismissCompletely()
                        }
                    }
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
