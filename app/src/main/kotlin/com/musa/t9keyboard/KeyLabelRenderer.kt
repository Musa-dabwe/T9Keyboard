package com.musa.t9keyboard

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import com.musa.t9keyboard.databinding.KeyboardViewBinding
import com.musa.t9keyboard.utils.FontUtils

class KeyLabelRenderer(
    private val keyboardView: KeyboardView,
    private val binding: KeyboardViewBinding
) {
    private var theme: KeyboardTheme = KeyboardThemes.DEFAULT
    private var accentColor: Int = androidx.core.content.ContextCompat.getColor(keyboardView.context, R.color.shift_active)

    // Primary label / hint label pairs for keys with letter output, in KeyboardLayout row order
    private val letterLabels: List<Pair<TextView, TextView>> = listOf(
        binding.labelPunct to binding.secondaryLabelPunct,
        binding.labelAb to binding.secondaryLabelAb,
        binding.labelCd to binding.secondaryLabelCd,
        binding.labelEf to binding.secondaryLabelEf,
        binding.labelGh to binding.secondaryLabelGh,
        binding.labelIj to binding.secondaryLabelIj,
        binding.labelKl to binding.secondaryLabelKl,
        binding.labelMn to binding.secondaryLabelMn,
        binding.labelOp to binding.secondaryLabelOp,
        binding.labelQr to binding.secondaryLabelQr,
        binding.labelSt to binding.secondaryLabelSt,
        binding.labelUv to binding.secondaryLabelUv,
        binding.labelWx to binding.secondaryLabelWx,
        binding.labelYz to binding.secondaryLabelYz
    )

    private val letterDefs: List<KeyDef> = KeyboardLayout.ALL_KEYS.filter {
        it.type == KeyType.LETTER || it.type == KeyType.SYMBOL_CYCLE
    }

    private val allKeys: List<View> = listOf(
        binding.keyPunct, binding.keyAb, binding.keyCd, binding.keyEf, binding.keyDel,
        binding.keyGh, binding.keyIj, binding.keyKl, binding.keyMn, binding.keySym,
        binding.keyOp, binding.keyQr, binding.keySt, binding.keyUv, binding.keyShift,
        binding.keyWx, binding.keySpace, binding.keyYz, binding.keyEnter
    )

    init {
        // Backspace is permanently accent-filled per the design
        binding.keyDel.isActivated = true
    }

    fun applyUbuntuFont() {
        val ubuntu = FontUtils.getUbuntu(keyboardView.context)
        val allTextViews = letterLabels.flatMap { listOf(it.first, it.second) } + listOf(
            binding.labelShift, binding.labelSym,
            binding.labelSpace, binding.secondaryLabelSpace
        )
        allTextViews.forEach { it.typeface = ubuntu }
    }

    fun updateKeyLabels(isNumMode: Boolean, lastShiftState: ShiftState) {
        letterLabels.forEachIndexed { index, (primary, secondary) ->
            val def = letterDefs[index]
            if (isNumMode) {
                primary.text = def.numLabel ?: def.label
                primary.textSize = 22f
                if (def.numHint != null) {
                    secondary.text = def.numHint
                    secondary.visibility = View.VISIBLE
                } else {
                    secondary.visibility = View.GONE
                }
            } else {
                primary.text = def.label
                primary.textSize = 18f
                secondary.text = def.hint ?: ""
                secondary.visibility = View.VISIBLE
            }
        }
        // Number mode hides the corner "0" hint, so surface the long-press digit in the label
        binding.labelSpace.text = if (isNumMode) "SPACE [0]" else "SPACE"
        binding.secondaryLabelSpace.visibility = if (isNumMode) View.GONE else View.VISIBLE
        updateShiftState(lastShiftState, isNumMode)
    }

    fun updateShiftState(state: ShiftState, isNumMode: Boolean) {
        val isActive = isNumMode || state != ShiftState.OFF
        updateKeyAccent(binding.keyShift, isActive)
        binding.labelShift.setTextColor(if (isActive) contentColorOnAccent() else theme.keyText)
        if (!isNumMode) {
            binding.labelShift.text = when (state) {
                ShiftState.OFF -> "SHIFT"
                ShiftState.ONE_SHOT -> "SHIFT"
                ShiftState.CAPS_LOCK -> "CAPS"
            }
        } else {
            binding.labelShift.text = "ABC"
        }
    }

    fun setTheme(theme: KeyboardTheme, isNumMode: Boolean, lastShiftState: ShiftState) {
        this.theme = theme
        applyThemeColors(isNumMode, lastShiftState)
    }

    fun setAccentColor(color: Int, isNumMode: Boolean, lastShiftState: ShiftState) {
        this.accentColor = color
        binding.suggestionBar.setAccentColor(color)
        applyThemeColors(isNumMode, lastShiftState)
    }

    private fun applyThemeColors(isNumMode: Boolean, lastShiftState: ShiftState) {
        // Key labels and corner hints
        letterLabels.forEach { (primary, secondary) ->
            primary.setTextColor(theme.keyText)
            secondary.setTextColor(theme.keyHint)
        }
        binding.labelSym.setTextColor(theme.keyText)
        binding.labelSpace.setTextColor(theme.keyText)
        binding.secondaryLabelSpace.setTextColor(theme.keyHint)
        ImageViewCompat.setImageTintList(binding.labelEnterIcon, ColorStateList.valueOf(theme.keyText))

        updateShiftState(lastShiftState, isNumMode)
        // Backspace is permanently accent-filled; pick readable content color for the chosen accent
        ImageViewCompat.setImageTintList(binding.labelDelIcon, ColorStateList.valueOf(contentColorOnAccent()))
        // Enter's corner dot is a plain indicator on the key surface, not accent-filled - use the raw accent
        ImageViewCompat.setImageTintList(binding.enterDot, ColorStateList.valueOf(accentColor))
        updateKeyBackgrounds()
    }

    private fun contentColorOnAccent(): Int = KeyboardThemes.readableOn(accentColor)

    /**
     * Flat tonal key surfaces: theme key fill with a hairline border, accent
     * flash while pressed, accent fill when activated (backspace, active
     * shift). Built in code so both the theme palette and the user-selected
     * accent color apply at runtime.
     */
    private fun updateKeyBackgrounds() {
        val radius = keyboardView.resources.getDimension(R.dimen.key_corner_radius)
        val strokeWidth = (1 * keyboardView.resources.displayMetrics.density + 0.5f).toInt()

        allKeys.forEach { key ->
            val normal = GradientDrawable().apply {
                setColor(theme.keySurface)
                setStroke(strokeWidth, theme.keyBorder)
                cornerRadius = radius
            }
            // Pressed flash follows the accent so it matches the app theme
            // instead of the old hardcoded blue.
            val pressed = GradientDrawable().apply {
                setColor(accentColor)
                cornerRadius = radius
            }
            val activated = GradientDrawable().apply {
                setColor(accentColor)
                cornerRadius = radius
            }
            key.background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), pressed)
                addState(intArrayOf(android.R.attr.state_activated), activated)
                addState(intArrayOf(), normal)
            }
        }
    }

    fun updateKeyAccent(view: View, isActivated: Boolean) {
        view.isActivated = isActivated
    }

    fun setKeyFontSize(sizeSp: Float) {
        val primaryLabels = letterLabels.map { it.first } + listOf(binding.labelSpace)
        primaryLabels.forEach { it.textSize = sizeSp }
    }
}
