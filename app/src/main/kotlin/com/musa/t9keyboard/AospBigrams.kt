package com.musa.t9keyboard

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

object AospBigrams {
    private val bigramMap = mutableMapOf<String, List<String>>()

    fun loadFromAssets(context: Context) {
        try {
            bigramMap.clear()
            val reader = BufferedReader(InputStreamReader(context.assets.open("en_us_bigrams.bin")))
            var line: String? = reader.readLine()
            while (line != null) {
                val parts = line.split("\t")
                if (parts.size == 2) {
                    val word = parts[0]
                    val nextWords = parts[1].split(",")
                    bigramMap[word] = nextWords
                }
                line = reader.readLine()
            }
            reader.close()
        } catch (e: Exception) {
            Log.e("AospBigrams", "Error loading bigrams", e)
        }
    }

    fun getNextWordSuggestions(previousWord: String): List<String> {
        return bigramMap[previousWord.lowercase().trim()] ?: emptyList()
    }
}
