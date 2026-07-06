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
        assertEquals('1', T9Utils.getDigitForChar('a'))
        assertEquals('1', T9Utils.getDigitForChar('b'))
        assertEquals('2', T9Utils.getDigitForChar('c'))
        assertEquals('7', T9Utils.getDigitForChar('r'))
        assertEquals('*', T9Utils.getDigitForChar('g'))
        assertEquals('#', T9Utils.getDigitForChar('o'))
        assertEquals('+', T9Utils.getDigitForChar('x'))
        assertEquals('/', T9Utils.getDigitForChar('z'))
        assertEquals('@', T9Utils.getDigitForChar('.'))
        assertEquals('@', T9Utils.getDigitForChar('?'))
    }

    @Test
    fun `T9Utils builds sequences with the pair mapping`() {
        // h=*, e=3, l=5, l=5, o=#
        assertEquals("*355#", T9Utils.getT9Sequence("hello"))
        // s=8, t=8, y=/
        assertEquals("88/", T9Utils.getT9Sequence("sty"))
    }

    @Test
    fun `isKeyCode distinguishes wildcard codes from exact letters`() {
        listOf('1', '2', '9', '*', '#', '+', '/', '@').forEach {
            assertTrue("$it should be a key code", T9Utils.isKeyCode(it))
        }
        // '0' is not a LETTER/SYMBOL_CYCLE code - it's only ever SPACE's hint/long-press digit
        listOf('a', 'z', ' ', '-', '0').forEach {
            assertFalse("$it should not be a key code", T9Utils.isKeyCode(it))
        }
    }

    @Test
    fun `getFirstCharForDigit returns the first letter of the pair`() {
        assertEquals('a', T9Utils.getFirstCharForDigit('1'))
        assertEquals('q', T9Utils.getFirstCharForDigit('7'))
        assertEquals('g', T9Utils.getFirstCharForDigit('*'))
        assertEquals('w', T9Utils.getFirstCharForDigit('+'))
        assertEquals('y', T9Utils.getFirstCharForDigit('/'))
        assertEquals('.', T9Utils.getFirstCharForDigit('@'))
    }

    @Test
    fun `digits 1-9 form a number-pad shape across rows 1-3, 0 centered on space`() {
        fun hintOf(label: String) = KeyboardLayout.ALL_KEYS.first { it.label == label }.hint
        // Column 2-4 of rows 1-3 read as a 3x3 pad
        assertEquals("1", hintOf("AB")); assertEquals("2", hintOf("CD")); assertEquals("3", hintOf("EF"))
        assertEquals("4", hintOf("IJ")); assertEquals("5", hintOf("KL")); assertEquals("6", hintOf("MN"))
        assertEquals("7", hintOf("QR")); assertEquals("8", hintOf("ST")); assertEquals("9", hintOf("UV"))
        assertEquals("0", hintOf("SPACE"))
        // Column 1 keys sit outside the pad and carry symbols instead
        assertEquals("@", hintOf(".,!?")); assertEquals("*", hintOf("GH")); assertEquals("#", hintOf("OP"))
    }
}
