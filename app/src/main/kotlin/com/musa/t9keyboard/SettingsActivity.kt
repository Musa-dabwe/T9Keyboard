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
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.core.app.ActivityCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var preferences: PreferencesManager
    private lateinit var dialogHelper: SettingsDialogHelper

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

    private var versionTapCount = 0
    private var lastVersionTapTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.TRANSPARENT

        val uiMode = resources.configuration.uiMode
        val isNightMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isNightMode
            isAppearanceLightNavigationBars = !isNightMode
        }

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = PreferencesManager(this)
        LearnedDictionary.load(this)
        dialogHelper = SettingsDialogHelper(this, preferences, accentColors)

        setupHeader()
        setupUI()
        setupAccentColorSelector()
        applyUbuntuFont()
        applyAccentColor()
        handleInsets()
    }

    private fun setupHeader() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnInfo.setOnClickListener { dialogHelper.showInfoDialog() }

        binding.txtWelcome.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastVersionTapTime < 500) {
                versionTapCount++
            } else {
                versionTapCount = 1
            }
            lastVersionTapTime = now
            if (versionTapCount == 5) {
                versionTapCount = 0
                val intent = android.content.Intent(this, DebugLogsActivity::class.java)
                startActivity(intent)
            }
        }
    }

    private fun setupUI() {
        binding.rowDefaultKeyboard.setOnClickListener {
            val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }

        updateToggle(binding.toggleXt9, preferences.xt9Enabled)
        binding.rowXt9.setOnClickListener {
            preferences.xt9Enabled = !preferences.xt9Enabled
            updateToggle(binding.toggleXt9, preferences.xt9Enabled)
        }

        updateToggle(binding.toggleHaptic, preferences.hapticEnabled)
        binding.rowHaptic.setOnClickListener {
            preferences.hapticEnabled = !preferences.hapticEnabled
            updateToggle(binding.toggleHaptic, preferences.hapticEnabled)
        }

        binding.boxHapticIntensity.text = preferences.hapticDuration.toString()
        binding.rowHapticIntensity.setOnClickListener {
            dialogHelper.showSliderDialog("Haptic Intensity", 0f, 50f, preferences.hapticDuration.toFloat()) { newValue ->
                preferences.hapticDuration = newValue.toInt()
                binding.boxHapticIntensity.text = newValue.toInt().toString()
            }
        }

        binding.boxDeleteSpeed.text = preferences.deletionSpeed.toString()
        binding.rowDeleteSpeed.setOnClickListener {
            dialogHelper.showSliderDialog("Delete Speed", 0f, 100f, preferences.deletionSpeed.toFloat()) { newValue ->
                preferences.deletionSpeed = newValue.toInt()
                binding.boxDeleteSpeed.text = newValue.toInt().toString()
            }
        }

        updateToggle(binding.toggleSound, preferences.soundEnabled)
        binding.rowSound.setOnClickListener {
            preferences.soundEnabled = !preferences.soundEnabled
            updateToggle(binding.toggleSound, preferences.soundEnabled)
        }

        binding.boxSoundVolume.text = (preferences.soundVolume * 100).toInt().toString()
        binding.rowSoundVolume.setOnClickListener {
            dialogHelper.showSliderDialog("Sound Volume", 0f, 100f, preferences.soundVolume * 100) { newValue ->
                preferences.soundVolume = newValue / 100f
                binding.boxSoundVolume.text = newValue.toInt().toString()
            }
        }

        binding.boxTimeout.text = preferences.multiTapTimeout.toString()
        binding.rowTimeout.setOnClickListener {
            dialogHelper.showSliderDialog("Multi-tap timeout", 0f, 800f, preferences.multiTapTimeout.toFloat()) { newValue ->
                preferences.multiTapTimeout = newValue.toLong()
                binding.boxTimeout.text = newValue.toInt().toString()
            }
        }

        binding.boxKeyFont.text = preferences.keyFontSize.toString()
        binding.rowKeyFont.setOnClickListener {
            dialogHelper.showSliderDialog("Key label font size (sp)", 0f, 40f, preferences.keyFontSize.toFloat()) { newValue ->
                preferences.keyFontSize = newValue.toInt()
                binding.boxKeyFont.text = newValue.toInt().toString()
            }
        }

        binding.boxSuggestionFont.text = preferences.suggestionFontSize.toString()
        binding.rowSuggestionFont.setOnClickListener {
            dialogHelper.showSliderDialog("Suggestion Font Size (sp)", 0f, 30f, preferences.suggestionFontSize.toFloat()) { newValue ->
                preferences.suggestionFontSize = newValue.toInt()
                binding.boxSuggestionFont.text = newValue.toInt().toString()
            }
        }

        binding.boxEmojiSize.text = preferences.emojiSize.toString()
        binding.rowEmojiSize.setOnClickListener {
            dialogHelper.showSliderDialog("Emoji Size (sp)", 16f, 40f, preferences.emojiSize.toFloat()) { newValue ->
                preferences.emojiSize = newValue.toInt()
                binding.boxEmojiSize.text = newValue.toInt().toString()
            }
        }

        updateThemeText()
        binding.spinnerTheme.setOnClickListener {
            dialogHelper.showThemeDialog { which ->
                preferences.theme = which
                applyTheme(which)
                updateThemeText()
            }
        }

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

        binding.btnClearDict.setOnClickListener {
            LearnedDictionary.clear()
            Toast.makeText(this, "Learned dictionary cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateToggle(imageView: ImageView, isEnabled: Boolean) {
        val accentColor = ContextCompat.getColor(this, accentColors[preferences.accentColorIndex])
        if (isEnabled) {
            imageView.setImageResource(R.drawable.ic_toggle_on)
            androidx.core.widget.ImageViewCompat.setImageTintList(imageView, ColorStateList.valueOf(accentColor))
        } else {
            imageView.setImageResource(R.drawable.ic_toggle_off)
            androidx.core.widget.ImageViewCompat.setImageTintList(imageView, ColorStateList.valueOf(Color.parseColor("#444444")))
        }
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
        val density = resources.displayMetrics.density
        val size = (31 * density).toInt()
        val margin = (4 * density).toInt()

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
                    val p = (8 * density).toInt()
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
        val color = ContextCompat.getColor(this, accentColors[preferences.accentColorIndex])
        val headers = listOf(
            binding.headerInputMode,
            binding.headerFeedback,
            binding.headerLayout,
            binding.headerTheme,
            binding.headerIntegrations,
            binding.headerEmoji
        )
        headers.forEach { it.setTextColor(color) }
        binding.btnClearDict.strokeColor = ColorStateList.valueOf(color)
    }

    private fun applyUbuntuFont() {
        val ubuntu = FontUtils.getUbuntu(this)
        val views = listOf(
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
            binding.headerEmoji,
            binding.txtEmojiSizeTitle,
            binding.txtEmojiSizeSubtitle,
            binding.boxEmojiSize,
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
            binding.txtDefaultKeyboardTitle,
            binding.txtDefaultKeyboardSubtitle,
            binding.btnClearDict,
            binding.txtClearDictDesc
        )
        views.forEach { it.typeface = ubuntu }
    }
}
