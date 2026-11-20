package com.mmalyil.smartdocai.scan

import android.graphics.PointF
import android.util.Log

/**
 * Temporal smoother + hysteresis for live document quads.
 *
 * Auto-capture (3.14.5): once the overlay is showing a valid quad, accumulate
 * consecutive visible frames / wall-clock hold — do NOT require a high lock score.
 * Score/IoU only soft-smooth the outline; they no longer gate capture.
 */
class DocumentEdgeTracker {

    data class FrameResult(
        /** Smoothed quad to draw (null = hide overlay). */
        val displayQuad: Array<PointF>?,
        val smoothedScore: Float,
        /** True when outline is shown (past enter hysteresis). */
        val visible: Boolean,
        /** True when hold criteria met — auto-capture should fire now. */
        val lockedForCapture: Boolean,
        /** Outline is good enough — user can hold for auto or tap. */
        val ready: Boolean,
        val holdProgress: Int,
        val holdNeeded: Int
    )

    private var smooth: Array<PointF>? = null
    private var smoothScore = 0f
    private var visible = false
    private var hitStreak = 0
    private var missStreak = 0
    /** Consecutive frames where overlay stays visible — primary auto gate. */
    private var visibleHoldStreak = 0
    private var visibleSinceMs = 0L

    fun reset() {
        smooth = null
        smoothScore = 0f
        visible = false
        hitStreak = 0
        missStreak = 0
        visibleHoldStreak = 0
        visibleSinceMs = 0L
    }

    fun onFrame(raw: DocumentEdgeDetector.Detection?, nowMs: Long = System.currentTimeMillis()): FrameResult {
        if (raw == null || raw.score < SHOW_SCORE) {
            missStreak++
            hitStreak = 0
            // Soft decay on hold — one miss doesn't wipe progress
            visibleHoldStreak = (visibleHoldStreak - 1).coerceAtLeast(0)
            smoothScore *= 0.90f
            if (missStreak >= MISS_CLEAR || smoothScore < HIDE_SCORE) {
                visible = false
                smooth = null
                smoothScore = 0f
                visibleHoldStreak = 0
                visibleSinceMs = 0L
                return FrameResult(
                    displayQuad = null,
                    smoothedScore = 0f,
                    visible = false,
                    lockedForCapture = false,
                    ready = false,
                    holdProgress = 0,
                    holdNeeded = HOLD_FRAMES
                )
            }
            val locked = checkLocked(nowMs)
            return FrameResult(
                displayQuad = if (visible) copyQuad(smooth) else null,
                smoothedScore = smoothScore,
                visible = visible,
                lockedForCapture = locked,
                ready = visible,
                holdProgress = holdProgress(nowMs),
                holdNeeded = HOLD_FRAMES
            )
        }

        missStreak = 0
        hitStreak++
        val incoming = copyQuad(raw.quad)!!

        val prev = smooth
        if (prev == null) {
            smooth = incoming
            smoothScore = raw.score
        } else {
            val iou = DocumentEdgeDetector.iou(prev, incoming)
            if (iou < JUMP_REJECT) {
                blendInto(prev, incoming, alpha = 0.22f)
                smoothScore = smoothScore * 0.65f + raw.score * 0.35f
            } else {
                blendInto(prev, incoming, alpha = SMOOTH_ALPHA)
                smoothScore = smoothScore * (1f - SCORE_ALPHA) + raw.score * SCORE_ALPHA
            }
        }

        val smoothed = smooth!!

        if (!visible && hitStreak >= ENTER_NEED) {
            visible = true
            visibleSinceMs = nowMs
            visibleHoldStreak = 0
            Log.i(TAG, "overlay VISIBLE score=${"%.2f".format(smoothScore)}")
        }

        if (visible) {
            visibleHoldStreak++
            if (visibleSinceMs == 0L) visibleSinceMs = nowMs
        }

        val locked = checkLocked(nowMs)
        if (locked) {
            Log.i(
                TAG,
                "LOCK ready frames=$visibleHoldStreak ms=${nowMs - visibleSinceMs} " +
                    "score=${"%.2f".format(smoothScore)} raw=${"%.2f".format(raw.score)}"
            )
        }

        return FrameResult(
            displayQuad = if (visible) copyQuad(smoothed) else null,
            smoothedScore = smoothScore,
            visible = visible,
            lockedForCapture = locked,
            ready = visible,
            holdProgress = holdProgress(nowMs),
            holdNeeded = HOLD_FRAMES
        )
    }

    private fun checkLocked(nowMs: Long): Boolean {
        if (!visible) return false
        val heldMs = if (visibleSinceMs > 0L) nowMs - visibleSinceMs else 0L
        return visibleHoldStreak >= HOLD_FRAMES || heldMs >= HOLD_MS
    }

    private fun holdProgress(nowMs: Long): Int {
        if (!visible) return 0
        val byFrames = visibleHoldStreak.coerceAtMost(HOLD_FRAMES)
        val heldMs = if (visibleSinceMs > 0L) nowMs - visibleSinceMs else 0L
        val byTime = ((heldMs.toFloat() / HOLD_MS) * HOLD_FRAMES).toInt().coerceIn(0, HOLD_FRAMES)
        return maxOf(byFrames, byTime)
    }

    fun lastSmoothedQuad(): Array<PointF>? = copyQuad(smooth)

    fun lastSmoothedScore(): Float = smoothScore

    private fun blendInto(dst: Array<PointF>, src: Array<PointF>, alpha: Float) {
        for (i in 0..3) {
            dst[i].x = dst[i].x * (1f - alpha) + src[i].x * alpha
            dst[i].y = dst[i].y * (1f - alpha) + src[i].y * alpha
        }
    }

    private fun copyQuad(q: Array<PointF>?): Array<PointF>? {
        if (q == null) return null
        return Array(4) { PointF(q[it].x, q[it].y) }
    }

    companion object {
        private const val TAG = "EdgeTracker"

        /** First usable hit shows overlay and starts the auto hold clock. */
        private const val ENTER_NEED = 1
        private const val MISS_CLEAR = 14
        /** Any detector hit above this can enter the overlay. */
        private const val SHOW_SCORE = 0.38f
        private const val HIDE_SCORE = 0.26f

        /**
         * Auto after this many visible-hold frames OR [HOLD_MS] wall-clock
         * since overlay appeared — whichever first. Time gate matters when
         * live hits are intermittent (camera nulls between page frames).
         */
        const val HOLD_FRAMES = 6
        const val HOLD_MS = 750L

        /** Kept for UI copy that referenced LOCK_SCORE; ready = visible. */
        const val READY_SCORE = 0.40f
        const val LOCK_SCORE = 0.40f
        const val LOCK_NEED = HOLD_FRAMES

        private const val SMOOTH_ALPHA = 0.32f
        private const val SCORE_ALPHA = 0.30f
        private const val JUMP_REJECT = 0.35f
    }
}
