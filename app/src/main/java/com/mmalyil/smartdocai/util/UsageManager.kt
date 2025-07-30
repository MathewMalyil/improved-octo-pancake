package com.mmalyil.smartdocai.util



import android.content.Context
import android.content.SharedPreferences
import java.util.*

object UsageManager {
    private const val PREF_NAME = "ai_usage"
    private const val KEY_TOKENS_USED = "tokensUsed"
    private const val KEY_PRO_PLAN = "isPro"
    private const val KEY_LAST_RESET = "lastResetTime"

    private const val FREE_TOKEN_CAP = 30_000
    private const val PRO_TOKEN_CAP = 300_000

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
}