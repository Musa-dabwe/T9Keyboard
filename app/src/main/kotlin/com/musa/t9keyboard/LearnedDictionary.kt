package com.musa.t9keyboard

import android.content.Context
import android.content.SharedPreferences

object LearnedDictionary {
    private val learnedWords = mutableMapOf<String, Int>()
    private val lastTypedMap = mutableMapOf<String, Long>()
    private val nextWordMap = mutableMapOf<String, MutableMap<String, Int>>()
    private lateinit var prefs: SharedPreferences
    private const val EXPIRATION_MS = 180L * 86_400_000L

    @Synchronized
    fun load(context: Context) {
        prefs = context.getSharedPreferences("learned_words", Context.MODE_PRIVATE)
        learnedWords.clear()
        lastTypedMap.clear()
        nextWordMap.clear()

        val now = System.currentTimeMillis()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("freq_")) {
                val word = key.substring(5)
                val freq = value as Int
                learnedWords[word] = freq

                // Load or initialize last typed timestamp
                val timestamp = prefs.getLong("last_typed_$word", -1L)
                if (timestamp == -1L) {
                    lastTypedMap[word] = now
                    prefs.edit().putLong("last_typed_$word", now).apply()
                } else {
                    lastTypedMap[word] = timestamp
                }
            } else if (key.startsWith("next_")) {
                val parts = key.substring(5).split("__")
                if (parts.size == 2) {
                    val prev = parts[0]
                    val next = parts[1]
                    nextWordMap.getOrPut(prev) { mutableMapOf() }[next] = value as Int
                }
            }
        }
        cleanup()
        enforceCap()
    }

    private fun enforceCap() {
        if (learnedWords.size <= 500) return
        val now = System.currentTimeMillis()
        val sortedEntries = learnedWords.entries.sortedBy { (word, freq) ->
            val lastTyped = lastTypedMap[word] ?: 0L
            val daysSince = (now - lastTyped) / 86_400_000L
            val recency = when {
                daysSince <= 7   -> 1.0f
                daysSince <= 30  -> 0.75f
                daysSince <= 90  -> 0.5f
                else             -> 0.25f
            }
            freq * recency
        }
        val toRemoveCount = learnedWords.size - 500
        val toRemove = sortedEntries.take(toRemoveCount).map { it.key }

        val editor = prefs.edit()
        toRemove.forEach { word ->
            learnedWords.remove(word)
            lastTypedMap.remove(word)
            nextWordMap.entries.forEach { it.value.remove(word) }
            editor.remove("freq_$word")
            editor.remove("last_typed_$word")
            // Also remove any bigrams leading to this word
            prefs.all.keys.filter { it.startsWith("next_") && it.endsWith("__$word") }.forEach {
                editor.remove(it)
            }
        }
        editor.apply()
    }

    private fun recencyMultiplier(word: String): Float {
        val lastTyped = lastTypedMap[word] ?: return 0.25f
        val daysSince = (System.currentTimeMillis() - lastTyped) / 86_400_000L
        return when {
            daysSince <= 7   -> 1.0f
            daysSince <= 30  -> 0.75f
            daysSince <= 90  -> 0.5f
            else             -> 0.25f
        }
    }

    private fun cleanup() {
        val now = System.currentTimeMillis()
        val ninetyDays = 90L * 86_400_000L
        val toRemove = learnedWords.keys.filter { word ->
            val freq = learnedWords[word] ?: 0
            val lastTyped = lastTypedMap[word] ?: 0L
            (freq == 1 && (now - lastTyped) > ninetyDays) || (now - lastTyped) > EXPIRATION_MS
        }
        toRemove.forEach { word ->
            learnedWords.remove(word)
            lastTypedMap.remove(word)
            nextWordMap.entries.forEach { it.value.remove(word) }
        }
        if (toRemove.isNotEmpty()) {
            val editor = prefs.edit()
            toRemove.forEach { word ->
                editor.remove("freq_$word")
                editor.remove("last_typed_$word")
            }
            editor.apply()
        }
    }

    @Synchronized
    fun learnWord(word: String, previousWord: String? = null) {
        try {
        val lowerWord = word.lowercase().trim()
        if (lowerWord.isEmpty()) return

        val newFreq = (learnedWords[lowerWord] ?: 0) + 1
        learnedWords[lowerWord] = newFreq

        val now = System.currentTimeMillis()
        lastTypedMap[lowerWord] = now
        prefs.edit().putLong("last_typed_$lowerWord", now).apply()

        if (previousWord != null) {
            val lowerPrev = previousWord.lowercase().trim()
            if (lowerPrev.isNotEmpty()) {
                val nextMap = nextWordMap.getOrPut(lowerPrev) { mutableMapOf() }
                nextMap[lowerWord] = (nextMap[lowerWord] ?: 0) + 1

                if (nextWordMap.size > 5000) {
                    val evictKey = nextWordMap.minByOrNull { entry ->
                        entry.value.values.sum()
                    }?.key
                    if (evictKey != null) {
                        nextWordMap.remove(evictKey)
                        val editor = prefs.edit()
                        prefs.all.keys.filter { it.startsWith("next_${evictKey}__") }.forEach {
                            editor.remove(it)
                        }
                        editor.apply()
                    }
                }
            }
        }

        enforceCap()
        save()
        } catch (e: Exception) {
            // Context might not be available here directly, use a dummy or find a way to get it
            // For now, we'll rely on the caller to log if needed or inject context
        }
    }

    @Synchronized
    fun learnWordStrong(word: String, previousWord: String?) {
        learnWord(word, previousWord)
        learnWord(word, previousWord)
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
    fun getSuggestions(constraints: List<String>, previousWord: String? = null): List<AospDictionary.WordSuggestion> {
        if (constraints.isEmpty()) return emptyList()

        val digitSequence = constraints.map {
            if (it.length == 1 && it[0].isDigit()) it else T9Utils.getDigitForChar(it[0])
        }.joinToString("").trim()

        val bigramBoosts = if (previousWord != null) {
            nextWordMap[previousWord.lowercase().trim()] ?: emptyMap()
        } else emptyMap()

        val candidates = mutableListOf<AospDictionary.WordSuggestion>()

        val now = System.currentTimeMillis()
        // Match learned words against the digit sequence constraints
        learnedWords.forEach { (word, freq) ->
            val lastTyped = lastTypedMap[word] ?: 0L
            if (now - lastTyped > EXPIRATION_MS) return@forEach

            val wordT9 = getT9Sequence(word)
            if (wordT9.startsWith(digitSequence)) {
                var matches = true
                val stripped = word.filter { it in 'a'..'z' }
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
                    val aospBaseFreq = AospDictionary.getWordFrequency(word)
                    val decayedBoost = (256 + freq) * recencyMultiplier(word)
                    val finalFreq = aospBaseFreq + decayedBoost.toInt()
                    candidates.add(AospDictionary.WordSuggestion(word, finalFreq))
                }
            }
        }

        return candidates.map { suggestion ->
            val bigramFreq = bigramBoosts[suggestion.word.lowercase()] ?: 0
            val boostedFreq = suggestion.frequency + (bigramFreq * 3)
            suggestion.copy(frequency = boostedFreq)
        }.sortedByDescending { it.frequency }
    }

    private fun getT9Sequence(word: String): String {
        return word.lowercase().filter { it in 'a'..'z' }.map { T9Utils.getDigitForChar(it) }.joinToString("").trim()
    }

    @Synchronized
    fun getNextWordSuggestions(previousWord: String): List<String> {
        val lowerPrev = previousWord.lowercase().trim()
        val nextWords = nextWordMap[lowerPrev] ?: return emptyList()
        val now = System.currentTimeMillis()

        return nextWords.toList()
            .filter { (word, _) ->
                val lastTyped = lastTypedMap[word] ?: 0L
                (now - lastTyped) <= EXPIRATION_MS
            }
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
        lastTypedMap.clear()
        nextWordMap.clear()
        if (::prefs.isInitialized) {
            prefs.edit().clear().apply()
        }
    }
}
