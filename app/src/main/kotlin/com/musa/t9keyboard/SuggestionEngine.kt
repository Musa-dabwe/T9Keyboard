package com.musa.t9keyboard

import kotlinx.coroutines.*

class SuggestionEngine(
    private val serviceScope: CoroutineScope,
    private val contactProvider: () -> Boolean, // returns contactSuggestionsEnabled && contactPermissionGranted
    private val onSuggestionsReady: (List<String>, String?) -> Unit
) {

    private val suggestionCache = object : LinkedHashMap<String, Pair<List<String>, String?>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<List<String>, String?>>?): Boolean {
            return size > 32
        }
    }

    init {
        LearnedDictionary.setOnMutationListener {
            synchronized(suggestionCache) {
                suggestionCache.clear()
            }
        }
    }

    /**
     * Clears the internal suggestion cache.
     * Called during memory pressure to free up resources.
     */
    fun clearCache() {
        synchronized(suggestionCache) {
            val cacheSize = suggestionCache.size
            suggestionCache.clear()

            if (BuildConfig.DEBUG && cacheSize > 0) {
                android.util.Log.d("SuggestionEngine", "Cleared $cacheSize cache entries")
            }
        }
    }

    private var suggestionJob: Job? = null
    private var nextWordJob: Job? = null

    fun requestSuggestions(editorState: EditorState, xt9Enabled: Boolean, isInputSensitive: Boolean = false) {
        suggestionJob?.cancel()
        if (isInputSensitive) {
            onSuggestionsReady(emptyList(), null)
            return
        }
        val constraints = editorState.currentWordConstraints.toList()
        val composing = editorState.composingText.toString()
        val lastWord = editorState.lastCommittedWord
        val xt9DigitSeq = editorState.xt9DigitSequence.toString()
        val xt9RawSeq = editorState.xt9RawSequence.toString()

        val cacheKey = "$xt9Enabled|$composing|$xt9DigitSeq|$xt9RawSeq|$lastWord|${constraints.joinToString(",")}"
        synchronized(suggestionCache) {
            val cached = suggestionCache[cacheKey]
            if (cached != null) {
                onSuggestionsReady(cached.first, cached.second)
                return
            }
        }

        suggestionJob = serviceScope.launch {
            val result = withContext(Dispatchers.Default) {
                if (xt9Enabled) {
                    processXt9Suggestions(xt9DigitSeq, xt9RawSeq, constraints, lastWord)
                } else {
                    processMultiTapSuggestions(composing, constraints, lastWord)
                }
            }
            synchronized(suggestionCache) {
                suggestionCache[cacheKey] = result
            }
            onSuggestionsReady(result.first, result.second)
        }
    }

    private fun processMultiTapSuggestions(
        composing: String,
        constraints: List<String>,
        lastWord: String?
    ): Pair<List<String>, String?> {
        if (composing.isEmpty() || composing.all { !it.isLetter() }) {
            return Pair(emptyList(), null)
        }

        val targetLength = composing.length
        val contactsEnabled = contactProvider()

        val learned = LearnedDictionary.getSuggestions(constraints, lastWord)
        val aosp = AospDictionary.getSuggestions(constraints)
        val containing = if (targetLength >= 2) AospDictionary.getWordsContaining(composing) else emptyList()

        val mergedCandidates = HashMap<String, AospDictionary.WordSuggestion>()
        fun merge(list: List<AospDictionary.WordSuggestion>) {
            list.forEach { suggestion ->
                val key = suggestion.word.lowercase()
                val existing = mergedCandidates[key]

                if (existing == null) {
                    mergedCandidates[key] = suggestion
                } else {
                    // Keep suggestion with higher category priority
                    val shouldReplace = when {
                        suggestion.category == WordCategory.PROTECTED && existing.category != WordCategory.PROTECTED -> true
                        suggestion.category == existing.category && suggestion.frequency > existing.frequency -> true
                        suggestion.category == WordCategory.BASE && existing.category == WordCategory.LEARNED -> true
                        else -> false
                    }

                    if (shouldReplace) {
                        mergedCandidates[key] = suggestion
                    }
                }
            }
        }

        merge(learned)
        merge(aosp)
        merge(containing)

        if (contactsEnabled) {
            val seq = constraints.map { if (it.length == 1 && T9Utils.isKeyCode(it[0])) it else T9Utils.getDigitForChar(it[0]).toString() }.joinToString("")
            val contacts = ContactsDictionary.getSuggestionsForSequence(seq).map { AospDictionary.WordSuggestion(it, Int.MAX_VALUE - 1, WordCategory.BASE) }
            val contactPrefixes = ContactsDictionary.getSuggestionsForPrefix(seq).map { AospDictionary.WordSuggestion(it, Int.MAX_VALUE - 1, WordCategory.BASE) }
            merge(contacts)
            merge(contactPrefixes)
        }

        val allCandidates = mergedCandidates.values.toList()

        // Sort by category priority first, then by learned status, then frequency
        val exactMatches = allCandidates.filter { it.word.length == targetLength }
            .sortedWith(
                compareBy<AospDictionary.WordSuggestion> { suggestion ->
                    when (suggestion.category) {
                        WordCategory.PROTECTED -> 0  // Highest priority
                        WordCategory.BASE -> 1
                        WordCategory.LEARNED -> 2    // Lowest priority
                    }
                }.thenByDescending { it.frequency }
            )

        val longerMatches = allCandidates.filter { it.word.length > targetLength }
            .sortedWith(
                compareBy<AospDictionary.WordSuggestion> { it.word.length }
                    .thenBy { suggestion ->
                        when (suggestion.category) {
                            WordCategory.PROTECTED -> 0
                            WordCategory.BASE -> 1
                            WordCategory.LEARNED -> 2
                        }
                    }.thenByDescending { it.frequency }
            )

        val anchored = if (exactMatches.isNotEmpty()) exactMatches[0].word else composing
        val others = (exactMatches.drop(if (exactMatches.isNotEmpty()) 1 else 0) + longerMatches)
            .map { it.word }
            .take(20)

        return Pair(others, anchored)
    }

    private fun processXt9Suggestions(
        digitSeq: String,
        rawSeq: String,
        constraints: List<String>,
        lastWord: String?
    ): Pair<List<String>, String?> {
        if (digitSeq.isEmpty()) return Pair(emptyList(), null)

        val targetLength = digitSeq.length
        val contactsEnabled = contactProvider()

        val xt9Constraints = constraints.ifEmpty { digitSeq.map { it.toString() } }
        val learnedAll = LearnedDictionary.getSuggestions(xt9Constraints, lastWord)
        val learnedExact = learnedAll.filter { it.word.length == targetLength }
        val learnedPrefix = learnedAll.filter { it.word.length > targetLength }

        val aospExact = AospDictionary.getSuggestionsForSequence(digitSeq)
        val aospPrefix = AospDictionary.getWordsStartingWith(digitSeq).filter { it.word.length > targetLength }
        val containing = if (targetLength >= 2) AospDictionary.getWordsContaining(rawSeq) else emptyList()

        val mergedCandidates = HashMap<String, AospDictionary.WordSuggestion>()
        fun merge(list: List<AospDictionary.WordSuggestion>) {
            list.forEach { suggestion ->
                val key = suggestion.word.lowercase()
                val existing = mergedCandidates[key]

                if (existing == null) {
                    mergedCandidates[key] = suggestion
                } else {
                    // Keep suggestion with higher category priority
                    val shouldReplace = when {
                        suggestion.category == WordCategory.PROTECTED && existing.category != WordCategory.PROTECTED -> true
                        suggestion.category == existing.category && suggestion.frequency > existing.frequency -> true
                        suggestion.category == WordCategory.BASE && existing.category == WordCategory.LEARNED -> true
                        else -> false
                    }

                    if (shouldReplace) {
                        mergedCandidates[key] = suggestion
                    }
                }
            }
        }

        merge(learnedExact)
        merge(aospExact)
        merge(learnedPrefix)
        merge(aospPrefix)
        merge(containing)

        if (contactsEnabled) {
            val contactsExact = ContactsDictionary.getSuggestionsForSequence(digitSeq).map { AospDictionary.WordSuggestion(it, Int.MAX_VALUE - 1, WordCategory.BASE) }
            val contactsPrefix = ContactsDictionary.getSuggestionsForPrefix(digitSeq).map { AospDictionary.WordSuggestion(it, Int.MAX_VALUE - 1, WordCategory.BASE) }
            merge(contactsExact)
            merge(contactsPrefix)
        }

        val allCandidates = mergedCandidates.values.toList()

        // Sort by category priority first, then frequency
        val exactMatches = allCandidates.filter { it.word.length == targetLength }
            .sortedWith(
                compareBy<AospDictionary.WordSuggestion> { suggestion ->
                    when (suggestion.category) {
                        WordCategory.PROTECTED -> 0  // Highest priority
                        WordCategory.BASE -> 1
                        WordCategory.LEARNED -> 2    // Lowest priority
                    }
                }.thenByDescending { it.frequency }
            )

        val longerMatches = allCandidates.filter { it.word.length > targetLength }
            .sortedWith(
                compareBy<AospDictionary.WordSuggestion> { it.word.length }
                    .thenBy { suggestion ->
                        when (suggestion.category) {
                            WordCategory.PROTECTED -> 0
                            WordCategory.BASE -> 1
                            WordCategory.LEARNED -> 2
                        }
                    }.thenByDescending { it.frequency }
            )

        val predictions = (exactMatches + longerMatches).map { it.word }
        val finalPredictions = if (predictions.isEmpty()) listOf(rawSeq) else predictions

        val anchored = finalPredictions[0]
        val others = finalPredictions.drop(1).take(20)

        return Pair(others, anchored)
    }

    fun requestNextWordSuggestions(lastWord: String?, isInputSensitive: Boolean = false) {
        if (lastWord == null || isInputSensitive) return
        nextWordJob?.cancel()
        nextWordJob = serviceScope.launch {
            val combined = withContext(Dispatchers.Default) {
                val learned = LearnedDictionary.getNextWordSuggestions(lastWord)
                val aosp = AospBigrams.getNextWordSuggestions(lastWord)
                (learned + aosp).distinct().take(20)
            }
            onSuggestionsReady(combined, null)
        }
    }
}
