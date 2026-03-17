package com.musa.t9keyboard

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

object AospBigrams {
    private val bigramMap = mutableMapOf<String, MutableList<String>>()

    suspend fun loadFromAssets(context: Context) = withContext(Dispatchers.IO) {
        try {
            val localBigramMap = mutableMapOf<String, MutableList<String>>()
            val reader = BufferedReader(InputStreamReader(context.assets.open("en_us_bigrams.txt")))
            var line: String? = reader.readLine()
            while (line != null) {
                try {
                    val parts = line.split("\t")
                    if (parts.size >= 3) {
                        val word = parts[0]
                        val bigram = parts[1]
                        // Parsing frequency for robustness as per instructions
                        val freq = parts[2].split(" ")[0].toInt()

                        localBigramMap.getOrPut(word) { mutableListOf() }.add(bigram)
                    }
                } catch (e: Exception) {
                    Log.w("AospBigrams", "Skipping malformed line: $line", e)
                }
                line = reader.readLine()
            }
            reader.close()

            synchronized(this@AospBigrams) {
                bigramMap.clear()
                bigramMap.putAll(localBigramMap)
            }
        } catch (e: Exception) {
            Log.e("AospBigrams", "Error loading bigrams", e)
        }
    }

    @Synchronized
    fun getNextWordSuggestions(previousWord: String): List<String> {
        return bigramMap[previousWord.lowercase().trim()] ?: emptyList()
    }
}
