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
 * @param hint      Muted top-right corner hint ("2", "123", "🙂", ...)
 * @param chars     Multi-tap cycle characters (letters/punctuation), empty for action keys
 * @param type      Key behavior class
 * @param colSpan   Grid columns occupied (SPACE spans 2)
 * @param code      Engine key code used in T9 digit sequences; ' ' for action keys.
 *                  Codes must be unique per LETTER/SYMBOL_CYCLE key and must never be
 *                  a lowercase letter (letters denote exact-match constraints).
 * @param numLabel  Label shown in number mode (null keeps the primary label)
 * @param longPressChar  Character committed on long-press (the corner hint symbol), if any
 * @param longPressOpensNumPage  WX long-press ("123" hint) toggles the number page
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
    val longPressOpensNumPage: Boolean = false
)

/**
 * Single source of truth for the 5-column × 4-row, 2-letters-per-key layout.
 *
 * Row 1: .,!? · AB · CD · EF · ⌫
 * Row 2: GH · IJ · KL · MN · SYM
 * Row 3: OP · QR · ST · UV · SHIFT
 * Row 4: WX · SPACE(×2) · YZ · ⏎
 */
object KeyboardLayout {

    val ROWS: List<List<KeyDef>> = listOf(
        listOf(
            KeyDef(".,!?", "1", listOf('.', ',', '!', '?'), KeyType.SYMBOL_CYCLE, code = '1', numLabel = "1", longPressChar = '1'),
            KeyDef("AB", "2", listOf('a', 'b'), KeyType.LETTER, code = '2', numLabel = "2", longPressChar = '2'),
            KeyDef("CD", "3", listOf('c', 'd'), KeyType.LETTER, code = '3', numLabel = "3", longPressChar = '3'),
            KeyDef("EF", "4", listOf('e', 'f'), KeyType.LETTER, code = '4', numLabel = "4", longPressChar = '4'),
            KeyDef("", null, emptyList(), KeyType.BACKSPACE)
        ),
        listOf(
            KeyDef("GH", "5", listOf('g', 'h'), KeyType.LETTER, code = '5', numLabel = "5", longPressChar = '5'),
            KeyDef("IJ", "6", listOf('i', 'j'), KeyType.LETTER, code = '6', numLabel = "6", longPressChar = '6'),
            KeyDef("KL", "7", listOf('k', 'l'), KeyType.LETTER, code = '7', numLabel = "7", longPressChar = '7'),
            KeyDef("MN", "8", listOf('m', 'n'), KeyType.LETTER, code = '8', numLabel = "8", longPressChar = '8'),
            KeyDef("SYM", null, emptyList(), KeyType.SYM_PAGE)
        ),
        listOf(
            KeyDef("OP", "9", listOf('o', 'p'), KeyType.LETTER, code = '9', numLabel = "9", longPressChar = '9'),
            KeyDef("QR", "0", listOf('q', 'r'), KeyType.LETTER, code = '0', numLabel = "0", longPressChar = '0'),
            KeyDef("ST", "*", listOf('s', 't'), KeyType.LETTER, code = '*', numLabel = "*", longPressChar = '*'),
            KeyDef("UV", "#", listOf('u', 'v'), KeyType.LETTER, code = '#', numLabel = "#", longPressChar = '#'),
            KeyDef("SHIFT", null, emptyList(), KeyType.SHIFT)
        ),
        listOf(
            KeyDef("WX", "123", listOf('w', 'x'), KeyType.LETTER, code = '+', numLabel = ",", longPressOpensNumPage = true),
            KeyDef("SPACE", "0", emptyList(), KeyType.SPACE, colSpan = 2, numLabel = "0", longPressChar = '0'),
            KeyDef("YZ", "/", listOf('y', 'z'), KeyType.LETTER, code = '/', numLabel = ".", longPressChar = '/'),
            KeyDef("", "🙂", emptyList(), KeyType.ENTER)
        )
    )

    val ALL_KEYS: List<KeyDef> = ROWS.flatten()

    /** Engine key code → multi-tap characters, e.g. '2' → "ab". */
    val codeToChars: Map<Char, String> = ALL_KEYS
        .filter { it.code != ' ' }
        .associate { it.code to it.chars.joinToString("") }
}
