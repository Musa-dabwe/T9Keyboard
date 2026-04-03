package com.musa.t9keyboard

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class DebugLogsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_logs)

        val txtLogs = findViewById<TextView>(R.id.txt_logs)
        val btnClear = findViewById<MaterialButton>(R.id.btn_clear_logs)

        txtLogs.text = CrashLogger.getLogs(this)

        btnClear.setOnClickListener {
            CrashLogger.clearLogs(this)
            txtLogs.text = "No logs found."
        }
    }
}
