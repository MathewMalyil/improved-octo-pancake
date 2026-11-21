package com.mmalyil.smartdocai.util

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.mmalyil.smartdocai.BillingActivity
import java.util.Calendar

/**
 * Document Pro entitlement + soft free daily caps.
 * Free AI tokens remain in [UsageManager] (separate).
 *
 * Pro unlock via lifetime INAPP or yearly/monthly subscription — all set
 * [isDocumentPro] true (unlimited tools, ad-free, clean export).
 */
object DocumentEntitlement {

    private const val PREF = "document_pro_prefs"
    private const val KEY_IS_DOCUMENT_PRO = "isDocumentPro"
    private const val KEY_PRO_SOURCE = "proSource"
    private const val KEY_DAY = "cap_day" // yyyyMMdd

    enum class ProSource {
        NONE, LIFETIME, YEARLY, MONTHLY
    }

    enum class Feature(val key: String, val dailyFreeLimit: Int, val label: String) {
        IMAGE_TO_EXCEL("img_excel", 3, "Image → Excel"),
        SCAN_TO_PDF("scan_pdf", 2, "Multi-page scan → PDF"),
        AI_REPORT("ai_report", 2, "Excel ↔ Word / AI report"),
        OTHER_EXPORT("other_export", 5, "Other exports"),
        /** Clean PDF Share / Save to Downloads (no free watermark). */
        CLEAN_SHARE("clean_share", 2, "Clean PDF share / Downloads"),
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun isDocumentPro(context: Context): Boolean =
        prefs(context).getBoolean(KEY_IS_DOCUMENT_PRO, false)

    fun proSource(context: Context): ProSource {
        val raw = prefs(context).getString(KEY_PRO_SOURCE, ProSource.NONE.name) ?: ProSource.NONE.name
        return try {
            ProSource.valueOf(raw)
        } catch (_: Exception) {
            if (isDocumentPro(context)) ProSource.LIFETIME else ProSource.NONE
        }
    }

    fun setDocumentPro(
        context: Context,
        value: Boolean,
        source: ProSource = if (value) ProSource.LIFETIME else ProSource.NONE
    ) {
        prefs(context).edit()
            .putBoolean(KEY_IS_DOCUMENT_PRO, value)
            .putString(KEY_PRO_SOURCE, if (value) source.name else ProSource.NONE.name)
            .apply()
    }

    /** Free users get a light footer watermark on Save PDF; Pro is clean. */
    fun shouldWatermarkPdf(context: Context): Boolean = !isDocumentPro(context)

    private fun todayKey(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR) * 10_000 +
            (c.get(Calendar.MONTH) + 1) * 100 +
            c.get(Calendar.DAY_OF_MONTH)
    }

    private fun ensureDay(p: SharedPreferences) {
        val today = todayKey()
        if (p.getInt(KEY_DAY, 0) != today) {
            val ed = p.edit().putInt(KEY_DAY, today)
            Feature.entries.forEach { ed.putInt(it.key, 0) }
            ed.apply()
        }
    }

    fun usedToday(context: Context, feature: Feature): Int {
        val p = prefs(context)
        ensureDay(p)
        return p.getInt(feature.key, 0)
    }

    fun remainingToday(context: Context, feature: Feature): Int {
        if (isDocumentPro(context)) return Int.MAX_VALUE
        return (feature.dailyFreeLimit - usedToday(context, feature)).coerceAtLeast(0)
    }

    /** Returns true if the user may perform the action (Pro or under free cap). */
    fun canUse(context: Context, feature: Feature): Boolean {
        if (isDocumentPro(context)) return true
        return usedToday(context, feature) < feature.dailyFreeLimit
    }

    fun recordUse(context: Context, feature: Feature) {
        if (isDocumentPro(context)) return
        val p = prefs(context)
        ensureDay(p)
        p.edit().putInt(feature.key, p.getInt(feature.key, 0) + 1).apply()
    }

    fun freeLimitMessage(context: Context, feature: Feature): String {
        val used = usedToday(context, feature)
        return "${feature.label}: free limit reached ($used/${feature.dailyFreeLimit} today). " +
            "Unlock Document Pro for unlimited tools, ad-free app, and clean PDF export."
    }

    fun openPaywall(context: Context, reason: String? = null) {
        val i = Intent(context, BillingActivity::class.java).apply {
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (!reason.isNullOrBlank()) putExtra("message", reason)
        }
        context.startActivity(i)
    }

    /**
     * If allowed, runs [onAllowed] (caller should [recordUse] after success).
     * If blocked, opens paywall.
     */
    fun requireOrPaywall(
        context: Context,
        feature: Feature,
        onAllowed: () -> Unit
    ) {
        if (canUse(context, feature)) {
            onAllowed()
        } else {
            openPaywall(context, freeLimitMessage(context, feature))
        }
    }

    /**
     * High-value Share / Save to Downloads gate.
     * Pro or under clean-share cap → [onAllowed].
     * Exhausted → paywall (caller may offer watermarked share separately).
     */
    fun requireCleanShareOrPaywall(context: Context, onAllowed: () -> Unit) {
        requireOrPaywall(context, Feature.CLEAN_SHARE, onAllowed)
    }
}
