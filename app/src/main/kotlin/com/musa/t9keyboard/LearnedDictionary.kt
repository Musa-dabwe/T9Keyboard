package com.musa.t9keyboard

import android.content.Context
import android.content.SharedPreferences
import java.util.TreeMap

object LearnedDictionary {
    private val learnedWords = mutableMapOf<String, Int>()
    private val nextWordMap = mutableMapOf<String, MutableMap<String, Int>>()
    private val t9Map = TreeMap<String, MutableList<String>>()
    private lateinit var prefs: SharedPreferences

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

    @Synchronized
    fun load(context: Context) {
        prefs = context.getSharedPreferences("learned_words", Context.MODE_PRIVATE)
        learnedWords.clear()
        nextWordMap.clear()
        t9Map.clear()

        prefs.all.forEach { (key, value) ->
            if (key.startsWith("freq_")) {
                val word = key.substring(5)
                val freq = value as Int
                learnedWords[word] = freq
                addToT9Map(word)
            } else if (key.startsWith("next_")) {
                val parts = key.substring(5).split("__")
                if (parts.size == 2) {
                    val prev = parts[0]
                    val next = parts[1]
                    nextWordMap.getOrPut(prev) { mutableMapOf() }[next] = value as Int
                }
            }
        }
    }

    private fun addToT9Map(word: String) {
        val sequence = getT9Sequence(word)
        if (sequence.isNotEmpty()) {
            val list = t9Map.getOrPut(sequence) { mutableListOf() }
            if (!list.contains(word)) {
                list.add(word)
            }
        }
    }

    private fun getT9Sequence(word: String): String {
        return word.lowercase().filter { it in 'a'..'z' }.map { digitMap[it] ?: ' ' }.joinToString("").trim()
    }

    @Synchronized
    fun learnWord(word: String, previousWord: String? = null) {
        val lowerWord = word.lowercase().trim() // Keep punctuation for learning!
        if (lowerWord.isEmpty()) return

        val newFreq = (learnedWords[lowerWord] ?: 0) + 1
        learnedWords[lowerWord] = newFreq

        // Add to T9 map if it's the first time
        if (newFreq == 1) {
            addToT9Map(lowerWord)
        }

        if (previousWord != null) {
            val lowerPrev = previousWord.lowercase().trim()
            if (lowerPrev.isNotEmpty()) {
                val nextMap = nextWordMap.getOrPut(lowerPrev) { mutableMapOf() }
                nextMap[lowerWord] = (nextMap[lowerWord] ?: 0) + 1
            }
        }

        save()
    }

    private fun save() {
        if (!::prefs.isInitialized) return
        val editor = prefs.edit()
        learnedWords.forEach { (word, freq) ->
            editor.putInt("freq_$word", freq)
        }
        nextWordMap.forEach { (prev, map) ->
            map.forEach { (next, count) ->
                editor.putInt("next_${prev}__$next", count)
            }
        }
        editor.apply()
    }

    @Synchronized
    fun getSuggestionsForSequence(t9sequence: String): List<AospDictionary.WordSuggestion> {
        val words = t9Map[t9sequence] ?: return emptyList()
        return words.map { word ->
            // Frequency 256 + learned frequency for tie-breaking
            AospDictionary.WordSuggestion(word, 256 + (learnedWords[word] ?: 0))
        }
    }

    @Synchronized
    fun getSuggestions(constraints: List<String>): List<AospDictionary.WordSuggestion> {
        if (constraints.isEmpty()) return emptyList()

        val digitSequence = constraints.map {
            if (it.length == 1 && it[0].isDigit()) it else (digitMap[it[0]] ?: ' ')
        }.joinToString("").trim()

        if (digitSequence.length > 12) {
             val results = mutableListOf<AospDictionary.WordSuggestion>()
             t9Map[digitSequence]?.forEach { word ->
                 var matches = true
                 val stripped = word.lowercase().filter { it in 'a'..'z' }
                 for (i in constraints.indices) {
                     val constraint = constraints[i]
                     if (constraint.length == 1 && !constraint[0].isDigit()) {
                         if (stripped.length <= i || stripped[i] != constraint[0]) {
                             matches = false
                             break
                         }
                     }
                 }
                 if (matches) results.add(AospDictionary.WordSuggestion(word, 256 + (learnedWords[word] ?: 0)))
             }
             return results
        }

        val potentialMatches = t9Map.subMap(digitSequence, digitSequence + "\uFFFF")
        val results = mutableListOf<AospDictionary.WordSuggestion>()

        for (words in potentialMatches.values) {
            for (word in words) {
                var matches = true
                val stripped = word.lowercase().filter { it in 'a'..'z' }
                for (i in constraints.indices) {
                    val constraint = constraints[i]
                    if (constraint.length == 1 && !constraint[0].isDigit()) {
                        if (stripped.length <= i || stripped[i] != constraint[0]) {
                            matches = false
                            break
                        }
                    }
                }
                if (matches) {
                    results.add(AospDictionary.WordSuggestion(word, 256 + (learnedWords[word] ?: 0)))
                }
            }
        }
        return results
    }

    @Synchronized
    fun getNextWordSuggestions(previousWord: String): List<String> {
        val lowerPrev = previousWord.lowercase().trim()
        val nextWords = nextWordMap[lowerPrev] ?: return emptyList()
        return nextWords.toList()
            .sortedByDescending { it.second }
            .map { it.first }
            .take(3)
    }

    @Synchronized
    fun contains(word: String): Boolean {
        return learnedWords.containsKey(word.lowercase().trim())
    }

    @Synchronized
    fun isValidWord(word: String): Boolean {
        return contains(word)
    }

    @Synchronized
    fun clear() {
        learnedWords.clear()
        nextWordMap.clear()
        t9Map.clear()
        if (::prefs.isInitialized) {
            prefs.edit().clear().apply()
        }
    }
}
