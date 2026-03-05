package com.musa.t9keyboard

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

object AospDictionary {
    data class WordEntry(val stripped: String, val frequency: Int, val display: String) {
        val word: String get() = if (display.isNotEmpty()) display else stripped
    }

    private val t9Map = mutableMapOf<String, MutableList<WordEntry>>()
    private val wordMap = mutableMapOf<String, MutableList<WordEntry>>()

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
        "24" to listOf("AI", "bi", "ah"),
        "84" to listOf("th", "ti", "uh"),
        "843" to listOf("the", "tie", "vie"),
        "6872" to listOf("Musa"),
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
        "6473" to listOf("ogre", "nigh"),
        "2669" to listOf("any", "bow", "cow", "boy", "box", "coy"),
        "86" to listOf("to", "un", "vo"),
        "468" to listOf("got", "hot", "hit", "gut", "hut", "iot"),
        "273" to listOf("are", "ape", "age", "ace", "bre", "cre"),
        "9484" to listOf("with", "yogi"),
        "3676" to listOf("from", "eron"),
        "397263" to listOf("expand"),
        "84373" to listOf("there", "tired", "three"),
        "84489" to listOf("thirty", "ighty"),
        "2255" to listOf("ball", "call", "bill", "bell", "bull", "calk", "balk"),
        "7668" to listOf("pont", "root", "rout", "snot", "soot", "snou"),
        "3473" to listOf("fire", "dire"),
        "5478" to listOf("list", "jist"),
        "3225766" to listOf("Fackson"),
        "688383742" to listOf("Mutetesha"),
        "9677" to listOf("work", "worm", "worn", "worse", "worst", "wort", "yore")
    )

    fun loadFromAssets(context: Context) {
        try {
            t9Map.clear()
            wordMap.clear()
            val reader = BufferedReader(InputStreamReader(context.assets.open("en_us_words.bin")))
            var line: String? = reader.readLine()
            while (line != null) {
                val parts = line.split("\t")
                if (parts.size >= 2) {
                    val stripped = parts[0]
                    val freq = parts[1].toInt()
                    val display = if (parts.size >= 3) parts[2] else ""
                    val entry = WordEntry(stripped, freq, display)

                    wordMap.getOrPut(stripped) { mutableListOf() }.add(entry)

                    val digits = getT9Sequence(stripped)
                    if (digits.isNotEmpty()) {
                        t9Map.getOrPut(digits) { mutableListOf() }.add(entry)
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
        val lower = word.lowercase().trim()
        val stripped = lower.filter { it in 'a'..'z' }
        return wordMap[stripped]?.any { it.word.lowercase() == lower } ?: false
    }

    fun getWordsStartingWith(prefix: String): List<WordSuggestion> {
        return t9Map.filterKeys { it.startsWith(prefix) }
            .flatMap { (_, entries) ->
                entries.map { WordSuggestion(it.word, it.frequency) }
            }
            .sortedByDescending { it.frequency }
            .take(10)
    }

    fun getSuggestionsForSequence(t9sequence: String): List<WordSuggestion> {
        val entries = t9Map[t9sequence] ?: mutableListOf<WordEntry>()
        val results = entries.map { WordSuggestion(it.word, it.frequency) }.toMutableList()

        hardcodedWords[t9sequence]?.forEach { word ->
            if (results.none { it.word == word }) {
                results.add(WordSuggestion(word, 0))
            }
        }

        return results.distinctBy { it.word }
    }

    fun getSuggestions(constraints: List<String>): List<WordSuggestion> {
        if (constraints.isEmpty()) return emptyList()

        val digitSequence = constraints.map {
            if (it.length == 1 && it[0].isDigit()) it else (digitMap[it[0]] ?: ' ')
        }.joinToString("").trim()

        val potentialMatches = t9Map.filterKeys { it.startsWith(digitSequence) }
        val results = mutableListOf<WordSuggestion>()

        for ((_, entries) in potentialMatches) {
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
                    results.add(WordSuggestion(entry.word, entry.frequency))
                }
            }
        }
        return results
    }
}
