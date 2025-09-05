package com.mmalyil.smartdocai.prefs


import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

// SINGLE source of truth. Do not duplicate this anywhere else.
val Context.smartPrefsDataStore by preferencesDataStore(name = "smartdoc_prefs")