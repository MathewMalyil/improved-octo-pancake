package com.mmalyil.smartdocai.util



import android.content.Context
import android.content.SharedPreferences
import android.widget.ProgressBar
import android.widget.TextView
import java.util.*



object UsageManager {
    private const val PREF_NAME = "ai_usage"
    private const val KEY_TOKENS_USED = "tokensUsed"
    private const val KEY_PRO_PLAN = "isPro"
    private const val KEY_LAST_RESET = "lastResetTime"
    private const val KEY_GPT35_USED = "gpt35Used"

    private const val FREE_TOKEN_CAP = 30_000
    private const val PRO_TOKEN_CAP = 300_000
    private const val GPT35_TOKEN_CAP = 300

    fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isPro(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_PRO_PLAN, false)

    fun setPro(context: Context, value: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PRO_PLAN, value).apply()
    }

    fun getTokensUsed(context: Context): Int {
        maybeResetMonthly(context)
        return getPrefs(context).getInt(KEY_TOKENS_USED, 0)
    }

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

    fun getTokenCap(context: Context): Int {
        return if (isPro(context)) PRO_TOKEN_CAP else FREE_TOKEN_CAP
    }

    fun resetUsage(context: Context) {
        getPrefs(context).edit()
            .putInt(KEY_TOKENS_USED, 0)
            .putInt(KEY_GPT35_USED, 0)
            .putLong(KEY_LAST_RESET, System.currentTimeMillis())
            .apply()
    }

    private fun maybeResetMonthly(context: Context) {
        val prefs = getPrefs(context)
        val lastReset = prefs.getLong(KEY_LAST_RESET, 0L)
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = lastReset
        val lastMonth = calendar.get(Calendar.MONTH)

        val now = Calendar.getInstance()
        val currentMonth = now.get(Calendar.MONTH)

        if (lastMonth != currentMonth) {
            resetUsage(context)
        }
    }

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

    fun bindUsageUI(
        context: Context,
        usageTextView: TextView,
        progressBar: ProgressBar,
        aiSourceTextView: TextView
    ) {
        val prefs = getPrefs(context)
        val isPro = isPro(context)

        // Tokens used and cap for GPT-4 or Groq
        val used = getTokensUsed(context)
        val cap = getTokenCap(context)

        // GPT-3.5 fallback (separate counter)
        val gpt35Used = getGpt35Used(context)
        val gpt35Cap = getGpt35Cap()

        val percent = ((used.toDouble() / cap) * 100).toInt().coerceIn(0, 100)
        progressBar.progress = percent

        // 🎯 Get the last model actually used
        val lastModel = prefs.getString("lastModelUsed", "unknown") ?: "unknown"

        // 📌 AI Source label (based on last used model)
        val aiLabel = when {
            lastModel.startsWith("gpt-4") || lastModel == "gpt-4o" -> "🚀 GPT-4o (OpenAI)"
            lastModel.startsWith("gpt-3.5") -> "🧠 GPT-3.5 (OpenAI)"
            lastModel.startsWith("llama") -> "⚡ LLaMA (Groq)"
            else -> "🤖 Unknown Source"
        }
        aiSourceTextView.text = "AI Source: $aiLabel"

        // 🧮 Usage text (token-based or 3.5 fallback)
        usageTextView.text = when {
            lastModel.startsWith("gpt-3.5") -> "🧠 GPT-3.5 Fallback: $gpt35Used / $gpt35Cap tokens used"
            isPro -> "🚀 GPT-4o Pro: $used / $cap tokens used"
            else -> "⚡ Groq Free: $used / $cap tokens used"
        }
    }

    fun getLastModelUsed(context: Context): String? {
        return getPrefs(context).getString("lastModelUsed", null)
    }
}