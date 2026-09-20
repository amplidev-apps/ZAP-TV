package com.zap.tv.util

import android.view.KeyEvent

enum class RemoteAction {
    DIGIT_0, DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4, DIGIT_5, DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9,
    CHANNEL_UP, CHANNEL_DOWN,
    DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT,
    VOLUME_UP, VOLUME_DOWN,
    OK, BACK, MENU, PLAY_PAUSE,
    RED, GREEN, YELLOW, BLUE,
    INFO, GUIDE, LAST_CHANNEL
}

object RemoteKeyHandler {
    fun map(event: KeyEvent): RemoteAction? = when (event.keyCode) {
        KeyEvent.KEYCODE_0 -> RemoteAction.DIGIT_0
        KeyEvent.KEYCODE_1 -> RemoteAction.DIGIT_1
        KeyEvent.KEYCODE_2 -> RemoteAction.DIGIT_2
        KeyEvent.KEYCODE_3 -> RemoteAction.DIGIT_3
        KeyEvent.KEYCODE_4 -> RemoteAction.DIGIT_4
        KeyEvent.KEYCODE_5 -> RemoteAction.DIGIT_5
        KeyEvent.KEYCODE_6 -> RemoteAction.DIGIT_6
        KeyEvent.KEYCODE_7 -> RemoteAction.DIGIT_7
        KeyEvent.KEYCODE_8 -> RemoteAction.DIGIT_8
        KeyEvent.KEYCODE_9 -> RemoteAction.DIGIT_9
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> RemoteAction.CHANNEL_UP
        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> RemoteAction.CHANNEL_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> RemoteAction.VOLUME_DOWN
        KeyEvent.KEYCODE_DPAD_RIGHT -> RemoteAction.VOLUME_UP
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> RemoteAction.OK
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> RemoteAction.BACK
        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS -> RemoteAction.MENU
        KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_F1 -> RemoteAction.RED
        KeyEvent.KEYCODE_PROG_GREEN, KeyEvent.KEYCODE_F2 -> RemoteAction.GREEN
        KeyEvent.KEYCODE_PROG_YELLOW, KeyEvent.KEYCODE_F3 -> RemoteAction.YELLOW
        KeyEvent.KEYCODE_PROG_BLUE, KeyEvent.KEYCODE_F4 -> RemoteAction.BLUE
        KeyEvent.KEYCODE_INFO -> RemoteAction.INFO
        KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_TV_INPUT, KeyEvent.KEYCODE_WINDOW -> RemoteAction.GUIDE
        KeyEvent.KEYCODE_LAST_CHANNEL -> RemoteAction.LAST_CHANNEL
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> RemoteAction.PLAY_PAUSE
        else -> null
    }
    fun digit(action: RemoteAction): Int? = when (action) {
        RemoteAction.DIGIT_0 -> 0; RemoteAction.DIGIT_1 -> 1; RemoteAction.DIGIT_2 -> 2; RemoteAction.DIGIT_3 -> 3
        RemoteAction.DIGIT_4 -> 4; RemoteAction.DIGIT_5 -> 5; RemoteAction.DIGIT_6 -> 6; RemoteAction.DIGIT_7 -> 7
        RemoteAction.DIGIT_8 -> 8; RemoteAction.DIGIT_9 -> 9; else -> null
    }
}