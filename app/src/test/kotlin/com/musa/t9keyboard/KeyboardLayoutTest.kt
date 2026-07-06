package com.musa.t9keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardLayoutTest {

    @Test
    fun `grid is exactly 5 columns by 4 rows`() {
        assertEquals(4, KeyboardLayout.ROWS.size)
        KeyboardLayout.ROWS.forEach { row ->
            assertEquals(5, row.sumOf { it.colSpan })
        }
    }

    @Test
    fun `row 4 is WX SPACE YZ Enter with no duplicate YZ`() {
        val row4 = KeyboardLayout.ROWS[3]
        assertEquals(listOf("WX", "SPACE", "YZ", ""), row4.map { it.label })
        assertEquals(listOf(KeyType.LETTER, KeyType.SPACE, KeyType.LETTER, KeyType.ENTER), row4.map { it.type })
        assertEquals(2, row4[1].colSpan)
        assertEquals(1, KeyboardLayout.ALL_KEYS.count { it.label == "YZ" })
    }

    @Test
    fun `all 26 letters are reachable exactly once`() {
        val letters = KeyboardLayout.ALL_KEYS
            .filter { it.type == KeyType.LETTER }
            .flatMap { it.chars }
        assertEquals(26, letters.size)
        assertEquals(('a'..'z').toList(), letters.sorted())
    }

    @Test
    fun `letter keys carry exactly two characters`() {
        KeyboardLayout.ALL_KEYS.filter { it.type == KeyType.LETTER }.forEach {
            assertEquals("Key ${it.label}", 2, it.chars.size)
        }
    }

    @Test
    fun `engine key codes are unique and never lowercase letters`() {
        val codes = KeyboardLayout.ALL_KEYS.filter { it.code != ' ' }.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        codes.forEach { assertFalse("Code $it must not be a letter", it in 'a'..'z') }
    }

    @Test
    fun `T9Utils maps letters to their key codes`() {
        assertEquals('2', T9Utils.getDigitForChar('a'))
        assertEquals('2', T9Utils.getDigitForChar('b'))
        assertEquals('3', T9Utils.getDigitForChar('c'))
        assertEquals('0', T9Utils.getDigitForChar('r'))
        assertEquals('*', T9Utils.getDigitForChar('s'))
        assertEquals('#', T9Utils.getDigitForChar('v'))
        assertEquals('+', T9Utils.getDigitForChar('x'))
        assertEquals('/', T9Utils.getDigitForChar('z'))
        assertEquals('1', T9Utils.getDigitForChar('.'))
        assertEquals('1', T9Utils.getDigitForChar('?'))
    }

    @Test
    fun `T9Utils builds sequences with the pair mapping`() {
        // h=5, e=4, l=7, l=7, o=9
        assertEquals("54779", T9Utils.getT9Sequence("hello"))
        // s=*, t=*, y=/
        assertEquals("**/", T9Utils.getT9Sequence("sty"))
    }

    @Test
    fun `isKeyCode distinguishes wildcard codes from exact letters`() {
        listOf('1', '2', '9', '0', '*', '#', '+', '/').forEach {
            assertTrue("$it should be a key code", T9Utils.isKeyCode(it))
        }
        listOf('a', 'z', ' ', '-').forEach {
            assertFalse("$it should not be a key code", T9Utils.isKeyCode(it))
        }
    }

    @Test
    fun `getFirstCharForDigit returns the first letter of the pair`() {
        assertEquals('a', T9Utils.getFirstCharForDigit('2'))
        assertEquals('q', T9Utils.getFirstCharForDigit('0'))
        assertEquals('s', T9Utils.getFirstCharForDigit('*'))
        assertEquals('w', T9Utils.getFirstCharForDigit('+'))
        assertEquals('y', T9Utils.getFirstCharForDigit('/'))
    }
}
