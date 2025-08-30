package com.mmalyil.smartdocai.util

import android.content.Context
import android.content.SharedPreferences
import android.widget.ProgressBar
import android.widget.TextView
import java.util.*

object UsageManager {
    private const val PREF_NAME = "ai_usage"

    // GPT-4o (Pro) main counter (historically used for free too in your app)
    private const val KEY_TOKENS_USED = "tokensUsed"

    // Pro flag
    private const val KEY_PRO_PLAN = "isPro"

    // Monthly reset anchor
    private const val KEY_LAST_RESET = "lastResetTime"

    // GPT-3.5 fallback counter
    private const val KEY_GPT35_USED = "gpt35Used"

    // ✅ New: Groq (free) separate counter
    private const val KEY_GROQ_TOKENS_USED = "groq_tokens_used"

    // Caps
    private const val FREE_TOKEN_CAP = 30_000       // historical free cap (used for non-pro GPT-4o in old logic)
    private const val PRO_TOKEN_CAP = 300_000       // Pro GPT-4o cap
    private const val GPT35_TOKEN_CAP = 300         // hard limit for GPT-3.5 fallback
    private const val GROQ_TOKEN_CAP = 30_000       // Groq free cap

    fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isPro(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_PRO_PLAN, false)

    fun setPro(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PRO_PLAN, value).apply()
    }

    // -----------------------------
    // GPT-4o (Pro) usage (legacy name)
    // -----------------------------
    fun getTokensUsed(context: Context): Int {
        maybeResetMonthly(context)
        return getPrefs(context).getInt(KEY_TOKENS_USED, 0)
    }

    /** Records tokens against the GPT-4o (Pro) bucket. */
    fun recordUsage(context: Context, tokensUsed: Int) {
        maybeResetMonthly(context)
        val prefs = getPrefs(context)
        val current = prefs.getInt(KEY_TOKENS_USED, 0)
        val newTotal = current + tokensUsed
        val cap = if (isPro(context)) PRO_TOKEN_CAP else FREE_TOKEN_CAP
        if (newTotal <= cap) {
            prefs.edit().putInt(KEY_TOKENS_USED, newTotal).apply()
        }
    }

    /** Returns GPT-4o cap if Pro, else legacy FREE cap (kept for backward compatibility). */
    fun getTokenCap(context: Context): Int {
        return if (isPro(context)) PRO_TOKEN_CAP else FREE_TOKEN_CAP
    }

    // -----------------------------
    // ✅ Groq (Free) usage — NEW
    // -----------------------------
    fun getGroqUsed(context: Context): Int {
        maybeResetMonthly(context)
        return getPrefs(context).getInt(KEY_GROQ_TOKENS_USED, 0)
    }

    fun getGroqCap(context: Context): Int = GROQ_TOKEN_CAP

    fun recordGroqUsage(context: Context, tokens: Int) {
        maybeResetMonthly(context)
        val prefs = getPrefs(context)
        val current = prefs.getInt(KEY_GROQ_TOKENS_USED, 0)
        prefs.edit().putInt(KEY_GROQ_TOKENS_USED, (current + tokens).coerceAtLeast(0)).apply()
    }

    fun resetGroqUsage(context: Context) {
        getPrefs(context).edit().putInt(KEY_GROQ_TOKENS_USED, 0).apply()
    }

    // -----------------------------
    // GPT-3.5 fallback usage
    // -----------------------------
    fun getGpt35Used(context: Context): Int {
        maybeResetMonthly(context)
        return getPrefs(context).getInt(KEY_GPT35_USED, 0)
    }

    fun incrementGpt35Usage(context: Context, tokens: Int) {
        maybeResetMonthly(context)
        val prefs = getPrefs(context)
        val current = prefs.getInt(KEY_GPT35_USED, 0)
        val newTotal = (current + tokens).coerceAtMost(GPT35_TOKEN_CAP)
        prefs.edit().putInt(KEY_GPT35_USED, newTotal).apply()
    }

    fun getGpt35Cap(): Int = GPT35_TOKEN_CAP

    // -----------------------------
    // Resets & monthly rollover
    // -----------------------------
    fun resetUsage(context: Context) {
        getPrefs(context).edit()
            .putInt(KEY_TOKENS_USED, 0)        // GPT-4o bucket
            .putInt(KEY_GPT35_USED, 0)         // GPT-3.5
            .putInt(KEY_GROQ_TOKENS_USED, 0)   // Groq
            .putLong(KEY_LAST_RESET, System.currentTimeMillis())
            .apply()
    }

    private fun maybeResetMonthly(context: Context) {
        val prefs = getPrefs(context)
        val lastReset = prefs.getLong(KEY_LAST_RESET, 0L)

        val last = Calendar.getInstance().apply { timeInMillis = lastReset }
        val now = Calendar.getInstance()

        val lastMonth = last.get(Calendar.MONTH)
        val lastYear = last.get(Calendar.YEAR)
        val curMonth = now.get(Calendar.MONTH)
        val curYear = now.get(Calendar.YEAR)

        if (lastMonth != curMonth || lastYear != curYear) {
            resetUsage(context)
        }
    }

    // -----------------------------
    // UI binding
    // -----------------------------
    fun bindUsageUI(
        context: Context,
        usageTextView: TextView,
        progressBar: ProgressBar,
        aiSourceTextView: TextView
    ) {
        val prefs = getPrefs(context)
        val lastModel = prefs.getString("lastModelUsed", "unknown") ?: "unknown"

        // Decide which counter/cap to display based on last used model
        val (label, used, cap) = when {
            lastModel.startsWith("gpt-4") || lastModel == "gpt-4o" -> {
                Triple("🚀 GPT-4o Pro", getTokensUsed(context), getTokenCap(context))
            }
            lastModel.startsWith("llama") -> {
                Triple("⚡ Groq Free", getGroqUsed(context), getGroqCap(context))
            }
            lastModel.startsWith("gpt-3.5") -> {
                Triple("🧠 GPT-3.5 Fallback", getGpt35Used(context), getGpt35Cap())
            }
            else -> {
                // Fallback to Pro vs Free bucket if we don't know last model
                if (isPro(context)) {
                    Triple("🚀 GPT-4o Pro", getTokensUsed(context), getTokenCap(context))
                } else {
                    Triple("⚡ Groq Free", getGroqUsed(context), getGroqCap(context))
                }
            }
        }

        val percent = ((used.toDouble() / cap) * 100).toInt().coerceIn(0, 100)
        progressBar.progress = percent

        // AI source (last model used)
        val aiLabel = when {
            lastModel.startsWith("gpt-4") || lastModel == "gpt-4o" -> "🚀 GPT-4o (OpenAI)"
            lastModel.startsWith("gpt-3.5") -> "🧠 GPT-3.5 (OpenAI)"
            lastModel.startsWith("llama") -> "⚡ LLaMA (Groq)"
            else -> "🤖 Unknown Source"
        }
        aiSourceTextView.text = "AI Source: $aiLabel"

        // Usage text
        usageTextView.text = "$label: $used / $cap tokens used"
    }

    fun getLastModelUsed(context: Context): String? {
        return getPrefs(context).getString("lastModelUsed", null)
    }
}