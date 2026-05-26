package com.musa.t9keyboard

import com.musa.t9keyboard.BuildConfig
import android.content.Context
import android.provider.ContactsContract
import android.util.Log

object ContactsDictionary {
    private val t9Map = mutableMapOf<String, MutableList<String>>()
    private val prefixMap = mutableMapOf<String, MutableList<String>>()
    private var isLoaded = false

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

    private fun getDigitForChar(c: Char): Char {
        return when (c.lowercaseChar()) {
            'a', 'b', 'c' -> '2'
            'd', 'e', 'f' -> '3'
            'g', 'h', 'i' -> '4'
            'j', 'k', 'l' -> '5'
            'm', 'n', 'o' -> '6'
            'p', 'q', 'r', 's' -> '7'
            't', 'u', 'v' -> '8'
            'w', 'x', 'y', 'z' -> '9'
            else -> ' '
        }
    }

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
}
