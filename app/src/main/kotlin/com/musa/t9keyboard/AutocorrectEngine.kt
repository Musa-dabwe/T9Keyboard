package com.musa.t9keyboard

class AutocorrectEngine(private val bkTree: BKTree,
                        private val learnedDictionary: LearnedDictionary) {

    /**
     * Returns null if no correction needed or word is valid.
     * Returns the best correction string if one is found.
     *
     * Sensitivity: 0 (Conservative), 1 (Normal), 2 (Aggressive)
     */
    fun getCorrection(typedWord: String, sensitivity: Int): String? {
        val lowerTyped = typedWord.lowercase()

        // Rule: Word length ≥ 4
        if (typedWord.length < 4) return null

        // Rule: Word not found in AospDictionary and not found in LearnedDictionary
        // Check exact match in BKTree (distance 0)
        val exactMatchAosp = bkTree.search(lowerTyped, 0).isNotEmpty()
        if (exactMatchAosp) return null

        // Check exact match in LearnedDictionary
        if (learnedDictionary.contains(lowerTyped)) return null

        // Rule: Word is not all-caps
        if (typedWord.isNotEmpty() && typedWord.all { it.isUpperCase() }) return null

        // Determine max edit distance based on sensitivity
        val maxDist = when (sensitivity) {
            0 -> 1 // Conservative: distance 1 only
            1 -> { // Normal: distance 1 for words ≤6 chars, distance 2 for words ≥7
                if (typedWord.length <= 6) 1 else 2
            }
            2 -> 2 // Aggressive: distance 2 for all
            else -> 1
        }

        // Find candidates in AospDictionary via BKTree
        val candidates = bkTree.search(lowerTyped, maxDist)

        // Filter candidates: Candidate frequency > 1000 in AOSP dictionary
        // Among valid candidates, return the one with the highest frequency
        return candidates
            .filter { it.second > 1000 }
            .maxByOrNull { it.second }
            ?.first
    }
}
