package com.musa.t9keyboard

import android.content.res.Configuration
import android.graphics.Color

/**
 * Full color palette for the keyboard. Every surface the keyboard draws pulls
 * from one of these slots, so switching themes restyles everything — not just
 * the accent color.
 */
data class KeyboardTheme(
    val id: String,
    val displayName: String,
    /** Theme family ("github", "vscode", "claude") used to pair light/dark variants. */
    val family: String,
    val isDark: Boolean,
    /** Keyboard window background behind the key grid. */
    val background: Int,
    /** Fill of an idle key. */
    val keySurface: Int,
    /** Hairline border around each key. */
    val keyBorder: Int,
    /** Primary key label color. */
    val keyText: Int,
    /** Corner hints, muted labels, disabled text. */
    val keyHint: Int,
    /** Suggestion bar / panel top-bar background. */
    val suggestionBackground: Int,
    /** Suggestion chip text. */
    val suggestionText: Int
)

object KeyboardThemes {

    /** Sentinel id: follow the system dark-mode setting within the current family. */
    const val SYSTEM_ID = "system"
    const val SYSTEM_DISPLAY_NAME = "System Default"

    val GITHUB_DARK = KeyboardTheme(
        id = "github_dark", displayName = "GitHub Dark", family = "github", isDark = true,
        background = 0xFF0D1117.toInt(),
        keySurface = 0xFF161B22.toInt(),
        keyBorder = 0xFF30363D.toInt(),
        keyText = 0xFFE6EDF3.toInt(),
        keyHint = 0xFF8B949E.toInt(),
        suggestionBackground = 0xFF161B22.toInt(),
        suggestionText = 0xFFE6EDF3.toInt()
    )

    val GITHUB_LIGHT = KeyboardTheme(
        id = "github_light", displayName = "GitHub Light", family = "github", isDark = false,
        background = 0xFFF6F8FA.toInt(),
        keySurface = 0xFFFFFFFF.toInt(),
        keyBorder = 0xFFD0D7DE.toInt(),
        keyText = 0xFF1F2328.toInt(),
        keyHint = 0xFF656D76.toInt(),
        suggestionBackground = 0xFFEAEEF2.toInt(),
        suggestionText = 0xFF1F2328.toInt()
    )

    val VSCODE_DARK = KeyboardTheme(
        id = "vscode_dark", displayName = "VS Code Dark", family = "vscode", isDark = true,
        background = 0xFF1E1E1E.toInt(),
        keySurface = 0xFF252526.toInt(),
        keyBorder = 0xFF3C3C3C.toInt(),
        keyText = 0xFFCCCCCC.toInt(),
        keyHint = 0xFF858585.toInt(),
        suggestionBackground = 0xFF252526.toInt(),
        suggestionText = 0xFFCCCCCC.toInt()
    )

    val VSCODE_LIGHT = KeyboardTheme(
        id = "vscode_light", displayName = "VS Code Light", family = "vscode", isDark = false,
        background = 0xFFF3F3F3.toInt(),
        keySurface = 0xFFFFFFFF.toInt(),
        keyBorder = 0xFFCECECE.toInt(),
        keyText = 0xFF333333.toInt(),
        keyHint = 0xFF616161.toInt(),
        suggestionBackground = 0xFFE8E8E8.toInt(),
        suggestionText = 0xFF333333.toInt()
    )

    val CLAUDE_DARK = KeyboardTheme(
        id = "claude_dark", displayName = "Claude Dark", family = "claude", isDark = true,
        background = 0xFF262624.toInt(),
        keySurface = 0xFF30302E.toInt(),
        keyBorder = 0xFF43423E.toInt(),
        keyText = 0xFFFAF9F5.toInt(),
        keyHint = 0xFFB0AEA5.toInt(),
        suggestionBackground = 0xFF30302E.toInt(),
        suggestionText = 0xFFFAF9F5.toInt()
    )

    val CLAUDE_LIGHT = KeyboardTheme(
        id = "claude_light", displayName = "Claude Light", family = "claude", isDark = false,
        background = 0xFFFAF9F5.toInt(),
        keySurface = 0xFFFFFFFF.toInt(),
        keyBorder = 0xFFE0DED3.toInt(),
        keyText = 0xFF3D3929.toInt(),
        keyHint = 0xFF87867F.toInt(),
        suggestionBackground = 0xFFF0EEE6.toInt(),
        suggestionText = 0xFF3D3929.toInt()
    )

    val ALL = listOf(GITHUB_DARK, GITHUB_LIGHT, VSCODE_DARK, VSCODE_LIGHT, CLAUDE_DARK, CLAUDE_LIGHT)

    /** VS Code Dark is closest to the keyboard's original look, so it's the fallback. */
    val DEFAULT = VSCODE_DARK

    fun byId(id: String): KeyboardTheme? = ALL.find { it.id == id }

    /**
     * Resolves the stored theme selection to a concrete palette. [SYSTEM_ID]
     * follows the system dark-mode setting, picking the light or dark variant
     * of the most recently chosen theme family.
     */
    fun resolve(themeId: String, family: String, isSystemDark: Boolean): KeyboardTheme {
        if (themeId != SYSTEM_ID) {
            return byId(themeId) ?: DEFAULT
        }
        return ALL.find { it.family == family && it.isDark == isSystemDark }
            ?: ALL.find { it.isDark == isSystemDark }
            ?: DEFAULT
    }

    fun isSystemDark(configuration: Configuration): Boolean =
        (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /**
     * WCAG relative-luminance check so content drawn on top of an arbitrary
     * color (accent fills, key previews) stays readable — light accents like
     * yellow or teal fail with hardcoded white content.
     */
    fun readableOn(background: Int): Int {
        val r = Color.red(background) / 255.0
        val g = Color.green(background) / 255.0
        val b = Color.blue(background) / 255.0
        fun lin(v: Double) = if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        val luminance = 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
        return if (luminance > 0.45) Color.parseColor("#12141A") else Color.WHITE
    }

    /** Applies [alpha] (0-255) to [color], keeping its RGB channels. */
    fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)
}
