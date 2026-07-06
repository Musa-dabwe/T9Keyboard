package com.musa.t9keyboard

import com.musa.t9keyboard.BuildConfig
import android.content.Context
import android.provider.ContactsContract
import android.util.Log

object ContactsDictionary {
    private val t9Map = mutableMapOf<String, MutableList<String>>()
    private val prefixMap = mutableMapOf<String, MutableList<String>>()
    private var isLoaded = false

    @Synchronized
    fun load(context: Context) {
        if (isLoaded) return
        clear()
        val cursor = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
            null, null, null
        )

        val uniqueWords = mutableSetOf<String>()

        cursor?.use {
            val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            while (it.moveToNext() && uniqueWords.size < 2000) {
                val fullName = it.getString(nameIndex) ?: continue
                val words = fullName.split("\\s+".toRegex())
                for (word in words) {
                    val cleanWord = word.trim()
                    if (cleanWord.isNotEmpty() && isValidWord(cleanWord)) {
                        uniqueWords.add(cleanWord)
                        if (uniqueWords.size >= 2000) break
                    }
                }
            }
        }

        val sortedWords = uniqueWords.sortedWith(
            compareBy<String> { it.length }.thenBy(String.CASE_INSENSITIVE_ORDER, { it })
        )

        for (word in sortedWords) {
            val sequence = getT9Sequence(word)
            if (sequence.isNotEmpty()) {
                // Exact match map
                t9Map.getOrPut(sequence) { mutableListOf() }.add(word)

                // Prefix match map
                for (i in 1 until sequence.length) {
                    val prefix = sequence.substring(0, i)
                    val list = prefixMap.getOrPut(prefix) { mutableListOf() }
                    if (list.size < 10) {
                        list.add(word)
                    }
                }
                // Also add full sequence to prefix map if it's not already there (though t9Map covers exact)
                val fullPrefix = sequence
                val fullList = prefixMap.getOrPut(fullPrefix) { mutableListOf() }
                if (fullList.size < 10) {
                    fullList.add(word)
                }
            }
        }
        isLoaded = true
        if (BuildConfig.DEBUG) Log.d("ContactsDictionary", "Loaded ${uniqueWords.size} contact name tokens")
    }

    private fun isValidWord(word: String): Boolean {
        for (c in word) {
            val digit = getDigitForChar(c)
            if (digit == ' ' || !c.isLetter()) {
                return false
            }
        }
        return true
    }

    private fun getDigitForChar(c: Char): Char = T9Utils.getDigitForChar(c)

    private fun getT9Sequence(word: String): String {
        return word.map { getDigitForChar(it) }.joinToString("")
    }

    @Synchronized
    fun getSuggestionsForSequence(digitSeq: String): List<String> {
        return t9Map[digitSeq] ?: emptyList()
    }

    @Synchronized
    fun getSuggestionsForPrefix(digitSeq: String): List<String> {
        return prefixMap[digitSeq] ?: emptyList()
    }


    @Synchronized
    fun isLoaded(): Boolean = isLoaded

    @Synchronized
    fun clear() {
        t9Map.clear()
        prefixMap.clear()
        isLoaded = false
    }

    /**
     * Check if a name exists in loaded contacts.
     * @param name Name to check (case-insensitive)
     * @return true if the name exists in contacts
     */
    @Synchronized
    fun containsName(name: String): Boolean {
        val normalized = name.lowercase()
        // Check all values in both t9Map and prefixMap
        val allWords = (t9Map.values.flatten() + prefixMap.values.flatten()).toSet()
        return allWords.any { it.lowercase() == normalized }
    }
}
