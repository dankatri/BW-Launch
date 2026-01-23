package com.bwlaunch.launcher.model

/**
 * Display modes for the home screen.
 */
enum class DisplayMode(val key: String) {
    TEXT("text"),
    ICONS_TEXT("icons_text"),
    ICONS("icons");

    companion object {
        fun fromKey(key: String): DisplayMode {
            return entries.find { it.key == key } ?: TEXT
        }
    }
}
