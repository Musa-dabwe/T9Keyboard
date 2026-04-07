package com.musa.t9keyboard

import android.content.Context
import android.provider.ContactsContract
import android.util.Log

object ContactsDictionary {
    private val t9Map = mutableMapOf<String, MutableList<String>>()
    private val prefixMap = mutableMapOf<String, MutableList<String>>()
    private var isLoaded = false

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
        Log.d("ContactsDictionary", "Loaded ${uniqueWords.size} contact name tokens")
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

    private fun getDigitForChar(c: Char): Char {
        return T9Utils.getDigitForChar(c)
    }

    private fun getT9Sequence(word: String): String {
        return word.map { getDigitForChar(it) }.joinToString("")
    }

    fun getSuggestionsForSequence(digitSeq: String): List<String> {
        return t9Map[digitSeq] ?: emptyList()
    }

    fun getSuggestionsForPrefix(digitSeq: String): List<String> {
        return prefixMap[digitSeq] ?: emptyList()
    }

    fun isEmpty(): Boolean {
        return t9Map.isEmpty()
    }

    fun isLoaded(): Boolean = isLoaded

    fun clear() {
        t9Map.clear()
        prefixMap.clear()
        isLoaded = false
    }
}
