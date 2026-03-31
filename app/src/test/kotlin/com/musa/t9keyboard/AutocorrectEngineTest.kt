package com.musa.t9keyboard

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class AutocorrectEngineTest {

    @Mock
    lateinit var mockBKTree: BKTree
    @Mock
    lateinit var mockLearnedDictionary: LearnedDictionary

    private lateinit var autocorrectEngine: AutocorrectEngine

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        autocorrectEngine = AutocorrectEngine(mockBKTree, mockLearnedDictionary)
    }

    @Test
    fun testAutocorrectSensitivity() {
        // Mock candidates for "appl"
        `when`(mockBKTree.search("appl", 0)).thenReturn(emptyList()) // No exact match
        `when`(mockBKTree.search("appl", 1)).thenReturn(listOf("apple" to 1500))
        `when`(mockBKTree.search("appl", 2)).thenReturn(listOf("apple" to 1500, "apply" to 2000))

        `when`(mockLearnedDictionary.isValidWord("appl")).thenReturn(false)

        // Conservative (0): distance 1 only
        val result0 = autocorrectEngine.getCorrection("appl", 0)
        assertEquals("apple", result0)

        // Normal (1): distance 1 for words <= 6 chars
        val result1 = autocorrectEngine.getCorrection("appl", 1)
        assertEquals("apple", result1)

        // Normal (1): distance 2 for words >= 7 chars
        // "applica" (7) -> distance 2: "application" (11)? No, "apply" (5) is dist 2.
        // Actually "applica" (7) vs "apply" (5): a-p-p-l-i-c-a vs a-p-p-l-y
        // edits: i->y, c->delete, a->delete. Distance 3.

        `when`(mockBKTree.search("applica", 0)).thenReturn(emptyList())
        `when`(mockBKTree.search("applica", 1)).thenReturn(emptyList())
        `when`(mockBKTree.search("applica", 2)).thenReturn(listOf("applied" to 3000))

        val result1Long = autocorrectEngine.getCorrection("applica", 1)
        assertEquals("applied", result1Long)

        // Aggressive (2): distance 2 for all
        val result2 = autocorrectEngine.getCorrection("appl", 2)
        // Aggressive should return highest frequency from distance 2
        assertEquals("apply", result2)
    }

    @Test
    fun testAutocorrectImmunity() {
        `when`(mockLearnedDictionary.isValidWord("mispelled")).thenReturn(true)

        // Even if there's a perfect match at distance 1
        `when`(mockBKTree.search("mispelled", 1)).thenReturn(listOf("misspelled" to 5000))

        val result = autocorrectEngine.getCorrection("mispelled", 1)
        assertNull("Learned words should be immune to autocorrect", result)
    }

    @Test
    fun testMinLengthRequirement() {
        // Word length >= 4
        val resultShort = autocorrectEngine.getCorrection("abc", 1)
        assertNull("Words shorter than 4 should not be autocorrected", resultShort)
    }

    @Test
    fun testAllCapsImmunity() {
        // All-caps should be immune
        val resultCaps = autocorrectEngine.getCorrection("APPLE", 1)
        assertNull("All-caps words should be immune to autocorrect", resultCaps)
    }
}
