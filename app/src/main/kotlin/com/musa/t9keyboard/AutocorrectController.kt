package com.musa.t9keyboard

class AutocorrectController(
    private val engine: AutocorrectEngine
) {

    var lastAutocorrection: AutocorrectRecord? = null

    fun getCorrection(typedWord: String, prediction: String, sensitivity: Int): String? {
        if (!AospDictionary.isBKTreeReady) return null
        if (typedWord.isBlank()) return null

        return engine.getCorrection(prediction, sensitivity)
    }

    fun clear() {
        lastAutocorrection = null
    }

    fun saveRecord(original: String, correction: String, trigger: String) {
        lastAutocorrection = AutocorrectRecord(original, correction, trigger)
    }
}
