package com.musa.t9keyboard

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

object AospDictionary {
    private val t9Map = mutableMapOf<String, MutableList<String>>()
    private val wordFrequency = mutableMapOf<String, Int>()

    data class WordSuggestion(val word: String, val frequency: Int)

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

    private val hardcodedWords = mapOf(
        "2" to listOf("a", "b", "c"),
        "3" to listOf("e", "d", "f"),
        "4" to listOf("i", "h", "g"),
        "5" to listOf("j", "k", "l"),
        "6" to listOf("n", "m", "o"),
        "7" to listOf("s", "p", "q", "r"),
        "8" to listOf("t", "u", "v"),
        "9" to listOf("w", "x", "y", "z"),
        "84" to listOf("th", "ti", "uh"),
        "843" to listOf("the", "tie", "vie"),
        "4663" to listOf("good", "gone", "home", "hone"),
        "4673" to listOf("hope", "gore", "hose"),
        "9673" to listOf("word", "wore", "yore"),
        "7483" to listOf("the", "ride", "side", "site"),
        "2273" to listOf("bare", "care", "case", "base"),
        "5663" to listOf("love", "lone", "lone"),
        "3276" to listOf("farm", "earn", "darn"),
        "4283" to listOf("have", "gave", "hate", "gate", "fate", "gave"),
        "9687" to listOf("your", "wots"),
        "4687" to listOf("govs", "hour"),
        "6473" to listOf("mire", "ogre", "nigh"),
        "2669" to listOf("any", "bow", "cow", "boy", "box", "coy"),
        "86" to listOf("to", "un", "vo"),
        "468" to listOf("got", "hot", "hit", "gut", "hut", "iot"),
        "273" to listOf("are", "ape", "age", "ace", "bre", "cre"),
        "9484" to listOf("with", "yogi"),
        "3676" to listOf("from", "eron"),
        "84373" to listOf("there", "tired", "three"),
        "84489" to listOf("thirty", "ighty"),
        "2255" to listOf("ball", "call", "bill", "bell", "bull", "calk", "balk"),
        "7668" to listOf("pont", "root", "rout", "snot", "soot", "snou"),
        "9677" to listOf("wops", "yops", "work", "worm", "worn", "worse", "worst", "wort", "yore")
    )

    private val displayWords = mutableMapOf<String, String>()

    fun loadFromAssets(context: Context) {
        try {
            t9Map.clear()
            wordFrequency.clear()
            displayWords.clear()
            val reader = BufferedReader(InputStreamReader(context.assets.open("en_us_words.bin")))
            var line: String? = reader.readLine()
            while (line != null) {
                val parts = line.split("\t")
                if (parts.size >= 2) {
                    val word = parts[0]
                    val freq = parts[1].toInt()
                    wordFrequency[word] = freq
                    if (parts.size >= 3 && parts[2].isNotEmpty()) {
                        displayWords[word] = parts[2]
                    }
                    val sequence = getT9Sequence(word)
                    if (sequence.isNotEmpty()) {
                        t9Map.getOrPut(sequence) { mutableListOf() }.add(word)
                    }
                }
                line = reader.readLine()
            }
            reader.close()
        } catch (e: Exception) {
            Log.e("AospDictionary", "Error loading dictionary", e)
        }
    }

    private fun getT9Sequence(word: String): String {
        return word.lowercase().filter { it in 'a'..'z' }.map { digitMap[it] ?: ' ' }.joinToString("").trim()
    }

    fun contains(word: String): Boolean {
        return wordFrequency.containsKey(word.lowercase().trim())
    }

    fun getFrequency(word: String): Int {
        return wordFrequency[word.lowercase().trim()] ?: 0
    }

    fun getWordsStartingWith(prefix: String): List<WordSuggestion> {
        // prefix is a T9 digit sequence
        return t9Map.filterKeys { it.startsWith(prefix) }
            .flatMap { (seq, words) ->
                words.map { word ->
                    WordSuggestion(displayWords[word] ?: word, wordFrequency[word] ?: 0)
                }
            }
            .sortedByDescending { it.frequency }
            .take(10)
    }

    fun getSuggestionsForSequence(t9sequence: String): List<WordSuggestion> {
        val words = t9Map[t9sequence]?.toMutableList() ?: mutableListOf()

        hardcodedWords[t9sequence]?.forEach {
            if (!words.contains(it)) {
                words.add(it)
            }
        }

        return words.distinct().map { word ->
            WordSuggestion(displayWords[word] ?: word, wordFrequency[word] ?: 0)
        }
    }

    fun getSuggestions(constraints: List<String>): List<WordSuggestion> {
        if (constraints.isEmpty()) return emptyList()

        val digitSequence = constraints.map {
            if (it.length == 1 && it[0].isDigit()) it else (digitMap[it[0]] ?: ' ')
        }.joinToString("").trim()

        val potentialMatches = t9Map.filterKeys { it.startsWith(digitSequence) }
        val results = mutableListOf<WordSuggestion>()

        for ((sequence, words) in potentialMatches) {
            for (word in words) {
                var matches = true
                // word here is the strippedWord from the file
                if (word.length < constraints.size) {
                    // Optimization: if word is shorter than constraints, it can't match unless we're doing prefix
                    // But getSuggestions is usually for the current length
                    // Wait, T9Dictionary implementation allowed startsWith for multi-tap?
                    // "Find all words whose T9 sequence starts with this digit sequence"
                    // Yes, so it supports prefix.
                }

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
                    results.add(WordSuggestion(displayWords[word] ?: word, wordFrequency[word] ?: 0))
                }
            }
        }
        return results
    }
}
