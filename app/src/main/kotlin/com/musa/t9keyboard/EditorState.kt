package com.musa.t9keyboard

class EditorState {
    var composingText = StringBuilder()
    var xt9DigitSequence = StringBuilder()
    var xt9RawSequence = StringBuilder()
    var currentXt9Predictions = listOf<String>()
    var currentWordConstraints = mutableListOf<String>()
    var lastCommittedWord: String? = null
    var lastDigit = ' '
    var lastTapTime = 0L

    fun reset() {
        composingText.setLength(0)
        xt9DigitSequence.setLength(0)
        xt9RawSequence.setLength(0)
        currentXt9Predictions = emptyList()
        currentWordConstraints.clear()
        lastCommittedWord = null
        lastDigit = ' '
        lastTapTime = 0L
    }
}
