package com.ideals.arnav.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Simple app-wide preferences for TrailSight. Backed by SharedPreferences,
// exposed as StateFlows so Composables can observe changes reactively.
class TrailSightSettings private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _language = MutableStateFlow(
        prefs.getString(KEY_LANG, DEFAULT_LANG) ?: DEFAULT_LANG
    )
    val language: StateFlow<String> = _language.asStateFlow()

    private val _keepScreenOn = MutableStateFlow(
        prefs.getBoolean(KEY_KEEP_ON, true)
    )
    val keepScreenOn: StateFlow<Boolean> = _keepScreenOn.asStateFlow()

    private val _showElevation = MutableStateFlow(
        prefs.getBoolean(KEY_SHOW_ELEV, true)
    )
    val showElevation: StateFlow<Boolean> = _showElevation.asStateFlow()

    private val _autoResume = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_RESUME, true)
    )
    val autoResume: StateFlow<Boolean> = _autoResume.asStateFlow()

    fun setLanguage(lang: String) {
        val normalised = if (lang == LANG_HE) LANG_HE else LANG_EN
        _language.value = normalised
        prefs.edit().putString(KEY_LANG, normalised).apply()
    }

    fun setKeepScreenOn(on: Boolean) {
        _keepScreenOn.value = on
        prefs.edit().putBoolean(KEY_KEEP_ON, on).apply()
    }

    fun setShowElevation(on: Boolean) {
        _showElevation.value = on
        prefs.edit().putBoolean(KEY_SHOW_ELEV, on).apply()
    }

    fun setAutoResume(on: Boolean) {
        _autoResume.value = on
        prefs.edit().putBoolean(KEY_AUTO_RESUME, on).apply()
    }

    companion object {
        const val LANG_EN = "en"
        const val LANG_HE = "he"
        private const val DEFAULT_LANG = LANG_EN
        private const val PREFS = "trailsight_settings"
        private const val KEY_LANG = "lang"
        private const val KEY_KEEP_ON = "keep_screen_on"
        private const val KEY_SHOW_ELEV = "show_elevation"
        private const val KEY_AUTO_RESUME = "auto_resume"

        @Volatile private var instance: TrailSightSettings? = null

        fun get(context: Context): TrailSightSettings {
            return instance ?: synchronized(this) {
                instance ?: TrailSightSettings(context).also { instance = it }
            }
        }
    }
}

// Compose helper: collect the current language once at composition time.
@Composable
fun rememberLanguage(context: Context): State<String> =
    TrailSightSettings.get(context).language.collectAsState()
