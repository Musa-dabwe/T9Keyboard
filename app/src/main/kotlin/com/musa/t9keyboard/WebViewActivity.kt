package com.musa.t9keyboard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.musa.t9keyboard.utils.ImeUtils
import org.json.JSONArray
import org.json.JSONObject

/**
 * Hosts every PoetBoard screen (setup, settings, privacy, logs, about) as a
 * pastel HTML page from assets/, replacing the old native Activities. Pages
 * talk to the app through the [Bridge] JavaScript interface, and navigate
 * between each other with plain links.
 */
class WebViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SCREEN = "screen"
        private val ALLOWED_SCREENS = setOf("setup", "settings", "privacy", "logs", "about")
        private const val PERMISSION_REQUEST_CONTACTS = 101
    }

    private lateinit var webView: WebView
    private lateinit var preferences: PreferencesManager

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = PreferencesManager(this)
        LearnedDictionary.load(this)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.parseColor("#EEF5FD")
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true

        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.setBackgroundColor(Color.parseColor("#EEF5FD"))
        webView.addJavascriptInterface(Bridge(), "Android")
        setContentView(webView)

        val screen = intent.getStringExtra(EXTRA_SCREEN)?.takeIf { it in ALLOWED_SCREENS } ?: "settings"
        webView.loadUrl("file:///android_asset/$screen.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // Pages that poll system state (setup) refresh themselves on focus,
        // but returning from system settings doesn't always refocus the page.
        webView.evaluateJavascript("window.onAppResume && window.onAppResume()", null)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CONTACTS) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            preferences.contactSuggestionsEnabled = granted
            if (granted) Thread { ContactsDictionary.load(this) }.start()
            webView.evaluateJavascript(
                "window.onContactsPermissionResult && window.onContactsPermissionResult($granted)", null
            )
        }
    }

    /** All methods run on the WebView's JS bridge thread unless noted. */
    inner class Bridge {

        // ---------- Theme ----------

        @JavascriptInterface
        fun getThemeId(): String = preferences.keyboardThemeId

        @JavascriptInterface
        fun setThemeId(id: String) {
            if (KeyboardThemes.byId(id) != null) preferences.keyboardThemeId = id
        }

        @JavascriptInterface
        fun getThemeList(): String {
            val arr = JSONArray()
            KeyboardThemes.ALL.forEach { t ->
                arr.put(JSONObject().apply {
                    put("id", t.id)
                    put("name", t.displayName)
                })
            }
            return arr.toString()
        }

        // ---------- IME status / system navigation ----------

        @JavascriptInterface
        fun isImeEnabled(): Boolean = ImeUtils.isImeEnabled(this@WebViewActivity)

        @JavascriptInterface
        fun isImeDefault(): Boolean = ImeUtils.isImeDefault(this@WebViewActivity)

        @JavascriptInterface
        fun openImeSettings() {
            runOnUiThread {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }

        @JavascriptInterface
        fun showImePicker() {
            runOnUiThread {
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showInputMethodPicker()
            }
        }

        @JavascriptInterface
        fun isSetupComplete(): Boolean = preferences.setupComplete

        @JavascriptInterface
        fun markSetupComplete() {
            preferences.setupComplete = true
        }

        // ---------- Settings ----------

        @JavascriptInterface
        fun getSettings(): String = JSONObject().apply {
            put("xt9Enabled", preferences.xt9Enabled)
            put("hapticEnabled", preferences.hapticEnabled)
            put("hapticDuration", preferences.hapticDuration)
            put("soundEnabled", preferences.soundEnabled)
            put("soundVolume", (preferences.soundVolume * 100).toInt())
            put("deletionSpeed", preferences.deletionSpeed)
            put("multiTapTimeout", preferences.multiTapTimeout)
            put("keyFontSize", preferences.keyFontSize)
            put("suggestionFontSize", preferences.suggestionFontSize)
            put("emojiSize", preferences.emojiSize)
            put("contactsEnabled", preferences.contactSuggestionsEnabled)
        }.toString()

        @JavascriptInterface
        fun saveSetting(key: String, value: String) {
            when (key) {
                "xt9Enabled" -> preferences.xt9Enabled = value.toBoolean()
                "hapticEnabled" -> preferences.hapticEnabled = value.toBoolean()
                "hapticDuration" -> value.toIntOrNull()?.let { preferences.hapticDuration = it.coerceIn(0, 50) }
                "soundEnabled" -> preferences.soundEnabled = value.toBoolean()
                "soundVolume" -> value.toIntOrNull()?.let { preferences.soundVolume = it.coerceIn(0, 100) / 100f }
                "deletionSpeed" -> value.toIntOrNull()?.let { preferences.deletionSpeed = it.coerceIn(0, 100) }
                "multiTapTimeout" -> value.toLongOrNull()?.let { preferences.multiTapTimeout = it.coerceIn(0L, 800L) }
                "keyFontSize" -> value.toIntOrNull()?.let { preferences.keyFontSize = it.coerceIn(0, 40) }
                "suggestionFontSize" -> value.toIntOrNull()?.let { preferences.suggestionFontSize = it.coerceIn(0, 30) }
                "emojiSize" -> value.toIntOrNull()?.let { preferences.emojiSize = it.coerceIn(16, 40) }
                "contactsEnabled" -> setContactsEnabled(value.toBoolean())
            }
        }

        private fun setContactsEnabled(enabled: Boolean) {
            if (!enabled) {
                preferences.contactSuggestionsEnabled = false
                ContactsDictionary.clear()
                return
            }
            if (ContextCompat.checkSelfPermission(this@WebViewActivity, Manifest.permission.READ_CONTACTS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                preferences.contactSuggestionsEnabled = true
                Thread { ContactsDictionary.load(this@WebViewActivity) }.start()
            } else {
                runOnUiThread {
                    ActivityCompat.requestPermissions(
                        this@WebViewActivity,
                        arrayOf(Manifest.permission.READ_CONTACTS),
                        PERMISSION_REQUEST_CONTACTS
                    )
                }
            }
        }

        @JavascriptInterface
        fun clearDictionary() {
            LearnedDictionary.clear()
        }

        // ---------- Privacy / app blacklist ----------

        @JavascriptInterface
        fun getBlacklist(): String {
            val pm = packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val arr = JSONArray()
            try {
                pm.queryIntentActivities(launcher, 0)
                    .filter { it.activityInfo.packageName != packageName }
                    .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                    .distinctBy { it.first }
                    .sortedBy { it.second.lowercase() }
                    .forEach { (pkg, label) ->
                        arr.put(JSONObject().apply {
                            put("packageName", pkg)
                            put("appName", label)
                            put("blacklisted", preferences.isAppBlacklisted(pkg))
                        })
                    }
            } catch (_: Exception) {
                // Return whatever resolved before the failure
            }
            return arr.toString()
        }

        @JavascriptInterface
        fun setBlacklisted(packageName: String, blacklisted: Boolean) {
            val current = preferences.blacklistedApps.toMutableSet()
            if (blacklisted) current.add(packageName) else current.remove(packageName)
            preferences.blacklistedApps = current
        }

        // ---------- Logs ----------

        @JavascriptInterface
        fun getLogs(): String = CrashLogger.getLogs(this@WebViewActivity)

        @JavascriptInterface
        fun clearLogs() = CrashLogger.clearLogs(this@WebViewActivity)

        // ---------- About ----------

        @JavascriptInterface
        fun getVersion(): String = BuildConfig.VERSION_NAME
    }
}
