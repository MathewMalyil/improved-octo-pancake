package com.mmalyil.smartdocai.scan

import android.graphics.PointF
import android.util.Log
import kotlin.math.abs

/**
 * Temporal smoother + patient auto-capture gate.
 *
 * Auto fires only when the overlay is visible, the score is confident, geometry
 * fills a sensible page fraction, and the quad has stayed stable for a sustained
 * hold — not on the first flash of a hit or while the phone is moving.
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
    /** Consecutive stable+confident frames — primary patient auto gate. */
    private var stableHoldStreak = 0
    private var stableSinceMs = 0L

    fun reset() {
        smooth = null
        smoothScore = 0f
        visible = false
        hitStreak = 0
        missStreak = 0
        stableHoldStreak = 0
        stableSinceMs = 0L
    }

    fun onFrame(raw: DocumentEdgeDetector.Detection?, nowMs: Long = System.currentTimeMillis()): FrameResult {
        if (raw == null || raw.score < SHOW_SCORE) {
            missStreak++
            hitStreak = 0
            // Movement / miss resets patient hold quickly
            stableHoldStreak = (stableHoldStreak - 3).coerceAtLeast(0)
            if (stableHoldStreak == 0) stableSinceMs = 0L
            smoothScore *= 0.88f
            if (missStreak >= MISS_CLEAR || smoothScore < HIDE_SCORE) {
                visible = false
                smooth = null
                smoothScore = 0f
                stableHoldStreak = 0
                stableSinceMs = 0L
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
                ready = visible && smoothScore >= READY_SCORE,
                holdProgress = holdProgress(nowMs),
                holdNeeded = HOLD_FRAMES
            )
        }

        missStreak = 0
        hitStreak++
        val incoming = copyQuad(raw.quad)!!

        val prev = smooth
        var frameIou = 1f
        if (prev == null) {
            smooth = incoming
            smoothScore = raw.score
        } else {
            frameIou = DocumentEdgeDetector.iou(prev, incoming)
            if (frameIou < JUMP_REJECT) {
                // Big jump / motion — soft follow, don't let score spike for lock
                blendInto(prev, incoming, alpha = 0.14f)
                smoothScore = smoothScore * 0.75f + raw.score * 0.25f
            } else {
                blendInto(prev, incoming, alpha = SMOOTH_ALPHA)
                smoothScore = smoothScore * (1f - SCORE_ALPHA) + raw.score * SCORE_ALPHA
            }
        }

        val smoothed = smooth!!

        if (!visible && hitStreak >= ENTER_NEED && smoothScore >= SHOW_SCORE) {
            visible = true
            Log.i(TAG, "overlay VISIBLE score=${"%.2f".format(smoothScore)}")
        }

        val areaOk = pageAreaOk(smoothed)
        // Patient gate: high confidence + stable geometry + sensible page fill
        val confident = smoothScore >= LOCK_SCORE && raw.score >= LOCK_SCORE && areaOk
        val stable = frameIou >= STABLE_IOU
        if (visible && confident && stable) {
            if (stableHoldStreak == 0) stableSinceMs = nowMs
            stableHoldStreak++
        } else if (visible && confident) {
            // Jitter — soft decay, don't wipe entirely on one frame
            stableHoldStreak = (stableHoldStreak - 2).coerceAtLeast(0)
            if (stableHoldStreak == 0) stableSinceMs = 0L
        } else {
            // Low confidence, partial page, or lost — reset hold (no hurried fire)
            stableHoldStreak = 0
            stableSinceMs = 0L
        }

        val locked = checkLocked(nowMs)
        if (locked) {
            Log.i(
                TAG,
                "LOCK ready frames=$stableHoldStreak ms=${nowMs - stableSinceMs} " +
                    "score=${"%.2f".format(smoothScore)} raw=${"%.2f".format(raw.score)} " +
                    "iou=${"%.2f".format(frameIou)} area=${"%.2f".format(quadArea(smoothed))}"
            )
        }

        return FrameResult(
            displayQuad = if (visible) copyQuad(smoothed) else null,
            smoothedScore = smoothScore,
            visible = visible,
            lockedForCapture = locked,
            ready = visible && smoothScore >= READY_SCORE && areaOk,
            holdProgress = holdProgress(nowMs),
            holdNeeded = HOLD_FRAMES
        )
    }

    private fun checkLocked(nowMs: Long): Boolean {
        if (!visible || smoothScore < LOCK_SCORE) return false
        val q = smooth ?: return false
        if (!pageAreaOk(q)) return false
        val heldMs = if (stableSinceMs > 0L) nowMs - stableSinceMs else 0L
        // Require sustained stable frames; wall-clock alone is not enough
        return stableHoldStreak >= HOLD_FRAMES && heldMs >= HOLD_MS_MIN
    }

    private fun holdProgress(nowMs: Long): Int {
        if (!visible) return 0
        return stableHoldStreak.coerceAtMost(HOLD_FRAMES)
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

        /** Need a few solid hits before showing / starting the clock. */
        private const val ENTER_NEED = 4
        private const val MISS_CLEAR = 14
        private const val SHOW_SCORE = 0.40f
        private const val HIDE_SCORE = 0.28f

        /**
         * Patient auto: ~2.4–2.8s of stable + high-confidence frames at ~8 fps.
         * Wall-clock minimum prevents hurried fire on a burst of hits.
         */
        const val HOLD_FRAMES = 20
        const val HOLD_MS_MIN = 2400L

        const val READY_SCORE = 0.55f
        /** Auto requires this smoothed + raw confidence (high only). */
        const val LOCK_SCORE = 0.72f
        const val LOCK_NEED = HOLD_FRAMES

        /** Quad must stay firmly still between frames. */
        private const val STABLE_IOU = 0.92f

        /** Reject partial / near-fullscreen desk hits from locking. */
        private const val MIN_LOCK_AREA = 0.18f
        private const val MAX_LOCK_AREA = 0.88f

        /** Lower = less outline jitter (laggy but calmer). */
        private const val SMOOTH_ALPHA = 0.16f
        private const val SCORE_ALPHA = 0.20f
        /** Below this IoU → treat as motion jump (soft blend only). */
        private const val JUMP_REJECT = 0.42f

        private fun quadArea(q: Array<PointF>): Float {
            var sum = 0f
            for (i in 0..3) {
                val j = (i + 1) % 4
                sum += q[i].x * q[j].y - q[j].x * q[i].y
            }
            return abs(sum) * 0.5f
        }

        private fun pageAreaOk(q: Array<PointF>): Boolean {
            val a = quadArea(q)
            return a in MIN_LOCK_AREA..MAX_LOCK_AREA
        }
    }
}
