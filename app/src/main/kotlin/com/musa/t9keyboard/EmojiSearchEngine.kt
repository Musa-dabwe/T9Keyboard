package com.musa.t9keyboard

object EmojiSearchEngine {

    fun search(query: String, maxResults: Int = 5): List<String> {
        if (query.isBlank()) return emptyList()
        val q = query.lowercase().trim()

        // Score each emoji using keywords from EmojiSearchData
        val scored = EmojiSearchData.keywords.entries.mapNotNull { (emoji, keywords) ->
            val score = scoreMatch(q, keywords)
            if (score > 0) emoji to score else null
        }

        return scored.sortedByDescending { it.second }
            .take(maxResults)
            .map { it.first }
    }

    private fun scoreMatch(query: String, keywords: List<String>): Int {
        var best = 0
        for (keyword in keywords) {
            val score = when {
                keyword == query -> 100                          // exact match
                keyword.startsWith(query) -> 80                 // prefix match
                keyword.contains(query) -> 60                   // substring match
                fuzzyMatch(query, keyword) -> 40                // fuzzy match
                else -> 0
            }
            if (score > best) best = score
        }
        return best
    }

    private fun fuzzyMatch(query: String, keyword: String): Boolean {
        // Check if all query chars appear in keyword in order (subsequence check)
        if (query.length > keyword.length) return false
        var ki = 0
        for (qc in query) {
            while (ki < keyword.length && keyword[ki] != qc) ki++
            if (ki >= keyword.length) return false
            ki++
        }
        return true
    }
}
