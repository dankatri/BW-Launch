package com.bwlaunch.launcher.model

/**
 * Font types for the launcher display.
 */
enum class FontType(val key: String) {
    SANS_SERIF("sans_serif"),
    SERIF("serif");

    companion object {
        fun fromKey(key: String): FontType {
            return entries.find { it.key == key } ?: SANS_SERIF
        }
    }
}
