package com.musa.t9keyboard

import kotlinx.coroutines.*

class SuggestionEngine(
    private val serviceScope: CoroutineScope,
    private val contactProvider: () -> Boolean, // returns contactSuggestionsEnabled && contactPermissionGranted
    private val onSuggestionsReady: (List<String>, String?) -> Unit
) {

    private var suggestionJob: Job? = null
    private var nextWordJob: Job? = null

    fun requestSuggestions(editorState: EditorState, xt9Enabled: Boolean) {
        suggestionJob?.cancel()
        val constraints = editorState.currentWordConstraints.toList()
        val composing = editorState.composingText.toString()
        val lastWord = editorState.lastCommittedWord
        val xt9DigitSeq = editorState.xt9DigitSequence.toString()
        val xt9RawSeq = editorState.xt9RawSequence.toString()

        suggestionJob = serviceScope.launch {
            val result = withContext(Dispatchers.Default) {
                if (xt9Enabled) {
                    processXt9Suggestions(xt9DigitSeq, xt9RawSeq, constraints, lastWord)
                } else {
                    processMultiTapSuggestions(composing, constraints, lastWord)
                }
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

        val allCandidates = if (contactsEnabled) {
            val seq = constraints.map { if (it.length == 1 && it[0].isDigit()) it else T9Utils.getDigitForChar(it[0]).toString() }.joinToString("")
            val contacts = ContactsDictionary.getSuggestionsForSequence(seq).map { AospDictionary.WordSuggestion(it, Int.MAX_VALUE - 1) }
            val contactPrefixes = ContactsDictionary.getSuggestionsForPrefix(seq).map { AospDictionary.WordSuggestion(it, Int.MAX_VALUE - 1) }
            (learned + contacts + aosp + contactPrefixes + containing).distinctBy { it.word.lowercase() }
        } else {
            (learned + aosp + containing).distinctBy { it.word.lowercase() }
        }

        val learnedSet = learned.map { it.word.lowercase() }.toHashSet()
        val exactMatches = allCandidates.filter { it.word.length == targetLength }
            .sortedWith(compareByDescending<AospDictionary.WordSuggestion> { learnedSet.contains(it.word.lowercase()) }
                .thenByDescending { it.frequency })

        val longerMatches = allCandidates.filter { it.word.length > targetLength }
            .sortedWith(compareBy<AospDictionary.WordSuggestion> { it.word.length }
                .thenByDescending { learnedSet.contains(it.word.lowercase()) }
                .thenByDescending { it.frequency })

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

        val allCandidates = if (contactsEnabled) {
            val contactsExact = ContactsDictionary.getSuggestionsForSequence(digitSeq).map { AospDictionary.WordSuggestion(it, Int.MAX_VALUE - 1) }
            val contactsPrefix = ContactsDictionary.getSuggestionsForPrefix(digitSeq).map { AospDictionary.WordSuggestion(it, Int.MAX_VALUE - 1) }
            (learnedExact + contactsExact + aospExact + learnedPrefix + contactsPrefix + aospPrefix + containing)
        } else {
            (learnedExact + aospExact + learnedPrefix + aospPrefix + containing)
        }.distinctBy { it.word.lowercase() }

        val learnedSet = (learnedExact + learnedPrefix).map { it.word.lowercase() }.toHashSet()

        val exactMatches = allCandidates.filter { it.word.length == targetLength }
            .sortedWith(compareByDescending<AospDictionary.WordSuggestion> { learnedSet.contains(it.word.lowercase()) }
                .thenByDescending { it.frequency })

        val longerMatches = allCandidates.filter { it.word.length > targetLength }
            .sortedWith(compareBy<AospDictionary.WordSuggestion> { it.word.length }
                .thenByDescending { learnedSet.contains(it.word.lowercase()) }
                .thenByDescending { it.frequency })

        val predictions = (exactMatches + longerMatches).map { it.word }
        val finalPredictions = if (predictions.isEmpty()) listOf(rawSeq) else predictions

        val anchored = finalPredictions[0]
        val others = finalPredictions.drop(1).take(20)

        return Pair(others, anchored)
    }

    fun requestNextWordSuggestions(lastWord: String?) {
        if (lastWord == null) return
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
