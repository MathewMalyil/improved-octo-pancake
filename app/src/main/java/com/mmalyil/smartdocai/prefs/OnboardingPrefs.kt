package com.mmalyil.smartdocai.prefs
// file: com/mmalyil/smartdocai/prefs/OnboardingPrefs.kt


import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map




object OnboardingPrefs {
    private val KEY_HAS_SEEN = booleanPreferencesKey("has_seen_onboarding")

    fun hasSeen(context: Context): Flow<Boolean> =
        context.smartPrefsDataStore.data.map { it[KEY_HAS_SEEN] ?: false }

    suspend fun setSeen(context: Context, seen: Boolean) {
        context.smartPrefsDataStore.edit { it[KEY_HAS_SEEN] = seen }
    }
}