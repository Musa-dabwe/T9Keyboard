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

/**
 * Hashmap-based combination dictionary.
 *
 * Every dictionary word is stored lowercase and mapped to the sequence of keys
 * that types it ([wordToEntry] / [getSequenceForWord]); the reverse hashmap
 * [sequenceToWords] answers exact T9 lookups in O(1). Capitalization is never
 * stored - it is applied at typing time from the shift state.
 *
 * [prefixIndex] is a sorted view over the same entries used only for
 * longer-word predictions (prefix matches on a partial key sequence).
 */
object AospDictionary {
    data class WordEntry(
        val word: String,       // canonical lowercase form, apostrophes kept ("i'll")
        val stripped: String,   // a-z only, used for exact-letter constraint matching
        val sequence: String,   // key combination that types this word
        val frequency: Int
    )

    private val wordToEntry = HashMap<String, WordEntry>()
    private val sequenceToWords = HashMap<String, MutableList<WordEntry>>()
    private val prefixIndex = TreeMap<String, MutableList<WordEntry>>()
    private val entriesByFrequency = mutableListOf<WordEntry>()
    private var isLoaded = false

    data class WordSuggestion(
        val word: String,
        val frequency: Int,
        val category: WordCategory = WordCategory.BASE
    )

    suspend fun loadFromAssets(context: Context) = withContext(Dispatchers.IO) {
        val newWordToEntry = HashMap<String, WordEntry>()
        val newSequenceToWords = HashMap<String, MutableList<WordEntry>>()
        val newPrefixIndex = TreeMap<String, MutableList<WordEntry>>()
        val newEntriesByFrequency = mutableListOf<WordEntry>()

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
                        // The asset is normalized to lowercase, but never trust stored case
                        val word = parts[0].lowercase()
                        val freq = parts[1].split(" ")[0].toInt()
                        val stripped = word.filter { it in 'a'..'z' }
                        val sequence = T9Utils.getT9Sequence(word)

                        if (sequence.isNotEmpty() && !newWordToEntry.containsKey(word)) {
                            val entry = WordEntry(word, stripped, sequence, freq)
                            newWordToEntry[word] = entry
                            newSequenceToWords.getOrPut(sequence) { mutableListOf() }.add(entry)
                            newPrefixIndex.getOrPut(sequence) { mutableListOf() }.add(entry)
                            newEntriesByFrequency.add(entry)
                        }
                    }
                } catch (e: Exception) {
                    Log.w("AospDictionary", "Skipping malformed line: $line", e)
                }
                line = r.readLine()
            }
        }

        // The asset is sorted by frequency desc; keep that order for containing-matches
        synchronized(this@AospDictionary) {
            wordToEntry.clear()
            wordToEntry.putAll(newWordToEntry)
            sequenceToWords.clear()
            sequenceToWords.putAll(newSequenceToWords)
            prefixIndex.clear()
            prefixIndex.putAll(newPrefixIndex)
            entriesByFrequency.clear()
            entriesByFrequency.addAll(newEntriesByFrequency)
            isLoaded = true
        }
    }

    /** The key combination that types [word], or null if it's not in the dictionary. */
    @Synchronized
    fun getSequenceForWord(word: String): String? =
        wordToEntry[word.lowercase().trim()]?.sequence

    @Synchronized
    fun getWordFrequency(word: String): Int =
        wordToEntry[word.lowercase().trim()]?.frequency ?: 0

    @Synchronized
    fun contains(word: String): Boolean =
        wordToEntry.containsKey(word.lowercase().trim())

    @Synchronized
    fun containsWord(word: String): Boolean = contains(word)

    private fun categorizeWord(entry: WordEntry): WordCategory =
        if (entry.stripped.length == 1) WordCategory.PROTECTED else WordCategory.BASE

    private fun toSuggestion(entry: WordEntry): WordSuggestion =
        WordSuggestion(entry.word, entry.frequency, categorizeWord(entry))

    @Synchronized
    fun getSuggestionsForSequence(t9sequence: String): List<WordSuggestion> {
        if (!isLoaded) return emptyList()
        val entries = sequenceToWords[t9sequence] ?: return emptyList()
        return entries.map { toSuggestion(it) }.sortedByDescending { it.frequency }
    }

    @Synchronized
    fun getWordsStartingWith(prefix: String): List<WordSuggestion> {
        if (!isLoaded) return emptyList()
        if (prefix.length > 12) return emptyList()
        val potentialMatches = prefixIndex.subMap(prefix, prefix + "\uFFFF")
        return potentialMatches.values
            .flatMap { entries -> entries.map { toSuggestion(it) } }
            .sortedByDescending { it.frequency }
    }

    @Synchronized
    fun getWordsContaining(literal: String): List<WordSuggestion> {
        if (!isLoaded) return emptyList()
        if (literal.isEmpty()) return emptyList()
        val lowerLiteral = literal.lowercase()
        return entriesByFrequency
            .asSequence()
            .filter { it.word.contains(lowerLiteral) }
            .take(30)
            .map { toSuggestion(it) }
            .sortedByDescending { it.frequency }
            .toList()
    }

    @Synchronized
    fun getSuggestions(constraints: List<String>): List<WordSuggestion> {
        if (!isLoaded) return emptyList()
        if (constraints.isEmpty()) return emptyList()

        val digitSequence = constraints.map {
            if (it.length == 1 && T9Utils.isKeyCode(it[0])) it else T9Utils.getDigitForChar(it[0])
        }.joinToString("").trim()

        fun matchesConstraints(entry: WordEntry): Boolean {
            for (i in constraints.indices) {
                val constraint = constraints[i]
                if (constraint.length == 1 && !T9Utils.isKeyCode(constraint[0])) {
                    if (entry.stripped.length <= i || entry.stripped[i] != constraint[0]) {
                        return false
                    }
                }
            }
            return true
        }

        // Long sequences: exact hashmap hit only, skip prefix iteration
        if (digitSequence.length > 12) {
            return sequenceToWords[digitSequence]
                ?.filter { matchesConstraints(it) }
                ?.map { toSuggestion(it) }
                ?: emptyList()
        }

        val potentialMatches = prefixIndex.subMap(digitSequence, digitSequence + "\uFFFF")
        val results = mutableListOf<WordSuggestion>()
        for (entries in potentialMatches.values) {
            for (entry in entries) {
                if (matchesConstraints(entry)) {
                    results.add(toSuggestion(entry))
                }
            }
        }
        return results
    }
}
