package com.musa.t9keyboard

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.musa.t9keyboard.databinding.ActivitySettingsBinding
import com.google.android.material.slider.Slider

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var preferences: PreferencesManager

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

        setupToolbar()
        setupUI()
        setupAccentColorSelector()
        applyAccentColor()
        handleInsets()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.settings_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_theme) {
            showThemeDialog()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showThemeDialog() {
        val options = arrayOf("Light", "Dark", "Follow System")
        val checkedItem = preferences.theme // 0, 1, 2

        AlertDialog.Builder(this)
            .setTitle("Select Theme")
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                preferences.theme = which
                applyTheme(which)
                dialog.dismiss()
            }
            .show()
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
            binding.toolbar.setPadding(0, systemBars.top, 0, 0)
            insets
        }
    }

    private fun setupUI() {
        // XT9 Predictive Text
        binding.checkXt9.isChecked = preferences.xt9Enabled
        binding.checkXt9.setOnCheckedChangeListener { _, isChecked ->
            preferences.xt9Enabled = isChecked
        }

        // Haptic feedback
        binding.checkHaptic.isChecked = preferences.hapticEnabled
        binding.checkHaptic.setOnCheckedChangeListener { _, isChecked ->
            preferences.hapticEnabled = isChecked
        }

        binding.seekHapticDuration.value = preferences.hapticDuration.toFloat().coerceIn(0f, 100f)
        binding.valHapticDuration.text = "${preferences.hapticDuration}ms"
        binding.seekHapticDuration.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                preferences.hapticDuration = value.toInt()
                binding.valHapticDuration.text = "${value.toInt()}ms"
            }
        }

        // Key press sound
        binding.checkSound.isChecked = preferences.soundEnabled
        binding.checkSound.setOnCheckedChangeListener { _, isChecked ->
            preferences.soundEnabled = isChecked
        }

        binding.seekSoundVolume.value = (preferences.soundVolume * 100).coerceIn(0f, 100f)
        binding.valSoundVolume.text = "${(preferences.soundVolume * 100).toInt()}%"
        binding.seekSoundVolume.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                preferences.soundVolume = value / 100f
                binding.valSoundVolume.text = "${value.toInt()}%"
            }
        }

        // Multi-tap timeout
        binding.seekTimeout.value = preferences.multiTapTimeout.toFloat().coerceIn(400f, 1200f)
        binding.valTimeout.text = "${preferences.multiTapTimeout}ms"
        binding.seekTimeout.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                preferences.multiTapTimeout = value.toLong()
                binding.valTimeout.text = "${value.toInt()}ms"
            }
        }

        // Key label font size
        binding.seekKeyFont.value = preferences.keyFontSize.toFloat().coerceIn(10f, 40f)
        binding.valKeyFont.text = "${preferences.keyFontSize}sp"
        binding.seekKeyFont.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                preferences.keyFontSize = value.toInt()
                binding.valKeyFont.text = "${value.toInt()}sp"
            }
        }

        // Suggestion font size
        binding.seekSuggestionFont.value = preferences.suggestionFontSize.toFloat().coerceIn(10f, 30f)
        binding.valSuggestionFont.text = "${preferences.suggestionFontSize}sp"
        binding.seekSuggestionFont.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                preferences.suggestionFontSize = value.toInt()
                binding.valSuggestionFont.text = "${value.toInt()}sp"
            }
        }

        // Clear dictionary
        binding.btnClearDict.setOnClickListener {
            LearnedDictionary.clear()
            Toast.makeText(this, "Learned dictionary cleared", Toast.LENGTH_SHORT).show()
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
            }

            binding.accentColorsLayout.addView(frame)
        }
    }

    private fun applyAccentColor() {
        val accentColor = ContextCompat.getColor(this, accentColors[preferences.accentColorIndex])
        val colorStateList = ColorStateList.valueOf(accentColor)

        // Headers
        binding.headerInputMode.setTextColor(accentColor)
        binding.headerFeedback.setTextColor(accentColor)
        binding.headerLayout.setTextColor(accentColor)
        binding.headerAccent.setTextColor(accentColor)
        binding.headerDictionary.setTextColor(accentColor)

        // Switches
        binding.checkXt9.thumbTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Color.WHITE, Color.LTGRAY)
        )
        binding.checkXt9.trackTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(accentColor, Color.parseColor("#444444"))
        )

        binding.checkHaptic.thumbTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Color.WHITE, Color.LTGRAY)
        )
        binding.checkHaptic.trackTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(accentColor, Color.parseColor("#444444"))
        )

        binding.checkSound.thumbTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Color.WHITE, Color.LTGRAY)
        )
        binding.checkSound.trackTintList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(accentColor, Color.parseColor("#444444"))
        )

        // Sliders
        val sliders = listOf(
            binding.seekHapticDuration,
            binding.seekSoundVolume,
            binding.seekTimeout,
            binding.seekKeyFont,
            binding.seekSuggestionFont
        )
        sliders.forEach {
            it.thumbTintList = colorStateList
            it.trackActiveTintList = colorStateList
            it.trackInactiveTintList = ColorStateList.valueOf(Color.parseColor("#444444"))
        }

        // Dynamic labels
        binding.valTimeout.setTextColor(accentColor)
        binding.valKeyFont.setTextColor(accentColor)
        binding.valSuggestionFont.setTextColor(accentColor)

        // Clear dictionary button
        binding.btnClearDict.strokeColor = colorStateList
    }
}
