package com.musa.t9keyboard.utils

import android.content.Context
import android.provider.Settings

object ImeUtils {
    fun isImeEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_INPUT_METHODS
        ) ?: return false
        return enabled.contains(context.packageName)
    }

    fun isImeDefault(context: Context): Boolean {
        val default = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: return false
        return default.contains(context.packageName)
    }

    fun isFullyConfigured(context: Context) =
        isImeEnabled(context) && isImeDefault(context)
}
