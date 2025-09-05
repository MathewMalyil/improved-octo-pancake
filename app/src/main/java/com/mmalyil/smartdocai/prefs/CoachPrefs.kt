package com.mmalyil.smartdocai.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map



object CoachPrefs {
    private val KEY_COACH_SEEN = booleanPreferencesKey("has_seen_coachmarks")

    fun hasSeenCoach(context: Context): Flow<Boolean> =
        context.smartPrefsDataStore.data.map { it[KEY_COACH_SEEN] ?: false }

    suspend fun setCoachSeen(context: Context, seen: Boolean) {
        context.smartPrefsDataStore.edit { it[KEY_COACH_SEEN] = seen }
    }
}