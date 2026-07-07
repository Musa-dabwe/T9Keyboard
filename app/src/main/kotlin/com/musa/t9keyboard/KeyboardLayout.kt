package com.musa.t9keyboard

enum class KeyType {
    LETTER,        // 2-letter multi-tap key
    SYMBOL_CYCLE,  // .,!? multi-tap key
    BACKSPACE,
    SYM_PAGE,
    SHIFT,
    NUM_PAGE,
    SPACE,
    ENTER
}

/**
 * Definition of a single key in the 5-column grid.
 *
 * @param label     Primary centered label ("AB", "SPACE", ...)
 * @param hint      Muted top-right corner hint ("1", "123", "@", ...). Enter has none -
 *                  it shows an accent-colored dot instead, rendered directly in the layout XML.
 * @param chars     Multi-tap cycle characters (letters/punctuation), empty for action keys
 * @param type      Key behavior class
 * @param colSpan   Grid columns occupied (SPACE spans 2)
 * @param code      Engine key code used in T9 digit sequences; ' ' for action keys.
 *                  Codes must be unique per LETTER/SYMBOL_CYCLE key and must never be
 *                  a lowercase letter (letters denote exact-match constraints).
 * @param numLabel  Label shown in number mode (null keeps the primary label)
 * @param longPressChar  Character committed on long-press (the corner hint symbol), if any
 * @param longPressOpensNumPage  WX long-press ("123" hint) toggles the number page
 * @param numHint  Muted corner hint shown in number mode (only the comma key's "-")
 * @param numLongPressChar  Character committed on long-press while in number mode.
 *                          All other keys have no long-press in number mode - their
 *                          long-press char would just duplicate the tap character.
 */
data class KeyDef(
    val label: String,
    val hint: String?,
    val chars: List<Char>,
    val type: KeyType,
    val colSpan: Int = 1,
    val code: Char = ' ',
    val numLabel: String? = null,
    val longPressChar: Char? = null,
    val longPressOpensNumPage: Boolean = false,
    val numHint: String? = null,
    val numLongPressChar: Char? = null
)

/**
 * Single source of truth for the 5-column × 4-row, 2-letters-per-key layout.
 *
 * Digits follow a phone-number-pad shape: columns 2-4 of rows 1-3 read as a
 * 3x3 pad (1-9), with 0 centered directly below on the space bar - exactly
 * where a dial pad's 0 sits under 7/8/9. Column 1 and column 5 keys sit
 * outside the pad and carry symbols instead ('@', '*', '#').
 *
 * Row 1: @ · AB(1) · CD(2) · EF(3) · ⌫
 * Row 2: * · IJ(4) · KL(5) · MN(6) · SYM
 * Row 3: # · QR(7) · ST(8) · UV(9) · SHIFT
 * Row 4: WX · SPACE(0, ×2) · YZ · ⏎
 */
object KeyboardLayout {

    val ROWS: List<List<KeyDef>> = listOf(
        listOf(
            KeyDef(".,!?", "@", listOf('.', ',', '!', '?'), KeyType.SYMBOL_CYCLE, code = '@', numLabel = "@", longPressChar = '@'),
            KeyDef("AB", "1", listOf('a', 'b'), KeyType.LETTER, code = '1', numLabel = "1", longPressChar = '1'),
            KeyDef("CD", "2", listOf('c', 'd'), KeyType.LETTER, code = '2', numLabel = "2", longPressChar = '2'),
            KeyDef("EF", "3", listOf('e', 'f'), KeyType.LETTER, code = '3', numLabel = "3", longPressChar = '3'),
            KeyDef("", null, emptyList(), KeyType.BACKSPACE)
        ),
        listOf(
            KeyDef("GH", "*", listOf('g', 'h'), KeyType.LETTER, code = '*', numLabel = "*", longPressChar = '*'),
            KeyDef("IJ", "4", listOf('i', 'j'), KeyType.LETTER, code = '4', numLabel = "4", longPressChar = '4'),
            KeyDef("KL", "5", listOf('k', 'l'), KeyType.LETTER, code = '5', numLabel = "5", longPressChar = '5'),
            KeyDef("MN", "6", listOf('m', 'n'), KeyType.LETTER, code = '6', numLabel = "6", longPressChar = '6'),
            KeyDef("SYM", null, emptyList(), KeyType.SYM_PAGE)
        ),
        listOf(
            KeyDef("OP", "#", listOf('o', 'p'), KeyType.LETTER, code = '#', numLabel = "#", longPressChar = '#'),
            KeyDef("QR", "7", listOf('q', 'r'), KeyType.LETTER, code = '7', numLabel = "7", longPressChar = '7'),
            KeyDef("ST", "8", listOf('s', 't'), KeyType.LETTER, code = '8', numLabel = "8", longPressChar = '8'),
            KeyDef("UV", "9", listOf('u', 'v'), KeyType.LETTER, code = '9', numLabel = "9", longPressChar = '9'),
            KeyDef("SHIFT", null, emptyList(), KeyType.SHIFT)
        ),
        listOf(
            KeyDef("WX", "123", listOf('w', 'x'), KeyType.LETTER, code = '+', numLabel = ",", longPressOpensNumPage = true, numHint = "-", numLongPressChar = '-'),
            KeyDef("SPACE", "0", emptyList(), KeyType.SPACE, colSpan = 2, numLabel = "0", longPressChar = '0'),
            KeyDef("YZ", "/", listOf('y', 'z'), KeyType.LETTER, code = '/', numLabel = ".", longPressChar = '/'),
            KeyDef("", null, emptyList(), KeyType.ENTER)
        )
    )

    val ALL_KEYS: List<KeyDef> = ROWS.flatten()

    /** Engine key code → multi-tap characters, e.g. '2' → "ab". */
    val codeToChars: Map<Char, String> = ALL_KEYS
        .filter { it.code != ' ' }
        .associate { it.code to it.chars.joinToString("") }

    /** The punctuation-cycle key's engine code (currently '@'), used to distinguish it from letter keys. */
    val punctuationCode: Char = ALL_KEYS.first { it.type == KeyType.SYMBOL_CYCLE }.code
}
