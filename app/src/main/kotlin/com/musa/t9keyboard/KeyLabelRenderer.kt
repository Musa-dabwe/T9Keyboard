package com.musa.t9keyboard

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import androidx.core.widget.ImageViewCompat
import com.musa.t9keyboard.databinding.KeyboardViewBinding
import com.musa.t9keyboard.utils.FontUtils

class KeyLabelRenderer(
    private val keyboardView: KeyboardView,
    private val binding: KeyboardViewBinding
) {
    private var accentColor: Int = Color.parseColor("#00BFA5")

    fun applyUbuntuFont() {
        val ubuntu = FontUtils.getUbuntu(keyboardView.context)
        val allTextViews = listOf(
            binding.labelAbc, binding.secondaryLabelAbc,
            binding.labelDef, binding.secondaryLabelDef,
            binding.labelGhi, binding.secondaryLabelGhi,
            binding.labelJkl, binding.secondaryLabelJkl,
            binding.labelMno, binding.secondaryLabelMno,
            binding.labelPqrs, binding.secondaryLabelPqrs,
            binding.labelTuv, binding.secondaryLabelTuv,
            binding.labelWxyz, binding.secondaryLabelWxyz,
            binding.labelPunct, binding.secondaryLabelPunct,
            binding.labelShift, binding.labelEnter,
            binding.labelSpace, binding.secondaryLabelSpace,
            binding.labelSym, binding.label123
        )
        allTextViews.forEach { it.typeface = ubuntu }
    }

    fun updateKeyLabels(isNumMode: Boolean, lastShiftState: ShiftState) {
        val digitKeys = listOf(
            Triple(binding.labelPunct, binding.secondaryLabelPunct, "1"),
            Triple(binding.labelAbc, binding.secondaryLabelAbc, "2"),
            Triple(binding.labelDef, binding.secondaryLabelDef, "3"),
            Triple(binding.labelGhi, binding.secondaryLabelGhi, "4"),
            Triple(binding.labelJkl, binding.secondaryLabelJkl, "5"),
            Triple(binding.labelMno, binding.secondaryLabelMno, "6"),
            Triple(binding.labelPqrs, binding.secondaryLabelPqrs, "7"),
            Triple(binding.labelTuv, binding.secondaryLabelTuv, "8"),
            Triple(binding.labelWxyz, binding.secondaryLabelWxyz, "9"),
            Triple(binding.labelSpace, binding.secondaryLabelSpace, "0")
        )

        val letterLabels = listOf(
            ".,!?", "ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ", "SPACE"
        )

        digitKeys.forEachIndexed { index, (primary, secondary, digit) ->
            if (isNumMode) {
                primary.text = digit
                primary.textSize = 22f
                secondary.visibility = View.GONE
            } else {
                primary.text = letterLabels[index]
                primary.textSize = 18f
                secondary.visibility = View.VISIBLE
            }
        }
        if (isNumMode) {
            binding.label123.text = ","
            binding.labelShift.text = "ABC"
        } else {
            binding.label123.text = "123"
            updateShiftState(lastShiftState, isNumMode)
        }
    }

    fun updateShiftState(state: ShiftState, isNumMode: Boolean) {
        if (!isNumMode) {
            updateKeyAccent(binding.keyShift, state != ShiftState.OFF)
            binding.labelShift.text = when(state) {
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
        ImageViewCompat.setImageTintList(binding.labelEmoji, ColorStateList.valueOf(color))
        ImageViewCompat.setImageTintList(binding.labelDelIcon, ColorStateList.valueOf(color))
        updateKeyBackgrounds()
    }

    private fun updateKeyBackgrounds() {
        val pressedColor = (accentColor and 0x00FFFFFF) or (0x66 shl 24)
        val pressedColorList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf(android.R.attr.state_activated), intArrayOf()),
            intArrayOf(pressedColor, accentColor, Color.parseColor("#000000"))
        )

        val keys = listOf(
            binding.keyPunct, binding.keyAbc, binding.keyDef, binding.keyDel,
            binding.keyGhi, binding.keyJkl, binding.keyMno, binding.key123,
            binding.keyPqrs, binding.keyTuv, binding.keyWxyz, binding.keyShift,
            binding.keySym, binding.keySpace, binding.keyEmoji, binding.keyEnter
        )
        keys.forEach { it.backgroundTintList = pressedColorList }
    }

    fun updateKeyAccent(view: View, isActivated: Boolean) {
        view.isActivated = isActivated
    }

    fun setKeyFontSize(sizeSp: Float) {
        val primaryLabels = listOf(
            binding.labelAbc, binding.labelDef, binding.labelGhi, binding.labelJkl,
            binding.labelMno, binding.labelPqrs, binding.labelTuv, binding.labelWxyz,
            binding.labelPunct, binding.labelShift, binding.labelEnter,
            binding.labelSpace, binding.labelSym, binding.label123
        )
        primaryLabels.forEach { it.textSize = sizeSp }
    }
}
