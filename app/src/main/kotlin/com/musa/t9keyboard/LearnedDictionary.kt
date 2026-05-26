package com.musa.t9keyboard

import android.content.Context
import android.content.SharedPreferences
import com.harrytmthy.safebox.SafeBox
import java.util.TreeMap

object LearnedDictionary {
    private val learnedWords = mutableMapOf<String, Int>()
    private val learnedT9Index = TreeMap<String, MutableList<String>>()
    private var onMutationListener: (() -> Unit)? = null

    fun setOnMutationListener(listener: (() -> Unit)?) {
        onMutationListener = listener
    }

    private fun notifyMutation() {
        onMutationListener?.invoke()
    }

    private val lastTypedMap = mutableMapOf<String, Long>()
    private val nextWordMap = mutableMapOf<String, MutableMap<String, Int>>()
    private lateinit var prefs: SharedPreferences
    private const val EXPIRATION_MS = 180L * 86_400_000L
    private const val PREFS_NAME = "learned_words"
    private const val MAX_LEARNED_WORDS = 10_000
    private const val MAX_BIGRAM_ENTRIES = 5_000 // Already enforced

    enum class TrimLevel {
        MODERATE,   // Remove freq=1 words older than 30 days
        AGGRESSIVE  // Remove freq≤2 words older than 60 days
    }

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
        // One-time migration from plain SharedPreferences to SafeBox
        val plainPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (plainPrefs.all.isNotEmpty()) {
            val safePrefs = SafeBox.create(context, PREFS_NAME)
            val editor = safePrefs.edit()
            plainPrefs.all.forEach { (k, v) ->
                when (v) {
                    is Int -> editor.putInt(k, v)
                    is Long -> editor.putLong(k, v)
                    is String -> editor.putString(k, v)
                    is Boolean -> editor.putBoolean(k, v)
                }
            }
            editor.apply()
            plainPrefs.edit().clear().apply()
        }

        prefs = SafeBox.create(context, PREFS_NAME)
        learnedWords.clear()
        learnedT9Index.clear()
        lastTypedMap.clear()
        nextWordMap.clear()

        val now = System.currentTimeMillis()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("freq_")) {
                val word = key.substring(5)
                val freq = value as Int
                learnedWords[word] = freq
                val digits = getT9Sequence(word)
                if (digits.isNotEmpty()) {
                    learnedT9Index.getOrPut(digits) { mutableListOf() }.add(word)
                }

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
            val digits = getT9Sequence(word)
            learnedT9Index[digits]?.remove(word)
            if (learnedT9Index[digits]?.isEmpty() == true) {
                learnedT9Index.remove(digits)
            }
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
            notifyMutation()
        }
    }

    @Synchronized
    fun learnWord(word: String, previousWord: String? = null) {
        try {
        val lowerWord = word.lowercase().trim()
        if (lowerWord.isEmpty()) return

        // Enforce MAX_LEARNED_WORDS cap with LRU eviction
        if (learnedWords.size >= MAX_LEARNED_WORDS && !learnedWords.containsKey(lowerWord)) {
            val lruWord = lastTypedMap.entries
                .filter { learnedWords.containsKey(it.key) }
                .minByOrNull { it.value }
                ?.key

            if (lruWord != null) {
                evictWord(lruWord)
            }
        }

        val oldFreq = learnedWords[lowerWord]
        val newFreq = (oldFreq ?: 0) + 1
        learnedWords[lowerWord] = newFreq
        if (oldFreq == null) {
            val digits = getT9Sequence(lowerWord)
            if (digits.isNotEmpty()) {
                learnedT9Index.getOrPut(digits) { mutableListOf() }.add(lowerWord)
            }
        }

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

        save()
        notifyMutation()
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

    /**
     * Evicts a single word from all data structures and persistent storage.
     */
    private fun evictWord(word: String) {
        learnedWords.remove(word)
        val digits = getT9Sequence(word)
        learnedT9Index[digits]?.remove(word)
        if (learnedT9Index[digits]?.isEmpty() == true) {
            learnedT9Index.remove(digits)
        }
        lastTypedMap.remove(word)
        nextWordMap.entries.forEach { it.value.remove(word) }

        prefs.edit().apply {
            remove("freq_$word")
            remove("last_typed_$word")
        }.apply()
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
            if (it.length == 1 && it[0].isDigit()) it else (digitMap[it[0]] ?: ' ')
        }.joinToString("").trim()

        val bigramBoosts = if (previousWord != null) {
            nextWordMap[previousWord.lowercase().trim()] ?: emptyMap()
        } else emptyMap()

        val candidates = mutableListOf<AospDictionary.WordSuggestion>()
        val now = System.currentTimeMillis()

        // Use subMap to efficiently find all sequences that start with the digitSequence
        val potentialMatches = learnedT9Index.subMap(digitSequence, digitSequence + "\uFFFF")

        for (words in potentialMatches.values) {
            for (word in words) {
                val freq = learnedWords[word] ?: 0
                val lastTyped = lastTypedMap[word] ?: 0L
                if (now - lastTyped > EXPIRATION_MS) continue

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
        return word.lowercase().filter { it in 'a'..'z' }.map { digitMap[it] ?: ' ' }.joinToString("").trim()
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
    fun clear() {
        learnedWords.clear()
        learnedT9Index.clear()
        lastTypedMap.clear()
        nextWordMap.clear()
        if (::prefs.isInitialized) {
            prefs.edit().clear().apply()
        }
        notifyMutation()
    }

    /**
     * Trims memory by removing low-frequency or stale words.
     * Called from T9InputMethodService.onTrimMemory().
     */
    @Synchronized
    fun trimMemory(pruneLevel: TrimLevel) {
        val now = System.currentTimeMillis()
        val toRemove = mutableListOf<String>()

        when (pruneLevel) {
            TrimLevel.MODERATE -> {
                val thirtyDays = 30L * 86_400_000L
                learnedWords.forEach { (word, freq) ->
                    val lastTyped = lastTypedMap[word] ?: 0L
                    if (freq == 1 && (now - lastTyped) > thirtyDays) {
                        toRemove.add(word)
                    }
                }
            }

            TrimLevel.AGGRESSIVE -> {
                val sixtyDays = 60L * 86_400_000L
                learnedWords.forEach { (word, freq) ->
                    val lastTyped = lastTypedMap[word] ?: 0L
                    if (freq <= 2 && (now - lastTyped) > sixtyDays) {
                        toRemove.add(word)
                    }
                }
            }
        }

        if (toRemove.isNotEmpty()) {
            toRemove.forEach { word -> evictWord(word) }
            notifyMutation()

            if (BuildConfig.DEBUG) {
                android.util.Log.d("LearnedDictionary",
                    "trimMemory($pruneLevel): Removed ${toRemove.size} words")
            }
        }
    }
}
