package com.musa.t9keyboard

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // SetupActivity now handles theme initialization and redirection logic.
        // MainActivity serves as a legacy entry point redirecting to SetupActivity.
        val intent = Intent(this, SetupActivity::class.java)
        startActivity(intent)
        finish()
    }
    
}
