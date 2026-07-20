package com.musa.t9keyboard

import android.graphics.Color

/**
 * Full color palette for the keyboard. Every surface the keyboard draws pulls
 * from one of these slots, so switching themes restyles everything — not just
 * the accent color.
 */
data class KeyboardTheme(
    val id: String,
    val displayName: String,
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
    val suggestionText: Int,
    /** Primary accent (gradients, links, highlights). */
    val accent: Int,
    /** Solid accent for pressed states, filled keys and active toggles. */
    val accentSolid: Int,
    /** Soft tint for chips and hover/active backgrounds. */
    val soft: Int,
    /** Key pressed / activated fill. */
    val kpress: Int,
    /** Special key fill (shift, sym, backspace). */
    val kaccent: Int
)

object KeyboardThemes {

    val PEACH = KeyboardTheme(
        id = "peach", displayName = "Soft Peach",
        background = 0xFFFBE0CF.toInt(),
        keySurface = 0xFFFFFAF5.toInt(),
        keyBorder = 0xFFF3DDCA.toInt(),
        keyText = 0xFF5A463C.toInt(),
        keyHint = 0xFFC19A83.toInt(),
        suggestionBackground = 0xFFFBE0CF.toInt(),
        suggestionText = 0xFF5A463C.toInt(),
        accent = 0xFFF0A678.toInt(),
        accentSolid = 0xFFE0763F.toInt(),
        soft = 0xFFFBE4D3.toInt(),
        kpress = 0xFFE0763F.toInt(),
        kaccent = 0xFFF7D9C2.toInt()
    )

    val ROSE = KeyboardTheme(
        id = "rose", displayName = "Blush Rose",
        background = 0xFFFFDBE6.toInt(),
        keySurface = 0xFFFFF8FA.toInt(),
        keyBorder = 0xFFFAD3DF.toInt(),
        keyText = 0xFF5A3644.toInt(),
        keyHint = 0xFFCF97A8.toInt(),
        suggestionBackground = 0xFFFFDBE6.toInt(),
        suggestionText = 0xFF5A3644.toInt(),
        accent = 0xFFF5A3BE.toInt(),
        accentSolid = 0xFFE06489.toInt(),
        soft = 0xFFFFE0EA.toInt(),
        kpress = 0xFFE06489.toInt(),
        kaccent = 0xFFFFD6E2.toInt()
    )

    val LAVENDER = KeyboardTheme(
        id = "lavender", displayName = "Lavender Mist",
        background = 0xFFE6DFF9.toInt(),
        keySurface = 0xFFFAF8FF.toInt(),
        keyBorder = 0xFFE3DCF6.toInt(),
        keyText = 0xFF3A3352.toInt(),
        keyHint = 0xFF9C92C4.toInt(),
        suggestionBackground = 0xFFE6DFF9.toInt(),
        suggestionText = 0xFF3A3352.toInt(),
        accent = 0xFFB3A4E6.toInt(),
        accentSolid = 0xFF7B6EC4.toInt(),
        soft = 0xFFECE6FA.toInt(),
        kpress = 0xFF7B6EC4.toInt(),
        kaccent = 0xFFE8E1FB.toInt()
    )

    val SKY = KeyboardTheme(
        id = "sky", displayName = "Sky Blue",
        background = 0xFFDAEBFA.toInt(),
        keySurface = 0xFFF7FBFF.toInt(),
        keyBorder = 0xFFD3E6F7.toInt(),
        keyText = 0xFF2B3D52.toInt(),
        keyHint = 0xFF8AA8C8.toInt(),
        suggestionBackground = 0xFFDAEBFA.toInt(),
        suggestionText = 0xFF2B3D52.toInt(),
        accent = 0xFF8FC0EF.toInt(),
        accentSolid = 0xFF4A8FD6.toInt(),
        soft = 0xFFDCECFA.toInt(),
        kpress = 0xFF4A8FD6.toInt(),
        kaccent = 0xFFE0EEFB.toInt()
    )

    val MINT = KeyboardTheme(
        id = "mint", displayName = "Mint & Sky",
        background = 0xFFD7F0E5.toInt(),
        keySurface = 0xFFF6FEFB.toInt(),
        keyBorder = 0xFFCFEADA.toInt(),
        keyText = 0xFF2C4A3F.toInt(),
        keyHint = 0xFF7FB0A0.toInt(),
        suggestionBackground = 0xFFD7F0E5.toInt(),
        suggestionText = 0xFF2C4A3F.toInt(),
        accent = 0xFF6FC79E.toInt(),
        accentSolid = 0xFF3F9E78.toInt(),
        soft = 0xFFDCF3E8.toInt(),
        kpress = 0xFF3F9E78.toInt(),
        kaccent = 0xFFDFF5EC.toInt()
    )

    val BUTTER = KeyboardTheme(
        id = "butter", displayName = "Butter Cream",
        background = 0xFFFBEEC4.toInt(),
        keySurface = 0xFFFFFDF6.toInt(),
        keyBorder = 0xFFF5E6BF.toInt(),
        keyText = 0xFF4D4228.toInt(),
        keyHint = 0xFFC2AC6E.toInt(),
        suggestionBackground = 0xFFFBEEC4.toInt(),
        suggestionText = 0xFF4D4228.toInt(),
        accent = 0xFFF4D47A.toInt(),
        accentSolid = 0xFFCF9F2F.toInt(),
        soft = 0xFFFDF0CF.toInt(),
        kpress = 0xFFCF9F2F.toInt(),
        kaccent = 0xFFFBF1CC.toInt()
    )

    val ALL = listOf(PEACH, ROSE, LAVENDER, SKY, MINT, BUTTER)

    val DEFAULT = SKY

    fun byId(id: String): KeyboardTheme? = ALL.find { it.id == id }

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
