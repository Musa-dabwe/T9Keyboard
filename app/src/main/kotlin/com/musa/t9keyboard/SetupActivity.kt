package com.musa.t9keyboard

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.musa.t9keyboard.utils.ImeUtils

class SetupActivity : AppCompatActivity() {

    private lateinit var btnActivate: MaterialButton
    private lateinit var btnSetDefault: MaterialButton
    private lateinit var fabClose: FloatingActionButton
    private lateinit var setupStateHandler: Handler
    private lateinit var preferences: PreferencesManager

    private val stateChecker = object : Runnable {
        override fun run() {
            updateButtonStates()
            setupStateHandler.postDelayed(this, 500L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        preferences = PreferencesManager(this)

        // Theme initialization moved from MainActivity
        val mode = when (preferences.theme) {
            0 -> AppCompatDelegate.MODE_NIGHT_NO
            1 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)

        super.onCreate(savedInstanceState)

        // Guard — if already fully set up, go straight to SettingsActivity
        if (ImeUtils.isFullyConfigured(this)) {
            markSetupComplete()
            launchMainAndFinish()
            return
        }

        supportActionBar?.hide()
        setContentView(R.layout.activity_setup)

        btnActivate  = findViewById(R.id.btn_activate)
        btnSetDefault = findViewById(R.id.btn_set_default)
        fabClose     = findViewById(R.id.fab_close)

        setupStateHandler = Handler(Looper.getMainLooper())

        btnActivate.setOnClickListener {
            // Opens system Manage Keyboards settings
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        btnSetDefault.setOnClickListener {
            // Shows system keyboard picker popup
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        fabClose.setOnClickListener {
            if (ImeUtils.isFullyConfigured(this)) {
                markSetupComplete()
                launchMainAndFinish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateButtonStates()
        setupStateHandler.postDelayed(stateChecker, 500L)
    }

    override fun onPause() {
        super.onPause()
        setupStateHandler.removeCallbacks(stateChecker)
    }

    private fun updateButtonStates() {
        val isEnabled  = ImeUtils.isImeEnabled(this)
        val isDefault  = ImeUtils.isImeDefault(this)
        val bothDone   = isEnabled && isDefault

        val colorAccent = ContextCompat.getColor(this, R.color.setup_accent)
        val colorSuccess = ContextCompat.getColor(this, R.color.setup_success)

        // Activate button: accent if not done, success if done
        btnActivate.backgroundTintList = ColorStateList.valueOf(
            if (isEnabled) colorSuccess else colorAccent
        )

        // Set Default button: accent if not done, success if done
        btnSetDefault.backgroundTintList = ColorStateList.valueOf(
            if (isDefault) colorSuccess else colorAccent
        )

        // FAB: success and enabled only when both done, accent and visually inert otherwise
        fabClose.backgroundTintList = ColorStateList.valueOf(
            if (bothDone) colorSuccess else colorAccent
        )
        fabClose.alpha = if (bothDone) 1.0f else 0.5f

        // Auto-close: if both conditions met and user returns to screen
        if (bothDone) {
            markSetupComplete()
            launchMainAndFinish()
        }
    }

    private fun markSetupComplete() {
        preferences.setupComplete = true
    }

    private fun launchMainAndFinish() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
        finish()
    }
}
