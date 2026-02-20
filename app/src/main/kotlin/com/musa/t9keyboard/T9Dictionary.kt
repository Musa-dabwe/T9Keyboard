package com.musa.t9keyboard

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

class T9Dictionary(private val context: Context) {
    private val t9Map = mutableMapOf<String, MutableList<String>>()
    private val learnedWords = mutableMapOf<String, Int>()
    private val nextWordMap = mutableMapOf<String, MutableMap<String, Int>>()
    private val wordFrequencyRank = mutableMapOf<String, Int>()

    companion object {
        private const val DICTIONARY_VERSION = 1
    }

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
        checkDictionaryVersion()
        loadStaticDictionary()
        loadLearnedWords()
    }

    private fun checkDictionaryVersion() {
        val prefs = context.getSharedPreferences("dictionary_prefs", Context.MODE_PRIVATE)
        val currentVersion = prefs.getInt("version", 0)
        if (currentVersion != DICTIONARY_VERSION) {
            // Rebuild cache - although currently cache is in-memory
            // But we should store the version
            prefs.edit().putInt("version", DICTIONARY_VERSION).apply()
        }
    }

    private fun loadStaticDictionary() {
        try {
            val reader = BufferedReader(InputStreamReader(context.assets.open("english_words.txt")))
            var word: String? = reader.readLine()
            var rank = 1
            while (word != null) {
                val cleanedWord = word.lowercase().trim()
                if (cleanedWord.isNotEmpty()) {
                    addWordToT9Map(cleanedWord)
                    if (!wordFrequencyRank.containsKey(cleanedWord)) {
                        wordFrequencyRank[cleanedWord] = rank++
                    }
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

    /**
     * Retrieves up to 3 word predictions based on the exact digit sequence.
     * Matches are ranked by:
     * 1. User-learned status (learned words first)
     * 2. Frequency rank in dictionary (top of file first)
     * 3. Learned user frequency (most used first)
     */
    fun xt9Predict(digitSequence: String): List<String> {
        if (digitSequence.isEmpty()) return emptyList()

        val exactMatches = t9Map[digitSequence]?.toMutableList() ?: mutableListOf()

        // Add hardcoded words if sequence matches
        hardcodedWords[digitSequence]?.forEach {
            if (!exactMatches.contains(it)) {
                exactMatches.add(it)
            }
        }

        if (exactMatches.isEmpty()) return emptyList()

        return exactMatches.distinct().sortedWith(compareByDescending<String> { learnedWords.containsKey(it) }
            .thenBy { wordFrequencyRank[it] ?: Int.MAX_VALUE }
            .thenByDescending { learnedWords[it] ?: 0 })
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
