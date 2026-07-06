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
            binding.labelSpace, binding.secondaryLabelSpace,
            binding.secondaryLabelEnter
        )
        allTextViews.forEach { it.typeface = ubuntu }
    }

    fun updateKeyLabels(isNumMode: Boolean, lastShiftState: ShiftState) {
        letterLabels.forEachIndexed { index, (primary, secondary) ->
            val def = letterDefs[index]
            if (isNumMode) {
                primary.text = def.numLabel ?: def.label
                primary.textSize = 22f
                secondary.visibility = View.GONE
            } else {
                primary.text = def.label
                primary.textSize = 18f
                secondary.text = def.hint ?: ""
                secondary.visibility = View.VISIBLE
            }
        }
        binding.secondaryLabelSpace.visibility = if (isNumMode) View.GONE else View.VISIBLE
        updateShiftState(lastShiftState, isNumMode)
    }

    fun updateShiftState(state: ShiftState, isNumMode: Boolean) {
        if (!isNumMode) {
            updateKeyAccent(binding.keyShift, state != ShiftState.OFF)
            binding.labelShift.text = when (state) {
                ShiftState.OFF -> "SHIFT"
                ShiftState.ONE_SHOT -> "SHIFT"
                ShiftState.CAPS_LOCK -> "CAPS"
            }
        } else {
            updateKeyAccent(binding.keyShift, true)
            binding.labelShift.text = "ABC"
        }
    }

    fun setAccentColor(color: Int, isNumMode: Boolean, lastShiftState: ShiftState) {
        this.accentColor = color
        binding.suggestionBar.setAccentColor(color)
        updateShiftState(lastShiftState, isNumMode)
        // Accent-filled keys carry white content
        ImageViewCompat.setImageTintList(binding.labelDelIcon, ColorStateList.valueOf(Color.WHITE))
        updateKeyBackgrounds()
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
