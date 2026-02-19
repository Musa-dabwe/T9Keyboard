package com.musa.t9keyboard

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

class T9Dictionary(private val context: Context) {
    private val t9Map = mutableMapOf<String, MutableList<String>>()
    private val learnedWords = mutableMapOf<String, Int>()
    private val nextWordMap = mutableMapOf<String, MutableMap<String, Int>>()

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

    private val keyToChars = mapOf(
        '2' to "abc", '3' to "def", '4' to "ghi",
        '5' to "jkl", '6' to "mno", '7' to "pqrs",
        '8' to "tuv", '9' to "wxyz"
    )

    init {
        loadStaticDictionary()
        loadLearnedWords()
    }

    private fun loadStaticDictionary() {
        try {
            val reader = BufferedReader(InputStreamReader(context.assets.open("english_words.txt")))
            var word: String? = reader.readLine()
            while (word != null) {
                val cleanedWord = word.lowercase().trim()
                if (cleanedWord.isNotEmpty()) {
                    addWordToT9Map(cleanedWord)
                }
                word = reader.readLine()
            }
            reader.close()
        } catch (e: Exception) {
            Log.e("T9Dictionary", "Error loading static dictionary", e)
        }
    }

    private fun addWordToT9Map(word: String) {
        val sequence = getT9Sequence(word)
        t9Map.getOrPut(sequence) { mutableListOf() }.add(word)
    }

    private fun getT9Sequence(word: String): String {
        return word.map { digitMap[it] ?: ' ' }.joinToString("").trim()
    }

    /**
     * Retrieves up to 3 word suggestions based on the provided character constraints.
     *
     * Prediction Ranking Algorithm:
     * 1. Filtering: Find all words in the dictionary whose T9 sequence starts with the
     *    digit sequence derived from the constraints.
     * 2. Constraint Matching: For each potential match, verify that it satisfies fixed
     *    character constraints (where the user has multi-tapped to select a specific letter).
     * 3. Ranking: Sort the matching words by:
     *    a. Learned frequency (how often the user has typed/selected this word).
     *    b. Word length (shorter words/exact matches first).
     *    c. Alphabetical order (as a final tie-breaker).
     *
     * @param constraints A list of strings, where each string represents possible characters at that position.
     *                    If it's a single character like "b", it's fixed.
     *                    If it's a digit like "2", it means any of "abc".
     */
    fun getSuggestions(constraints: List<String>): List<String> {
        if (constraints.isEmpty()) return emptyList()

        val digitSequence = constraints.map { if (it.length == 1 && it[0].isDigit()) it else (digitMap[it[0]] ?: ' ') }.joinToString("").trim()

        // Find all words whose T9 sequence starts with this digit sequence
        val potentialMatches = t9Map.filterKeys { it.startsWith(digitSequence) }

        val filteredWords = mutableListOf<String>()
        for ((sequence, words) in potentialMatches) {
            for (word in words) {
                var matches = true
                for (i in constraints.indices) {
                    val constraint = constraints[i]
                    if (constraint.length == 1 && !constraint[0].isDigit()) {
                        // Fixed character constraint
                        if (word[i] != constraint[0]) {
                            matches = false
                            break
                        }
                    }
                    // If it's a digit, it matches by virtue of being in potentialMatches (which matches the digit sequence)
                }
                if (matches) {
                    filteredWords.add(word)
                }
            }
        }

        return filteredWords.distinct().sortedWith(compareByDescending<String> { learnedWords[it] ?: 0 }
            .thenBy { it.length }
            .thenBy { it })
            .take(3)
    }

    fun getNextWordSuggestions(previousWord: String): List<String> {
        val lowerPrev = previousWord.lowercase().trim().replace(Regex("[^a-z]"), "")
        val nextWords = nextWordMap[lowerPrev] ?: return emptyList()
        return nextWords.toList().sortedByDescending { it.second }.map { it.first }.take(3)
    }

    fun learnWord(word: String, previousWord: String? = null) {
        val lowerWord = word.lowercase().trim().replace(Regex("[^a-z]"), "")
        if (lowerWord.isEmpty()) return

        learnedWords[lowerWord] = (learnedWords[lowerWord] ?: 0) + 1

        if (previousWord != null) {
            val lowerPrev = previousWord.lowercase().trim().replace(Regex("[^a-z]"), "")
            if (lowerPrev.isNotEmpty()) {
                val nextMap = nextWordMap.getOrPut(lowerPrev) { mutableMapOf() }
                nextMap[lowerWord] = (nextMap[lowerWord] ?: 0) + 1
            }
        }

        // Add to T9 map if not present
        val sequence = getT9Sequence(lowerWord)
        if (sequence.isNotEmpty()) {
            val list = t9Map.getOrPut(sequence) { mutableListOf() }
            if (!list.contains(lowerWord)) {
                list.add(lowerWord)
            }
        }

        saveLearnedWords()
    }

    private fun loadLearnedWords() {
        val prefs = context.getSharedPreferences("learned_words", Context.MODE_PRIVATE)
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("freq_")) {
                learnedWords[key.substring(5)] = value as Int
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

    private fun saveLearnedWords() {
        val prefs = context.getSharedPreferences("learned_words", Context.MODE_PRIVATE)
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

    fun clearLearnedDictionary() {
        learnedWords.clear()
        nextWordMap.clear()
        context.getSharedPreferences("learned_words", Context.MODE_PRIVATE).edit().clear().apply()
    }
}
