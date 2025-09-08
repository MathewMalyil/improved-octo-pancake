package com.mmalyil.smartdocai.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow   // ✅ add this
import kotlinx.coroutines.flow.map

// SINGLE source of truth. Do not duplicate this anywhere else.
val Context.smartPrefsDataStore by preferencesDataStore(name = "smartdoc_prefs")

object ThemePrefs {
    private val KEY_DARK_MODE = booleanPreferencesKey("dark_mode_enabled")

    fun isDarkMode(context: Context): Flow<Boolean> =
        context.smartPrefsDataStore.data.map { it[KEY_DARK_MODE] ?: false }

    suspend fun setDarkMode(context: Context, enabled: Boolean) {
        context.smartPrefsDataStore.edit { it[KEY_DARK_MODE] = enabled }
    }
}