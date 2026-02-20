package com.musa.t9keyboard

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("t9_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_HAPTIC_ENABLED = "haptic_enabled"
        const val KEY_HAPTIC_DURATION = "haptic_duration"
        const val KEY_SOUND_ENABLED = "sound_enabled"
        const val KEY_SOUND_VOLUME = "sound_volume"
        const val KEY_MULTI_TAP_TIMEOUT = "multi_tap_timeout"
        const val KEY_KEY_FONT_SIZE = "key_font_size"
        const val KEY_SUGGESTION_FONT_SIZE = "suggestion_font_size"
        const val KEY_THEME = "theme"
        const val KEY_ACCENT_COLOR = "accent_color"
        const val KEY_XT9_ENABLED = "xt9_enabled"
        const val KEY_RECENTLY_USED_EMOJIS = "recently_used_emojis"
    }

    var recentlyUsedEmojis: String
        get() = prefs.getString(KEY_RECENTLY_USED_EMOJIS, "[]") ?: "[]"
        set(value) = prefs.edit().putString(KEY_RECENTLY_USED_EMOJIS, value).apply()

    var xt9Enabled: Boolean
        get() = prefs.getBoolean(KEY_XT9_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_XT9_ENABLED, value).apply()

    var theme: Int
        get() = prefs.getInt(KEY_THEME, 2) // Default to Follow System
        set(value) = prefs.edit().putInt(KEY_THEME, value).apply()

    var accentColorIndex: Int
        get() = prefs.getInt(KEY_ACCENT_COLOR, 7) // Default to Purple (matching design)
        set(value) = prefs.edit().putInt(KEY_ACCENT_COLOR, value).apply()

    var hapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC_ENABLED, value).apply()

    var hapticDuration: Int
        get() = prefs.getInt(KEY_HAPTIC_DURATION, 30)
        set(value) = prefs.edit().putInt(KEY_HAPTIC_DURATION, value).apply()

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()

    var soundVolume: Float
        get() = prefs.getFloat(KEY_SOUND_VOLUME, 0.5f)
        set(value) = prefs.edit().putFloat(KEY_SOUND_VOLUME, value).apply()

    var multiTapTimeout: Long
        get() = prefs.getLong(KEY_MULTI_TAP_TIMEOUT, 800L)
        set(value) = prefs.edit().putLong(KEY_MULTI_TAP_TIMEOUT, value).apply()

    var keyFontSize: Int
        get() = prefs.getInt(KEY_KEY_FONT_SIZE, 18)
        set(value) = prefs.edit().putInt(KEY_KEY_FONT_SIZE, value).apply()

    var suggestionFontSize: Int
        get() = prefs.getInt(KEY_SUGGESTION_FONT_SIZE, 16)
        set(value) = prefs.edit().putInt(KEY_SUGGESTION_FONT_SIZE, value).apply()
}
