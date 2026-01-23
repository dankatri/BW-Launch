package com.bwlaunch.launcher

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.bwlaunch.launcher.model.DarkModeSchedule
import com.bwlaunch.launcher.model.DisplayMode
import com.bwlaunch.launcher.model.FontType

/**
 * Manages app preferences using SharedPreferences.
 * Optimized for minimal writes to reduce e-ink refreshes.
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "bw_launch_prefs"
        private const val KEY_DISPLAY_MODE = "display_mode"
        private const val KEY_FONT_TYPE = "font_type"
        private const val KEY_FAVORITE_COUNT = "favorite_count"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_DARK_MODE_SCHEDULE = "dark_mode_schedule"
        private const val KEY_DARK_START_HOUR = "dark_start_hour"
        private const val KEY_DARK_START_MINUTE = "dark_start_minute"
        private const val KEY_DARK_END_HOUR = "dark_end_hour"
        private const val KEY_DARK_END_MINUTE = "dark_end_minute"
        private const val KEY_LOCATION_LATITUDE = "location_latitude"
        private const val KEY_LOCATION_LONGITUDE = "location_longitude"
        private const val KEY_CUSTOM_LABEL_PREFIX = "custom_label_"
        private const val KEY_TEXT_SIZE = "text_size"
        private const val KEY_FIRST_LAUNCH_DONE = "first_launch_done"

        const val DEFAULT_FAVORITE_COUNT = 5
        const val MIN_FAVORITE_COUNT = 3
        const val MAX_FAVORITE_COUNT = 10
        
        const val DEFAULT_TEXT_SIZE = 18
        const val MIN_TEXT_SIZE = 12
        const val MAX_TEXT_SIZE = 48
    }

    var displayMode: DisplayMode
        get() = DisplayMode.fromKey(prefs.getString(KEY_DISPLAY_MODE, DisplayMode.TEXT.key) ?: DisplayMode.TEXT.key)
        set(value) = prefs.edit { putString(KEY_DISPLAY_MODE, value.key) }

    var fontType: FontType
        get() = FontType.fromKey(prefs.getString(KEY_FONT_TYPE, FontType.SANS_SERIF.key) ?: FontType.SANS_SERIF.key)
        set(value) = prefs.edit { putString(KEY_FONT_TYPE, value.key) }

    var textSize: Int
        get() = prefs.getInt(KEY_TEXT_SIZE, DEFAULT_TEXT_SIZE)
        set(value) = prefs.edit { putInt(KEY_TEXT_SIZE, value.coerceIn(MIN_TEXT_SIZE, MAX_TEXT_SIZE)) }

    var isFirstLaunchDone: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH_DONE, false)
        set(value) = prefs.edit { putBoolean(KEY_FIRST_LAUNCH_DONE, value) }

    var favoriteCount: Int
        get() = prefs.getInt(KEY_FAVORITE_COUNT, DEFAULT_FAVORITE_COUNT)
        set(value) = prefs.edit { putInt(KEY_FAVORITE_COUNT, value.coerceIn(MIN_FAVORITE_COUNT, MAX_FAVORITE_COUNT)) }

    var favorites: List<String>
        get() {
            val favString = prefs.getString(KEY_FAVORITES, "") ?: ""
            return if (favString.isEmpty()) emptyList() else favString.split("|")
        }
        set(value) = prefs.edit { putString(KEY_FAVORITES, value.joinToString("|")) }

    var darkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, false)
        set(value) = prefs.edit { putBoolean(KEY_DARK_MODE, value) }

    var darkModeSchedule: DarkModeSchedule
        get() = DarkModeSchedule.fromKey(prefs.getString(KEY_DARK_MODE_SCHEDULE, DarkModeSchedule.MANUAL.key) ?: DarkModeSchedule.MANUAL.key)
        set(value) = prefs.edit { putString(KEY_DARK_MODE_SCHEDULE, value.key) }

    var darkStartHour: Int
        get() = prefs.getInt(KEY_DARK_START_HOUR, 20)
        set(value) = prefs.edit { putInt(KEY_DARK_START_HOUR, value) }

    var darkStartMinute: Int
        get() = prefs.getInt(KEY_DARK_START_MINUTE, 0)
        set(value) = prefs.edit { putInt(KEY_DARK_START_MINUTE, value) }

    var darkEndHour: Int
        get() = prefs.getInt(KEY_DARK_END_HOUR, 7)
        set(value) = prefs.edit { putInt(KEY_DARK_END_HOUR, value) }

    var darkEndMinute: Int
        get() = prefs.getInt(KEY_DARK_END_MINUTE, 0)
        set(value) = prefs.edit { putInt(KEY_DARK_END_MINUTE, value) }

    var locationLatitude: Double
        get() = prefs.getFloat(KEY_LOCATION_LATITUDE, 0f).toDouble()
        set(value) = prefs.edit { putFloat(KEY_LOCATION_LATITUDE, value.toFloat()) }

    var locationLongitude: Double
        get() = prefs.getFloat(KEY_LOCATION_LONGITUDE, 0f).toDouble()
        set(value) = prefs.edit { putFloat(KEY_LOCATION_LONGITUDE, value.toFloat()) }

    val hasLocation: Boolean
        get() = locationLatitude != 0.0 || locationLongitude != 0.0

    /**
     * Get custom label for an app using the key format: custom_label_[package_name]
     */
    fun getCustomLabel(packageName: String): String? {
        return prefs.getString("$KEY_CUSTOM_LABEL_PREFIX$packageName", null)
    }

    /**
     * Set custom label for an app using the key format: custom_label_[package_name]
     */
    fun setCustomLabel(packageName: String, label: String?) {
        prefs.edit {
            if (label.isNullOrEmpty()) {
                remove("$KEY_CUSTOM_LABEL_PREFIX$packageName")
            } else {
                putString("$KEY_CUSTOM_LABEL_PREFIX$packageName", label)
            }
        }
    }

    fun addFavorite(packageName: String) {
        val current = favorites.toMutableList()
        if (!current.contains(packageName) && current.size < favoriteCount) {
            current.add(packageName)
            favorites = current
        }
    }

    fun removeFavorite(packageName: String) {
        favorites = favorites.filter { it != packageName }
    }

    fun isFavorite(packageName: String): Boolean = favorites.contains(packageName)

    fun moveFavorite(fromIndex: Int, toIndex: Int) {
        val current = favorites.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            favorites = current
        }
    }

    fun setFavoritesOrdered(orderedPackages: List<String>) {
        favorites = orderedPackages.take(favoriteCount)
    }

    /**
     * Check if dark mode should be active based on schedule.
     */
    fun shouldUseDarkMode(): Boolean {
        return when (darkModeSchedule) {
            DarkModeSchedule.MANUAL -> darkMode
            DarkModeSchedule.TIME_BASED -> isInDarkTimeRange()
            DarkModeSchedule.SUNRISE_SUNSET -> isAfterSunset()
        }
    }

    private fun isInDarkTimeRange(): Boolean {
        val now = java.util.Calendar.getInstance()
        val currentHour = now.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(java.util.Calendar.MINUTE)
        val currentTime = currentHour * 60 + currentMinute

        val startTime = darkStartHour * 60 + darkStartMinute
        val endTime = darkEndHour * 60 + darkEndMinute

        return if (startTime <= endTime) {
            // Normal range (e.g., 08:00 to 18:00 means dark outside this range)
            currentTime < startTime || currentTime >= endTime
        } else {
            // Overnight range (e.g., 20:00 to 07:00)
            currentTime >= startTime || currentTime < endTime
        }
    }

    private fun isAfterSunset(): Boolean {
        if (!hasLocation) return darkMode // Fallback to manual if no location

        val sunCalculator = SunCalculator(locationLatitude, locationLongitude)
        val now = java.util.Calendar.getInstance()
        val currentMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)

        val sunrise = sunCalculator.getSunriseMinutes()
        val sunset = sunCalculator.getSunsetMinutes()

        // Dark mode is active before sunrise or after sunset
        return currentMinutes < sunrise || currentMinutes >= sunset
    }
}
