package com.bwlaunch.launcher.model

/**
 * Dark mode scheduling options.
 */
enum class DarkModeSchedule(val key: String) {
    MANUAL("manual"),
    TIME_BASED("time_based"),
    SUNRISE_SUNSET("sunrise_sunset");

    companion object {
        fun fromKey(key: String): DarkModeSchedule {
            return entries.find { it.key == key } ?: MANUAL
        }
    }
}
