package com.musa.t9keyboard

enum class ShiftState {
    OFF, ON, CAPS_LOCK
}

class ShiftStateManager {
    var currentState: ShiftState = ShiftState.OFF
        private set

    fun toggle() {
        currentState = when (currentState) {
            ShiftState.OFF -> ShiftState.ON
            ShiftState.ON -> ShiftState.OFF
            ShiftState.CAPS_LOCK -> ShiftState.OFF
        }
    }

    fun onDoubleTap() {
        currentState = ShiftState.CAPS_LOCK
    }

    fun consumeShift() {
        if (currentState == ShiftState.ON) {
            currentState = ShiftState.OFF
        }
    }

    fun reset() {
        currentState = ShiftState.OFF
    }
}
