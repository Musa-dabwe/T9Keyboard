package com.musa.t9keyboard

enum class ShiftState {
    OFF, ONE_SHOT, CAPS_LOCK
}

class ShiftStateManager {
    var currentState: ShiftState = ShiftState.OFF
        private set

    var wasShiftSetManually: Boolean = false

    fun toggle() {
        wasShiftSetManually = true
        currentState = when (currentState) {
            ShiftState.OFF -> ShiftState.ONE_SHOT
            ShiftState.ONE_SHOT -> ShiftState.OFF
            ShiftState.CAPS_LOCK -> ShiftState.OFF
        }
    }

    fun onDoubleTap() {
        wasShiftSetManually = true
        currentState = ShiftState.CAPS_LOCK
    }

    fun consumeShift() {
        if (currentState == ShiftState.ONE_SHOT) {
            currentState = ShiftState.OFF
        }
        wasShiftSetManually = false
    }

    fun reset() {
        currentState = ShiftState.OFF
        wasShiftSetManually = false
    }

    fun setAutoShift(state: ShiftState) {
        if (!wasShiftSetManually && currentState == ShiftState.OFF) {
            currentState = state
        }
    }
}
