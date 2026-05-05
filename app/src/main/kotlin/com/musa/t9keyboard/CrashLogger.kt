package com.musa.t9keyboard

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object CrashLogger {
    private const val LOG_FILE_NAME = "t9_errors.log"
    private const val MAX_LINES = 200
    private val TAG = "CrashLogger"

    // Only strip lowercase natural language words (6+ chars), leave class names/methods/paths alone
    private fun sanitizeMessage(message: String): String {
        return message.replace(Regex("\\b[a-z]{6,}\\b"), "***")
    }

    fun log(tag: String, throwable: Throwable, context: Context) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val rawMessage = throwable.message ?: ""
        val sanitizedMessage = sanitizeMessage(rawMessage)
        val logEntry = "[$timestamp] $tag: ${throwable::class.java.simpleName}: $sanitizedMessage"

        try {
            // Internal storage filesDir is private to the app (MODE_PRIVATE behavior)
            val file = File(context.filesDir, LOG_FILE_NAME)
            val lines = if (file.exists()) file.readLines() else emptyList()

            val newLines = lines.toMutableList()
            newLines.add(logEntry)

            // Rotation: cap at 200 lines
            val finalLines = if (newLines.size > MAX_LINES) {
                newLines.takeLast(MAX_LINES)
            } else {
                newLines
            }

            file.writeText(finalLines.joinToString("\n") + "\n")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
        }
    }

    fun getLogs(context: Context): String {
        return try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            if (file.exists()) file.readText() else "No logs found."
        } catch (e: Exception) {
            "Error reading logs: ${e.message}"
        }
    }

    fun clearLogs(context: Context) {
        try {
            val file = File(context.filesDir, LOG_FILE_NAME)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear logs", e)
        }
    }
}
