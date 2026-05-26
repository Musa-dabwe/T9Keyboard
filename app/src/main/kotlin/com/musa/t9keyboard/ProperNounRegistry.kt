package com.musa.t9keyboard

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Registry of proper nouns that should preserve capitalization.
 * Includes languages, countries, deity names, and other culturally significant terms.
 */
object ProperNounRegistry {
    private val properNouns = mutableSetOf<String>()
    private var isLoaded = false

    /**
     * Load proper nouns from assets. Call once at app startup.
     */
    @Synchronized
    fun load(context: Context) {
        if (isLoaded) return

        try {
            val inputStream = context.resources.openRawResource(R.raw.proper_nouns)
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.useLines { lines ->
                lines.forEach { line ->
                    val word = line.trim().lowercase()
                    if (word.isNotEmpty()) {
                        properNouns.add(word)
                    }
                }
            }
            isLoaded = true
        } catch (e: Exception) {
            android.util.Log.e("ProperNounRegistry", "Failed to load proper nouns", e)
        }
    }

    /**
     * Check if a word is a proper noun.
     * @param word Word to check (case-insensitive)
     * @return true if the word is a registered proper noun
     */
    @Synchronized
    fun contains(word: String): Boolean {
        return properNouns.contains(word.lowercase())
    }

    /**
     * Check if a word is the pronoun "I".
     */
    fun isPronounI(word: String): Boolean {
        return word.lowercase() == "i"
    }
}
