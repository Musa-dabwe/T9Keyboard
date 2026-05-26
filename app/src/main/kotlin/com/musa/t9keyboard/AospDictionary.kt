package com.musa.t9keyboard

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.TreeMap

enum class WordCategory {
    PROTECTED,  // Single-letter words (I, a)
    BASE,       // Multi-character base dictionary words
    LEARNED     // User-learned words not in base dictionary
}

object AospDictionary {
    data class WordEntry(val stripped: String, val frequency: Int, val display: String) {
        val word: String get() = if (display.isNotEmpty()) display else stripped
    }

    private val t9Map = TreeMap<String, MutableList<WordEntry>>()
    private val exactT9Map = HashMap<String, MutableList<WordEntry>>()
    private val wordToDigits = HashMap<String, String>()
    private val wordMap = mutableMapOf<String, MutableList<WordEntry>>()
    private val allWordEntries = mutableListOf<WordEntry>()

    data class WordSuggestion(
        val word: String,
        val frequency: Int,
        val category: WordCategory = WordCategory.BASE
    )

    private val digitMap = mapOf(
        'a' to '2', 'b' to '2', 'c' to '2',
        'd' to '3', 'e' to '3', 'f' to '3',
        'g' to '4', 'h' to '4', 'i' to '4',
        'j' to '5', 'k' to '5', 'l' to '5',
        'm' to '6', 'n' to '6', 'o' to '6',
        'p' to '7', 'q' to '7', 'r' to '7', 's' to '7',
        't' to '8', 'u' to '8', 'v' to '8',
        'w' to '9', 'x' to '9', 'y' to '9', 'z' to '9'
    )

    suspend fun loadFromAssets(context: Context) = withContext(Dispatchers.IO) {
        synchronized(this@AospDictionary) {
            t9Map.clear()
            exactT9Map.clear()
            wordToDigits.clear()
            wordMap.clear()
            allWordEntries.clear()
        }

        val reader = try {
            BufferedReader(InputStreamReader(context.assets.open("en_us_words.txt")))
        } catch (e: Exception) {
            Log.e("AospDictionary", "Error opening dictionary", e)
            return@withContext
        }

        reader.use { r ->
            var line: String? = r.readLine()
            while (line != null) {
                try {
                    val parts = line.split("\t")
                    if (parts.size >= 2) {
                        val word = parts[0]
                        // Robust frequency parsing as per instructions
                        val freqStr = parts[1].split(" ")[0]
                        val freq = freqStr.toInt()

                        // Per instructions, display is the same as word
                        val display = word
                        val stripped = word.lowercase().filter { it in 'a'..'z' }
                        val entry = WordEntry(stripped, freq, display)

                        synchronized(this@AospDictionary) {
                            wordMap.getOrPut(stripped) { mutableListOf() }.add(entry)
                            allWordEntries.add(entry)

                            val digits = getT9Sequence(word)
                            if (digits.isNotEmpty()) {
                                t9Map.getOrPut(digits) { mutableListOf() }.add(entry)
                                exactT9Map.getOrPut(digits) { mutableListOf() }.add(entry)
                                wordToDigits[word.lowercase()] = digits
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("AospDictionary", "Skipping malformed line: $line", e)
                }
                line = r.readLine()
            }
        }
    }

    private fun getT9Sequence(word: String): String {
        return word.lowercase().filter { it in 'a'..'z' }.map { digitMap[it] ?: ' ' }.joinToString("").trim()
    }

    @Synchronized
    fun getWordFrequency(word: String): Int {
        if (t9Map.isEmpty()) return 0
        val lower = word.lowercase().trim()
        val stripped = lower.filter { it in 'a'..'z' }
        return wordMap[stripped]?.find { it.word.lowercase() == lower }?.frequency ?: 0
    }

    @Synchronized
    fun contains(word: String): Boolean {
        if (t9Map.isEmpty()) return false
        val lower = word.lowercase().trim()
        val stripped = lower.filter { it in 'a'..'z' }
        return wordMap[stripped]?.any { it.word.lowercase() == lower } ?: false
    }

    @Synchronized
    fun containsWord(word: String): Boolean = contains(word)

    private fun categorizeWord(word: String): WordCategory {
        val stripped = word.lowercase().filter { it in 'a'..'z' }
        return if (stripped.length == 1) {
            WordCategory.PROTECTED
        } else {
            WordCategory.BASE
        }
    }


    @Synchronized
    fun getSuggestionsForSequence(t9sequence: String): List<WordSuggestion> {
        if (exactT9Map.isEmpty()) return emptyList()
        val entries = exactT9Map[t9sequence] ?: emptyList<WordEntry>()
        val results = entries.map {
            WordSuggestion(it.word, it.frequency, categorizeWord(it.word))
        }.toMutableList()

        return results.sortedByDescending { it.frequency }
            .distinctBy { it.word.lowercase() }
    }

    @Synchronized
    fun getWordsStartingWith(prefix: String): List<WordSuggestion> {
        if (t9Map.isEmpty()) return emptyList()
        if (prefix.length > 12) return emptyList()
        val potentialMatches = t9Map.subMap(prefix, prefix + "\uFFFF")
        return potentialMatches.values
            .flatMap { entries ->
                entries.map {
                    WordSuggestion(it.word, it.frequency, categorizeWord(it.word))
                }
            }
            .sortedByDescending { it.frequency }
            .distinctBy { it.word.lowercase() }
    }

    @Synchronized
    fun getWordsContaining(literal: String): List<WordSuggestion> {
        if (t9Map.isEmpty()) return emptyList()
        if (literal.isEmpty()) return emptyList()
        val lowerLiteral = literal.lowercase()
        return allWordEntries
            .asSequence()
            .filter { it.word.lowercase().contains(lowerLiteral) }
            .take(30)
            .map {
                WordSuggestion(it.word, it.frequency, categorizeWord(it.word))
            }
            .sortedByDescending { it.frequency }
            .distinctBy { it.word.lowercase() }
            .toList()
    }

    @Synchronized
    fun getSuggestions(constraints: List<String>): List<WordSuggestion> {
        if (exactT9Map.isEmpty()) return emptyList()
        if (constraints.isEmpty()) return emptyList()

        val digitSequence = constraints.map {
            if (it.length == 1 && it[0].isDigit()) it else (digitMap[it[0]] ?: ' ')
        }.joinToString("").trim()

        // Optimization: if sequence is long, don't do prefix searching to avoid iterating keys
        if (digitSequence.length > 12) {
             val results = mutableListOf<WordSuggestion>()
             exactT9Map[digitSequence]?.forEach { entry ->
                 var matches = true
                 for (i in constraints.indices) {
                     val constraint = constraints[i]
                     if (constraint.length == 1 && !constraint[0].isDigit()) {
                         if (entry.stripped.length <= i || entry.stripped[i] != constraint[0]) {
                             matches = false
                             break
                         }
                     }
                 }
                 if (matches) {
                     results.add(WordSuggestion(entry.word, entry.frequency, categorizeWord(entry.word)))
                 }
             }
             return results
        }

        val potentialMatches = t9Map.subMap(digitSequence, digitSequence + "\uFFFF")
        val results = mutableListOf<WordSuggestion>()

        for (entries in potentialMatches.values) {
            for (entry in entries) {
                var matches = true
                val word = entry.stripped
                for (i in constraints.indices) {
                    val constraint = constraints[i]
                    if (constraint.length == 1 && !constraint[0].isDigit()) {
                        if (word.length <= i || word[i] != constraint[0]) {
                            matches = false
                            break
                        }
                    }
                }
                if (matches) {
                    results.add(WordSuggestion(entry.word, entry.frequency, categorizeWord(entry.word)))
                }
            }
        }
        return results
    }
}
