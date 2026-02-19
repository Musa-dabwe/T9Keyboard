package com.musa.t9keyboard

import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.musa.t9keyboard.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var preferences: PreferencesManager
    private lateinit var dictionary: T9Dictionary

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = PreferencesManager(this)
        dictionary = T9Dictionary(this)

        setupUI()
    }

    private fun setupUI() {
        binding.checkHaptic.isChecked = preferences.hapticEnabled
        binding.checkHaptic.setOnCheckedChangeListener { _, isChecked ->
            preferences.hapticEnabled = isChecked
        }

        binding.seekHapticDuration.progress = preferences.hapticDuration
        binding.seekHapticDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) preferences.hapticDuration = progress
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.checkSound.isChecked = preferences.soundEnabled
        binding.checkSound.setOnCheckedChangeListener { _, isChecked ->
            preferences.soundEnabled = isChecked
        }

        binding.seekSoundVolume.progress = (preferences.soundVolume * 100).toInt()
        binding.seekSoundVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) preferences.soundVolume = progress / 100f
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekTimeout.progress = preferences.multiTapTimeout.toInt()
        binding.seekTimeout.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) preferences.multiTapTimeout = progress.toLong()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekKeyFont.progress = preferences.keyFontSize
        binding.seekKeyFont.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) preferences.keyFontSize = progress
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.seekSuggestionFont.progress = preferences.suggestionFontSize
        binding.seekSuggestionFont.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) preferences.suggestionFontSize = progress
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.btnClearDict.setOnClickListener {
            dictionary.clearLearnedDictionary()
            Toast.makeText(this, "Learned dictionary cleared", Toast.LENGTH_SHORT).show()
        }
    }
}
