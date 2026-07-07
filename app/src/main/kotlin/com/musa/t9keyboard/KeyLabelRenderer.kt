package com.musa.t9keyboard

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.musa.t9keyboard.databinding.KeyboardViewBinding
import com.musa.t9keyboard.utils.FontUtils

class KeyLabelRenderer(
    private val keyboardView: KeyboardView,
    private val binding: KeyboardViewBinding
) {
    private var accentColor: Int = ContextCompat.getColor(keyboardView.context, R.color.shift_active)

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
        binding.labelShift.setTextColor(if (isActive) contentColorOnAccent() else defaultTextColor)
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

    fun setAccentColor(color: Int, isNumMode: Boolean, lastShiftState: ShiftState) {
        this.accentColor = color
        binding.suggestionBar.setAccentColor(color)
        updateShiftState(lastShiftState, isNumMode)
        // Backspace is permanently accent-filled; pick readable content color for the chosen accent
        ImageViewCompat.setImageTintList(binding.labelDelIcon, ColorStateList.valueOf(contentColorOnAccent()))
        // Enter's corner dot is a plain indicator on the key surface, not accent-filled - use the raw accent
        ImageViewCompat.setImageTintList(binding.enterDot, ColorStateList.valueOf(color))
        updateKeyBackgrounds()
    }

    private val defaultTextColor: Int by lazy { ContextCompat.getColor(keyboardView.context, R.color.text_primary) }

    /**
     * WCAG relative-luminance check so backspace/active-SHIFT content stays readable
     * regardless of which accent color the user picks (light accents like yellow or
     * teal fail with hardcoded white content).
     */
    private fun contentColorOnAccent(): Int {
        val r = Color.red(accentColor) / 255.0
        val g = Color.green(accentColor) / 255.0
        val b = Color.blue(accentColor) / 255.0
        fun lin(v: Double) = if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        val luminance = 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
        return if (luminance > 0.45) Color.parseColor("#12141A") else Color.WHITE
    }

    /**
     * Flat tonal key surfaces per the design: #0D0D0D fill with a 5% white
     * hairline, #2979FF flash while pressed, accent fill when activated
     * (backspace, active shift). Built in code so the activated fill tracks
     * the user-selected accent color.
     */
    private fun updateKeyBackgrounds() {
        val context = keyboardView.context
        val radius = keyboardView.resources.getDimension(R.dimen.key_corner_radius)
        val strokeWidth = (1 * keyboardView.resources.displayMetrics.density + 0.5f).toInt()
        val surfaceColor = ContextCompat.getColor(context, R.color.key_surface)
        val borderColor = ContextCompat.getColor(context, R.color.key_border)
        val pressedColor = ContextCompat.getColor(context, R.color.key_pressed)

        allKeys.forEach { key ->
            val normal = GradientDrawable().apply {
                setColor(surfaceColor)
                setStroke(strokeWidth, borderColor)
                cornerRadius = radius
            }
            val pressed = GradientDrawable().apply {
                setColor(pressedColor)
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
