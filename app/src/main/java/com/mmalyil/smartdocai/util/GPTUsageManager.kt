package com.mmalyil.smartdocai.util


import android.content.Context
import android.content.SharedPreferences


object GPTUsageManager {

    private const val PREFS_NAME = "gpt_usage_prefs"

    fun getUsage(context: Context, model: String): Int {
        return getPrefs(context).getInt("usage_$model", 0)
    }

    fun incrementUsage(context: Context, model: String) {
        val prefs = getPrefs(context)
        val current = prefs.getInt("usage_$model", 0)
        prefs.edit().putInt("usage_$model", current + 1).apply()
    }

    fun resetUsage(context: Context, model: String) {
        getPrefs(context).edit().putInt("usage_$model", 0).apply()
    }

    fun resetAll(context: Context) {
        val editor = getPrefs(context).edit()
        editor.putInt("usage_gpt-4", 0)
        editor.putInt("usage_gpt-3.5", 0)
        editor.putInt("usage_groq", 0)
        editor.apply()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}