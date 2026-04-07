package com.musa.t9keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.slider.Slider

class SettingsDialogHelper(private val context: Context, private val preferences: PreferencesManager, private val accentColors: IntArray) {

    fun showInfoDialog() {
        val message = "T9 Keyboard is a modern, lightweight input method built for Android by Musadabwe in \n© 2026\n. Designed with speed and simplicity in mind, it brings back the familiarity of T9 predictive typing while adding smart word prediction powered by the AOSP dictionary. Built with Kotlin for the Android layer and Python for dictionary processing, T9 Keyboard is a passion project crafted from the ground up."
        AlertDialog.Builder(context)
            .setTitle("Keyboard Info")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    fun showSliderDialog(title: String, from: Float, to: Float, current: Float, onConfirm: (Float) -> Unit) {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        val slider = Slider(context).apply {
            valueFrom = from
            valueTo = to
            value = current.coerceIn(from, to)
            val accentColor = ContextCompat.getColor(context, accentColors[preferences.accentColorIndex])
            thumbTintList = ColorStateList.valueOf(accentColor)
            trackActiveTintList = thumbTintList
        }
        container.addView(slider)

        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(container)
            .setPositiveButton("Confirm") { _, _ -> onConfirm(slider.value) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showThemeDialog(onThemeSelected: (Int) -> Unit) {
        val options = arrayOf("Light", "Dark", "System Default")
        AlertDialog.Builder(context)
            .setTitle("Select Theme")
            .setSingleChoiceItems(options, preferences.theme) { dialog, which ->
                onThemeSelected(which)
                dialog.dismiss()
            }
            .show()
    }

    fun showSensitivityDialog(onSelected: (Int) -> Unit) {
        val options = arrayOf("Conservative", "Normal", "Aggressive")
        AlertDialog.Builder(context)
            .setTitle("Select Sensitivity")
            .setSingleChoiceItems(options, preferences.autocorrectSensitivity) { dialog, which ->
                onSelected(which)
                dialog.dismiss()
            }
            .show()
    }
}
