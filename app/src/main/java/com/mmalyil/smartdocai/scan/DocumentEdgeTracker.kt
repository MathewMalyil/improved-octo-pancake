package com.mmalyil.smartdocai.scan

import android.graphics.PointF

/**
 * Temporal smoother + hysteresis for live document quads.
 * Keeps the overlay from flickering and gates auto-capture until genuinely stable.
 */
class DocumentEdgeTracker {

    data class FrameResult(
        /** Smoothed quad to draw (null = hide overlay). */
        val displayQuad: Array<PointF>?,
        val smoothedScore: Float,
        /** True when outline is shown (past enter hysteresis). */
        val visible: Boolean,
        /** True when lock criteria met long enough for auto-capture. */
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
    private var lockStreak = 0

    fun reset() {
        smooth = null
        smoothScore = 0f
        visible = false
        hitStreak = 0
        missStreak = 0
        lockStreak = 0
    }

    fun onFrame(raw: DocumentEdgeDetector.Detection?): FrameResult {
        if (raw == null || raw.score < SHOW_SCORE) {
            missStreak++
            hitStreak = 0
            lockStreak = (lockStreak - 1).coerceAtLeast(0)
            // Decay score; keep last smooth briefly (hysteresis)
            smoothScore *= 0.88f
            if (missStreak >= MISS_CLEAR || smoothScore < HIDE_SCORE) {
                visible = false
                smooth = null
                smoothScore = 0f
                lockStreak = 0
                return FrameResult(
                    displayQuad = null,
                    smoothedScore = 0f,
                    visible = false,
                    lockedForCapture = false,
                    ready = false,
                    holdProgress = 0,
                    holdNeeded = LOCK_NEED
                )
            }
            // Keep showing faded last quad without advancing lock
            return FrameResult(
                displayQuad = if (visible) copyQuad(smooth) else null,
                smoothedScore = smoothScore,
                visible = visible,
                lockedForCapture = false,
                ready = visible && smoothScore >= READY_SCORE,
                holdProgress = lockStreak.coerceAtMost(LOCK_NEED),
                holdNeeded = LOCK_NEED
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
                // Sudden jump — treat as miss for lock, light blend only
                lockStreak = (lockStreak - 1).coerceAtLeast(0)
                blendInto(prev, incoming, alpha = 0.18f)
                smoothScore = smoothScore * 0.7f + raw.score * 0.3f
            } else {
                blendInto(prev, incoming, alpha = SMOOTH_ALPHA)
                smoothScore = smoothScore * (1f - SCORE_ALPHA) + raw.score * SCORE_ALPHA
            }
        }

        val smoothed = smooth!!

        if (!visible && hitStreak >= ENTER_NEED && smoothScore >= SHOW_SCORE) {
            visible = true
        }

        val iouToSmooth = if (prev != null) DocumentEdgeDetector.iou(prev, incoming) else 1f
        val lockOk = visible &&
            smoothScore >= LOCK_SCORE &&
            raw.score >= LOCK_SCORE * 0.92f &&
            iouToSmooth >= LOCK_IOU

        if (lockOk) {
            lockStreak++
        } else if (visible && smoothScore >= READY_SCORE && iouToSmooth >= LOCK_IOU * 0.9f) {
            // Near-ready: soft decay so brief wobble doesn't wipe progress
            lockStreak = (lockStreak - 1).coerceAtLeast(0)
        } else {
            lockStreak = (lockStreak - 2).coerceAtLeast(0)
        }

        val locked = lockStreak >= LOCK_NEED
        val ready = visible && smoothScore >= READY_SCORE
        return FrameResult(
            displayQuad = if (visible) copyQuad(smoothed) else null,
            smoothedScore = smoothScore,
            visible = visible,
            lockedForCapture = locked,
            ready = ready,
            holdProgress = lockStreak.coerceAtMost(LOCK_NEED),
            holdNeeded = LOCK_NEED
        )
    }

    /** Last smoothed quad for capture warp (may be null). */
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
        /** Overlay appears only after this many consecutive usable hits. */
        private const val ENTER_NEED = 2
        /** Clear overlay after this many consecutive misses. */
        private const val MISS_CLEAR = 10
        private const val SHOW_SCORE = 0.48f
        private const val HIDE_SCORE = 0.34f
        /** Clear “ready” UI — hold or tap. */
        const val READY_SCORE = 0.52f
        /**
         * Auto-capture lock. 3.14.2 used 0.68 which rarely cleared for white-on-dark
         * pages (typical score ~0.55–0.65). 3.14.4 targets reliable auto in that range.
         */
        const val LOCK_SCORE = 0.56f
        private const val LOCK_IOU = 0.80f
        /** ~1s at ~8 fps analysis cadence. */
        const val LOCK_NEED = 8
        private const val SMOOTH_ALPHA = 0.30f
        private const val SCORE_ALPHA = 0.28f
        /** Reject wild jumps from blending hard. */
        private const val JUMP_REJECT = 0.40f
    }
}
