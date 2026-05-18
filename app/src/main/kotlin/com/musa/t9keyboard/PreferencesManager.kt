package com.musa.t9keyboard

import android.content.Context
import android.content.SharedPreferences
import com.harrytmthy.safebox.SafeBox

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences

    init {
        val PREFS_NAME = "t9_prefs"
        val plainPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (plainPrefs.all.isNotEmpty()) {
            val safePrefs = SafeBox.create(context, PREFS_NAME)
            val editor = safePrefs.edit()
            plainPrefs.all.forEach { (k, v) ->
                when (v) {
                    is Int -> editor.putInt(k, v)
                    is Long -> editor.putLong(k, v)
                    is String -> editor.putString(k, v)
                    is Boolean -> editor.putBoolean(k, v)
                    is Float -> editor.putFloat(k, v)
                }
            }
            editor.apply()
            plainPrefs.edit().clear().apply()
        }
        prefs = SafeBox.create(context, PREFS_NAME)
    }

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
        const val KEY_DELETION_SPEED = "deletion_speed"
        const val KEY_CONTACT_SUGGESTIONS_ENABLED = "contact_suggestions_enabled"
        const val KEY_EMOJI_SIZE = "emoji_size"
        const val KEY_RECENT_EMOJIS = "recent_emojis"
        const val KEY_BLACKLISTED_APPS = "blacklisted_apps"
        const val KEY_SETUP_COMPLETE = "setup_complete"
    }

    var setupComplete: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_COMPLETE, value).apply()

    var emojiSize: Int
        get() = prefs.getInt(KEY_EMOJI_SIZE, 32)
        set(value) = prefs.edit().putInt(KEY_EMOJI_SIZE, value).apply()

    var recentEmojis: String
        get() = prefs.getString(KEY_RECENT_EMOJIS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_RECENT_EMOJIS, value).apply()

    var contactSuggestionsEnabled: Boolean
        get() = prefs.getBoolean(KEY_CONTACT_SUGGESTIONS_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_CONTACT_SUGGESTIONS_ENABLED, value).apply()

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
        get() = prefs.getInt(KEY_SUGGESTION_FONT_SIZE, 13)
        set(value) = prefs.edit().putInt(KEY_SUGGESTION_FONT_SIZE, value).apply()

    var deletionSpeed: Int
        get() = prefs.getInt(KEY_DELETION_SPEED, 100)
        set(value) = prefs.edit().putInt(KEY_DELETION_SPEED, value).apply()

    var blacklistedApps: Set<String>
        get() = prefs.getStringSet(KEY_BLACKLISTED_APPS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_BLACKLISTED_APPS, value).apply()

    fun isAppBlacklisted(packageName: String): Boolean {
        return blacklistedApps.contains(packageName)
    }

    fun toggleAppBlacklist(packageName: String) {
        val current = blacklistedApps.toMutableSet()
        if (current.contains(packageName)) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        blacklistedApps = current
    }
}
