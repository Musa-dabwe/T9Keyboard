package com.musa.t9keyboard

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.musa.t9keyboard.utils.ImeUtils

/**
 * Launcher entry point. Routes into the WebView-hosted UI: first-run users
 * land on the setup wizard, configured users land on settings.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val preferences = PreferencesManager(this)
        if (ImeUtils.isFullyConfigured(this)) {
            preferences.setupComplete = true
        }
        val screen = if (preferences.setupComplete) "settings" else "setup"

        startActivity(Intent(this, WebViewActivity::class.java).apply {
            putExtra(WebViewActivity.EXTRA_SCREEN, screen)
        })
        finish()
    }
}
