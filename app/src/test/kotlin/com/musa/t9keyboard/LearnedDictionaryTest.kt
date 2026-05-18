package com.musa.t9keyboard

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.*
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class LearnedDictionaryTest {

    @Mock
    lateinit var mockContext: Context
    @Mock
    lateinit var mockPrefs: SharedPreferences
    @Mock
    lateinit var mockEditor: SharedPreferences.Editor

    private val prefMap = mutableMapOf<String, Any>()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)

        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockPrefs)
        `when`(mockPrefs.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putInt(anyString(), anyInt())).thenAnswer {
            val key = it.arguments[0] as String
            val value = it.arguments[1] as Int
            prefMap[key] = value
            mockEditor
        }
        `when`(mockEditor.putLong(anyString(), anyLong())).thenAnswer {
            val key = it.arguments[0] as String
            val value = it.arguments[1] as Long
            prefMap[key] = value
            mockEditor
        }
        `when`(mockEditor.remove(anyString())).thenAnswer {
            prefMap.remove(it.arguments[0] as String)
            mockEditor
        }
        `when`(mockEditor.clear()).thenAnswer {
            prefMap.clear()
            mockEditor
        }
        `when`(mockEditor.apply()).then {
            // In a real test, we'd update mockPrefs.all here if needed
        }
        `when`(mockPrefs.all).thenAnswer { prefMap }
        `when`(mockPrefs.getLong(anyString(), anyLong())).thenAnswer {
            val key = it.arguments[0] as String
            val defValue = it.arguments[1] as Long
            prefMap[key] as? Long ?: defValue
        }

        // Initialize AospDictionary with empty data to avoid NPE
        `when`(mockContext.assets).thenThrow(RuntimeException("Mocked exception to avoid opening real assets"))
        runBlocking {
            try {
                AospDictionary.loadFromAssets(mockContext)
            } catch (e: Exception) {
                // Expected to fail opening assets
            }
        }

        // We need to inject the mockPrefs into LearnedDictionary.
        // Since it's an object and prefs is private, we must call load() which sets it.
        LearnedDictionary.load(mockContext)
        LearnedDictionary.clear()
    }

    @Test
    fun testBigramBoosting() {
        // 1. Learn words to populate dictionary
        LearnedDictionary.learnWord("apple")
        LearnedDictionary.learnWord("banana")

        // 2. Establish bigram relationship: "eat" -> "banana"
        LearnedDictionary.learnWord("banana", "eat")

        // 3. Get suggestions with no context
        val noContext = LearnedDictionary.getSuggestions(listOf("2", "2", "6", "2", "6", "2")) // "banana"
        val bananaNoContext = noContext.find { it.word == "banana" }!!

        // 4. Get suggestions with "eat" context
        val context = LearnedDictionary.getSuggestions(listOf("2", "2", "6", "2", "6", "2"), "eat")
        val bananaWithContext = context.find { it.word == "banana" }!!

        assertEquals("Bigram boost multiplier should be 3", bananaNoContext.frequency + 3, bananaWithContext.frequency)

        // Verify ranking: bigram match should rank higher than a more frequent non-bigram word
        repeat(10) { LearnedDictionary.learnWord("apple") }

        LearnedDictionary.clear()
        LearnedDictionary.load(mockContext)

        repeat(5) { LearnedDictionary.learnWord("bat") } // freq 5
        repeat(2) { LearnedDictionary.learnWord("cat") } // freq 2

        // Context: "black" -> "cat"
        LearnedDictionary.learnWord("cat", "black")

        val suggestions = LearnedDictionary.getSuggestions(listOf("2", "2", "8"), "black")

        assertEquals("cat", suggestions[0].word)
    }

    @Test
    fun testRecencyDecay() {
        LearnedDictionary.learnWord("recent")
        val now = System.currentTimeMillis()

        // 1. Recent word (<= 7 days) -> multiplier 1.0
        val suggestions1 = LearnedDictionary.getSuggestions(listOf("7", "3", "2", "3", "6", "8"))
        assertEquals(257, suggestions1.find { it.word == "recent" }?.frequency)

        // 2. 15 days ago (<= 30 days) -> multiplier 0.75
        LearnedDictionary.clear()
        prefMap["freq_recent"] = 1
        prefMap["last_typed_recent"] = now - (15L * 86_400_000L)
        LearnedDictionary.load(mockContext)
        val suggestions2 = LearnedDictionary.getSuggestions(listOf("7", "3", "2", "3", "6", "8"))
        assertEquals(192, suggestions2.find { it.word == "recent" }?.frequency)

        // 3. 45 days ago (<= 90 days) -> multiplier 0.5
        LearnedDictionary.clear()
        prefMap["freq_recent"] = 1
        prefMap["last_typed_recent"] = now - (45L * 86_400_000L)
        LearnedDictionary.load(mockContext)
        val suggestions3 = LearnedDictionary.getSuggestions(listOf("7", "3", "2", "3", "6", "8"))
        assertEquals(128, suggestions3.find { it.word == "recent" }?.frequency)

        // 4. 100 days ago (> 90 days) -> multiplier 0.25
        LearnedDictionary.clear()
        prefMap["freq_recent"] = 2
        prefMap["last_typed_recent"] = now - (100L * 86_400_000L)
        LearnedDictionary.load(mockContext)
        val suggestions4 = LearnedDictionary.getSuggestions(listOf("7", "3", "2", "3", "6", "8"))
        val freq4 = suggestions4.find { it.word == "recent" }?.frequency ?: 0
        assertTrue("Decayed frequency should be at least 64, was $freq4", freq4 >= 64)
    }

    @Test
    fun testForgettingMechanism() {
        // cleanup() removes words with freq=1 that haven't been typed in 90 days
        val ninetyOneDaysAgo = System.currentTimeMillis() - (91L * 86_400_000L)

        prefMap["freq_oldword"] = 1
        prefMap["last_typed_oldword"] = ninetyOneDaysAgo

        LearnedDictionary.load(mockContext)

        val suggestions = LearnedDictionary.getSuggestions(listOf("6", "5", "3", "9", "6", "7", "3")) // "oldword"
        assertNull("Old low-frequency word should be forgotten", suggestions.find { it.word == "oldword" })

        LearnedDictionary.clear()
        prefMap.clear()

        prefMap["freq_activeword"] = 1
        prefMap["last_typed_activeword"] = System.currentTimeMillis()
        LearnedDictionary.load(mockContext)
        val suggestions2 = LearnedDictionary.getSuggestions(listOf("2", "2", "8", "4", "8", "3", "9", "6", "7", "3")) // "activeword"
        assertNotNull("Recent word should be kept", suggestions2.find { it.word == "activeword" })
    }
}
