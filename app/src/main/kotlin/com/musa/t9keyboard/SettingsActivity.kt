package com.musa.t9keyboard

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.musa.t9keyboard.databinding.ActivitySettingsBinding
import com.musa.t9keyboard.utils.FontUtils
import com.google.android.material.slider.Slider
import android.widget.LinearLayout
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var preferences: PreferencesManager

    companion object {
        private const val PERMISSION_REQUEST_CONTACTS = 101
    }

    private val accentColors = listOf(
        R.color.accent_blue,
        R.color.accent_teal,
        R.color.accent_green,
        R.color.accent_yellow,
        R.color.accent_magenta,
        R.color.accent_red,
        R.color.accent_orange,
        R.color.accent_purple
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        val isNightMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isNightMode
            isAppearanceLightNavigationBars = !isNightMode
        }

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = PreferencesManager(this)
        LearnedDictionary.load(this)

        setupHeader()
        setupUI()
        setupAccentColorSelector()
        applyUbuntuFont()
        applyAccentColor()
        handleInsets()
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnInfo.setOnClickListener { showInfoDialog() }
    }

    private fun showInfoDialog() {
        val message = "T9 Keyboard is a modern, lightweight input method built for Android by Musadabwe in /n© 2026n. Designed with speed and simplicity in mind, it brings back the familiarity of T9 predictive typing while adding smart word prediction powered by the AOSP dictionary. Built with Kotlin for the Android layer and Python for dictionary processing, T9 Keyboard is a passion project crafted from the ground up."
        AlertDialog.Builder(this)
            .setTitle("Keyboard Info")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun setupUI() {
        // XT9 Predictive Text
        updateToggle(binding.toggleXt9, preferences.xt9Enabled)
        binding.rowXt9.setOnClickListener {
            preferences.xt9Enabled = !preferences.xt9Enabled
            updateToggle(binding.toggleXt9, preferences.xt9Enabled)
        }

        // Haptic feedback
        updateToggle(binding.toggleHaptic, preferences.hapticEnabled)
        binding.rowHaptic.setOnClickListener {
            preferences.hapticEnabled = !preferences.hapticEnabled
            updateToggle(binding.toggleHaptic, preferences.hapticEnabled)
        }

        // Haptic intensity
        binding.boxHapticIntensity.text = preferences.hapticDuration.toString()
        binding.rowHapticIntensity.setOnClickListener {
            showSliderDialog("Haptic Intensity", 0f, 50f, preferences.hapticDuration.toFloat()) { newValue ->
                preferences.hapticDuration = newValue.toInt()
                binding.boxHapticIntensity.text = newValue.toInt().toString()
            }
        }

        // Delete speed
        binding.boxDeleteSpeed.text = preferences.deletionSpeed.toString()
        binding.rowDeleteSpeed.setOnClickListener {
            showSliderDialog("Delete Speed", 0f, 100f, preferences.deletionSpeed.toFloat()) { newValue ->
                preferences.deletionSpeed = newValue.toInt()
                binding.boxDeleteSpeed.text = newValue.toInt().toString()
            }
        }

        // Key press sound
        updateToggle(binding.toggleSound, preferences.soundEnabled)
        binding.rowSound.setOnClickListener {
            preferences.soundEnabled = !preferences.soundEnabled
            updateToggle(binding.toggleSound, preferences.soundEnabled)
        }

        // Sound volume
        binding.boxSoundVolume.text = (preferences.soundVolume * 100).toInt().toString()
        binding.rowSoundVolume.setOnClickListener {
            showSliderDialog("Sound Volume", 0f, 100f, preferences.soundVolume * 100) { newValue ->
                preferences.soundVolume = newValue / 100f
                binding.boxSoundVolume.text = newValue.toInt().toString()
            }
        }

        // Multi-tap timeout
        binding.boxTimeout.text = preferences.multiTapTimeout.toString()
        binding.rowTimeout.setOnClickListener {
            showSliderDialog("Multi-tap timeout", 0f, 800f, preferences.multiTapTimeout.toFloat()) { newValue ->
                preferences.multiTapTimeout = newValue.toLong()
                binding.boxTimeout.text = newValue.toInt().toString()
            }
        }

        // Key label font size
        binding.boxKeyFont.text = preferences.keyFontSize.toString()
        binding.rowKeyFont.setOnClickListener {
            showSliderDialog("Key label font size (sp)", 0f, 40f, preferences.keyFontSize.toFloat()) { newValue ->
                preferences.keyFontSize = newValue.toInt()
                binding.boxKeyFont.text = newValue.toInt().toString()
            }
        }

        // Suggestion font size
        binding.boxSuggestionFont.text = preferences.suggestionFontSize.toString()
        binding.rowSuggestionFont.setOnClickListener {
            showSliderDialog("Suggestion Font Size (sp)", 0f, 30f, preferences.suggestionFontSize.toFloat()) { newValue ->
                preferences.suggestionFontSize = newValue.toInt()
                binding.boxSuggestionFont.text = newValue.toInt().toString()
            }
        }

        // Theme selection
        updateThemeText()
        binding.spinnerTheme.setOnClickListener { showThemeDialog() }

        // Contact Suggestions
        updateToggle(binding.toggleContacts, preferences.contactSuggestionsEnabled)
        binding.rowContacts.setOnClickListener {
            if (!preferences.contactSuggestionsEnabled) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS), PERMISSION_REQUEST_CONTACTS)
                } else {
                    preferences.contactSuggestionsEnabled = true
                    updateToggle(binding.toggleContacts, true)
                    Thread { ContactsDictionary.load(this) }.start()
                }
            } else {
                preferences.contactSuggestionsEnabled = false
                updateToggle(binding.toggleContacts, false)
                ContactsDictionary.clear()
            }
        }

        // Clear dictionary
        binding.btnClearDict.setOnClickListener {
            LearnedDictionary.clear()
            Toast.makeText(this, "Learned dictionary cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateToggle(imageView: ImageView, isEnabled: Boolean) {
        if (isEnabled) {
            imageView.setImageResource(R.drawable.toggle_on)
            imageView.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, accentColors[preferences.accentColorIndex]))
        } else {
            imageView.setImageResource(R.drawable.toggle_off)
            imageView.imageTintList = ColorStateList.valueOf(Color.parseColor("#444444"))
        }
    }

    private fun showSliderDialog(title: String, from: Float, to: Float, current: Float, onConfirm: (Float) -> Unit) {
        val dialogView = View.inflate(this, R.layout.suggestion_bar, null) // Reusing suggestion_bar layout just to get a container
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
        }
        val slider = Slider(this).apply {
            valueFrom = from
            valueTo = to
            value = current.coerceIn(from, to)
            thumbTintList = ColorStateList.valueOf(ContextCompat.getColor(this@SettingsActivity, accentColors[preferences.accentColorIndex]))
            trackActiveTintList = thumbTintList
        }
        container.addView(slider)

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(container)
            .setPositiveButton("Confirm") { _, _ -> onConfirm(slider.value) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showThemeDialog() {
        val options = arrayOf("Light", "Dark", "System Default")
        val checkedItem = preferences.theme // 0, 1, 2

        AlertDialog.Builder(this)
            .setTitle("Select Theme")
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                preferences.theme = which
                applyTheme(which)
                updateThemeText()
                dialog.dismiss()
            }
            .show()
    }

    private fun updateThemeText() {
        binding.txtSelectedTheme.text = when (preferences.theme) {
            0 -> "Light"
            1 -> "Dark"
            else -> "System Default"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CONTACTS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                preferences.contactSuggestionsEnabled = true
                updateToggle(binding.toggleContacts, true)
                Thread { ContactsDictionary.load(this) }.start()
                Toast.makeText(this, "Contact suggestions enabled", Toast.LENGTH_SHORT).show()
            } else {
                preferences.contactSuggestionsEnabled = false
                updateToggle(binding.toggleContacts, false)
                Toast.makeText(this, "Permission denied — contact suggestions disabled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyTheme(themeValue: Int) {
        val mode = when (themeValue) {
            0 -> AppCompatDelegate.MODE_NIGHT_NO
            1 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun handleInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupAccentColorSelector() {
        binding.accentColorsLayout.removeAllViews()
        val size = (31 * resources.displayMetrics.density).toInt()
        val margin = (4 * resources.displayMetrics.density).toInt()

        accentColors.forEachIndexed { index, colorResId ->
            val frame = FrameLayout(this).apply {
                layoutParams = FrameLayout.LayoutParams(size, size).apply {
                    setMargins(margin, 0, margin, 0)
                }
            }

            val circle = ImageView(this).apply {
                setImageResource(R.drawable.circle_white)
                imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this@SettingsActivity, colorResId))
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }

            frame.addView(circle)

            if (index == preferences.accentColorIndex) {
                val check = ImageView(this).apply {
                    setImageResource(R.drawable.ic_check)
                    imageTintList = ColorStateList.valueOf(Color.BLACK)
                    val p = (8 * resources.displayMetrics.density).toInt()
                    setPadding(p, p, p, p)
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                }
                frame.addView(check)
            }

            frame.setOnClickListener {
                preferences.accentColorIndex = index
                setupAccentColorSelector()
                applyAccentColor()
                updateToggles()
            }

            binding.accentColorsLayout.addView(frame)
        }
    }

    private fun updateToggles() {
        updateToggle(binding.toggleXt9, preferences.xt9Enabled)
        updateToggle(binding.toggleHaptic, preferences.hapticEnabled)
        updateToggle(binding.toggleSound, preferences.soundEnabled)
        updateToggle(binding.toggleContacts, preferences.contactSuggestionsEnabled)
    }

    private fun applyAccentColor() {
        val accentColor = ContextCompat.getColor(this, accentColors[preferences.accentColorIndex])

        binding.headerInputMode.setTextColor(accentColor)
        binding.headerFeedback.setTextColor(accentColor)
        binding.headerLayout.setTextColor(accentColor)
        binding.headerTheme.setTextColor(accentColor)
        binding.headerIntegrations.setTextColor(accentColor)

        binding.btnClearDict.strokeColor = ColorStateList.valueOf(accentColor)
    }

    private fun applyUbuntuFont() {
        val ubuntu = FontUtils.getUbuntu(this)
        val allViews = listOf(
            binding.txtWelcome,
            binding.headerInputMode,
            binding.txtXt9Title,
            binding.txtXt9Subtitle,
            binding.headerFeedback,
            binding.txtHapticTitle,
            binding.txtHapticSubtitle,
            binding.txtHapticIntensityTitle,
            binding.txtHapticIntensitySubtitle,
            binding.boxHapticIntensity,
            binding.txtDeleteSpeedTitle,
            binding.txtDeleteSpeedSubtitle,
            binding.boxDeleteSpeed,
            binding.txtSoundTitle,
            binding.txtSoundSubtitle,
            binding.txtSoundVolumeTitle,
            binding.txtSoundVolumeSubtitle,
            binding.boxSoundVolume,
            binding.headerLayout,
            binding.txtTimeoutTitle,
            binding.txtTimeoutSubtitle,
            binding.boxTimeout,
            binding.txtKeyFontTitle,
            binding.txtKeyFontSubtitle,
            binding.boxKeyFont,
            binding.txtSuggestionFontTitle,
            binding.txtSuggestionFontSubtitle,
            binding.boxSuggestionFont,
            binding.headerTheme,
            binding.headerIntegrations,
            binding.txtContactsTitle,
            binding.txtContactsSubtitle,
            binding.txtAccentColorTitle,
            binding.txtThemeTitle,
            binding.txtSelectedTheme,
            binding.btnClearDict,
            binding.txtClearDictDesc
        )
        allViews.forEach { it.typeface = ubuntu }
    }
}
