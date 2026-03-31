package com.musa.t9keyboard

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BKTreeTest {

    private lateinit var bkTree: BKTree

    @Before
    fun setup() {
        bkTree = BKTree()
    }

    @Test
    fun testBKTreeSearchDistances() {
        bkTree.insert("apple", 100)
        bkTree.insert("apply", 50)
        bkTree.insert("apples", 30)
        bkTree.insert("banana", 200)

        // Distance 0 (exact match)
        val dist0 = bkTree.search("apple", 0)
        assertEquals(1, dist0.size)
        assertEquals("apple", dist0[0].first)

        // Distance 1
        // "apple" -> "apply" is 1 edit (e -> y)
        val dist1 = bkTree.search("apple", 1)
        assertTrue(dist1.any { it.first == "apple" })
        assertTrue(dist1.any { it.first == "apply" })
        assertTrue(dist1.any { it.first == "apples" })
        assertFalse(dist1.any { it.first == "banana" })

        // Distance 2
        // "apple" -> "apples" is 1 edit
        // "apple" -> "apples" is distance 1
        // Let's find a distance 2: "apple" -> "applys" (if it existed)
        bkTree.insert("apricot", 10)
        // "apple" (5) vs "apricot" (7):
        // a-p-p-l-e
        // a-p-r-i-c-o-t
        // a p p l e _ _
        // a p r i c o t
        // dist: 1(p->r) + 1(p->i) + 1(l->c) + 1(e->o) + 1(_->t) = 5

        val dist2 = bkTree.search("apple", 2)
        // Should find "apple", "apply", "apples"
        assertEquals(3, dist2.size)
    }

    @Test
    fun testEmptyInput() {
        // No crash on empty tree
        val result1 = bkTree.search("anything", 1)
        assertTrue(result1.isEmpty())

        // No crash on empty query
        bkTree.insert("something", 100)
        val result2 = bkTree.search("", 1)
        // "something" (9) vs "" (0) is distance 9. Should be empty.
        assertTrue(result2.isEmpty())

        // Distance equal to length
        val result3 = bkTree.search("", 9)
        assertTrue(result3.any { it.first == "something" })
    }
}
