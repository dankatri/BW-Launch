package com.bwlaunch.launcher

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.bwlaunch.launcher.model.DarkModeSchedule
import com.bwlaunch.launcher.model.DisplayMode
import com.bwlaunch.launcher.model.FontType
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * Settings activity for configuring the launcher.
 * All preferences are optimized for e-ink with minimal visual updates.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme - dark mode disabled, always use light
        val prefs = PreferencesManager(this)
        setTheme(R.style.Theme_BWLaunch)
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.settings_title)

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        
        private lateinit var prefs: PreferencesManager
        private var fusedLocationClient: FusedLocationProviderClient? = null

        private val locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            when {
                permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                    requestLocation()
                }
                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                    requestLocation()
                }
                else -> {
                    Toast.makeText(requireContext(), R.string.location_permission_denied, Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            prefs = PreferencesManager(requireContext())
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
            
            val context = preferenceManager.context
            val screen = preferenceManager.createPreferenceScreen(context)

            // ==================== DISPLAY OPTIONS ====================
            val displayCategory = PreferenceCategory(context).apply {
                key = "category_display"
                title = getString(R.string.pref_category_display)
            }
            screen.addPreference(displayCategory)

            // Display mode (Radio-like selection via ListPreference)
            val displayModePref = ListPreference(context).apply {
                key = "display_mode"
                title = getString(R.string.pref_display_mode_title)
                dialogTitle = getString(R.string.pref_display_mode_title)
                entries = resources.getStringArray(R.array.display_mode_entries)
                entryValues = resources.getStringArray(R.array.display_mode_values)
                value = prefs.displayMode.key
                summary = entries[entryValues.indexOf(value)]
                setOnPreferenceChangeListener { pref, newValue ->
                    prefs.displayMode = DisplayMode.fromKey(newValue as String)
                    pref.summary = entries[entryValues.indexOf(newValue)]
                    true
                }
            }
            displayCategory.addPreference(displayModePref)

            // Font selection
            val fontTypePref = ListPreference(context).apply {
                key = "font_type"
                title = getString(R.string.pref_font_type_title)
                dialogTitle = getString(R.string.pref_font_type_title)
                entries = resources.getStringArray(R.array.font_type_entries)
                entryValues = resources.getStringArray(R.array.font_type_values)
                value = prefs.fontType.key
                summary = entries[entryValues.indexOf(value)]
                setOnPreferenceChangeListener { pref, newValue ->
                    prefs.fontType = FontType.fromKey(newValue as String)
                    pref.summary = entries[entryValues.indexOf(newValue)]
                    true
                }
            }
            displayCategory.addPreference(fontTypePref)

            // Text size slider
            val textSizePref = SeekBarPreference(context).apply {
                key = "text_size"
                title = getString(R.string.pref_text_size_title)
                summary = getString(R.string.pref_text_size_summary, prefs.textSize)
                min = PreferencesManager.MIN_TEXT_SIZE
                max = PreferencesManager.MAX_TEXT_SIZE
                value = prefs.textSize
                showSeekBarValue = true
                setOnPreferenceChangeListener { pref, newValue ->
                    val size = newValue as Int
                    prefs.textSize = size
                    pref.summary = getString(R.string.pref_text_size_summary, size)
                    true
                }
            }
            displayCategory.addPreference(textSizePref)

            // ==================== APP SELECTION & ORDERING ====================
            val favoritesCategory = PreferenceCategory(context).apply {
                key = "category_favorites"
                title = getString(R.string.pref_category_favorites)
            }
            screen.addPreference(favoritesCategory)

            // Select and order favorites
            val selectFavoritesPref = Preference(context).apply {
                key = "select_favorites"
                title = getString(R.string.pref_select_favorites_title)
                summary = getString(R.string.pref_select_favorites_summary)
                setOnPreferenceClickListener {
                    val intent = Intent(requireContext(), AppPickerActivity::class.java)
                    startActivity(intent)
                    true
                }
            }
            favoritesCategory.addPreference(selectFavoritesPref)

            // ==================== WEATHER WIDGET ====================
            val weatherCategory = PreferenceCategory(context).apply {
                key = "category_weather"
                title = getString(R.string.pref_category_weather)
            }
            screen.addPreference(weatherCategory)

            // Weather widget toggle
            val weatherEnabledPref = SwitchPreferenceCompat(context).apply {
                key = "weather_enabled"
                title = getString(R.string.pref_weather_enabled_title)
                summary = getString(R.string.pref_weather_enabled_summary)
                isChecked = prefs.weatherEnabled
                setOnPreferenceChangeListener { _, newValue ->
                    val enabled = newValue as Boolean
                    prefs.weatherEnabled = enabled
                    // Request location permission when enabling weather
                    if (enabled && !prefs.hasLocation) {
                        requestLocationPermission()
                    }
                    true
                }
            }
            weatherCategory.addPreference(weatherEnabledPref)

            // Temperature unit selector
            val temperatureUnitPref = ListPreference(context).apply {
                key = "temperature_unit"
                title = getString(R.string.pref_temperature_unit_title)
                dialogTitle = getString(R.string.pref_temperature_unit_title)
                entries = resources.getStringArray(R.array.temperature_unit_entries)
                entryValues = resources.getStringArray(R.array.temperature_unit_values)
                value = prefs.temperatureUnit
                summary = entries[entryValues.indexOf(value)]
                setOnPreferenceChangeListener { pref, newValue ->
                    prefs.temperatureUnit = newValue as String
                    pref.summary = entries[entryValues.indexOf(newValue)]
                    // Clear weather cache when unit changes so it refreshes
                    prefs.weatherCacheTime = 0L
                    true
                }
            }
            weatherCategory.addPreference(temperatureUnitPref)

            // ==================== DARK MODE SCHEDULING ====================
            // Dark mode is temporarily disabled - uncomment this section to re-enable
            /*
            val darkModeCategory = PreferenceCategory(context).apply {
                key = "category_dark_mode"
                title = getString(R.string.pref_category_dark_mode)
            }
            screen.addPreference(darkModeCategory)

            // Dark mode schedule type (Radio buttons via ListPreference)
            val darkModeSchedulePref = ListPreference(context).apply {
                key = "dark_mode_schedule"
                title = getString(R.string.pref_dark_mode_schedule_title)
                dialogTitle = getString(R.string.pref_dark_mode_schedule_title)
                entries = resources.getStringArray(R.array.dark_mode_schedule_entries)
                entryValues = resources.getStringArray(R.array.dark_mode_schedule_values)
                value = prefs.darkModeSchedule.key
                summary = entries[entryValues.indexOf(value)]
                setOnPreferenceChangeListener { pref, newValue ->
                    val schedule = DarkModeSchedule.fromKey(newValue as String)
                    prefs.darkModeSchedule = schedule
                    pref.summary = entries[entryValues.indexOf(newValue)]
                    updateDarkModePrefsVisibility(schedule)
                    
                    // Request location if sunrise/sunset selected
                    if (schedule == DarkModeSchedule.SUNRISE_SUNSET && !prefs.hasLocation) {
                        requestLocationPermission()
                    }
                    true
                }
            }
            darkModeCategory.addPreference(darkModeSchedulePref)

            // Manual dark mode toggle
            val darkModePref = SwitchPreferenceCompat(context).apply {
                key = "dark_mode"
                title = getString(R.string.pref_dark_mode_title)
                summary = getString(R.string.pref_dark_mode_summary)
                isChecked = prefs.darkMode
                isVisible = prefs.darkModeSchedule == DarkModeSchedule.MANUAL
                setOnPreferenceChangeListener { _, newValue ->
                    prefs.darkMode = newValue as Boolean
                    requireActivity().recreate()
                    true
                }
            }
            darkModeCategory.addPreference(darkModePref)

            // Time-based: Start time
            val darkStartTimePref = Preference(context).apply {
                key = "dark_start_time"
                title = getString(R.string.pref_dark_start_time_title)
                summary = formatTime(prefs.darkStartHour, prefs.darkStartMinute)
                isVisible = prefs.darkModeSchedule == DarkModeSchedule.TIME_BASED
                setOnPreferenceClickListener {
                    showTimePicker(prefs.darkStartHour, prefs.darkStartMinute) { hour, minute ->
                        prefs.darkStartHour = hour
                        prefs.darkStartMinute = minute
                        it.summary = formatTime(hour, minute)
                    }
                    true
                }
            }
            darkModeCategory.addPreference(darkStartTimePref)

            // Time-based: End time
            val darkEndTimePref = Preference(context).apply {
                key = "dark_end_time"
                title = getString(R.string.pref_dark_end_time_title)
                summary = formatTime(prefs.darkEndHour, prefs.darkEndMinute)
                isVisible = prefs.darkModeSchedule == DarkModeSchedule.TIME_BASED
                setOnPreferenceClickListener {
                    showTimePicker(prefs.darkEndHour, prefs.darkEndMinute) { hour, minute ->
                        prefs.darkEndHour = hour
                        prefs.darkEndMinute = minute
                        it.summary = formatTime(hour, minute)
                    }
                    true
                }
            }
            darkModeCategory.addPreference(darkEndTimePref)

            // Sunrise/Sunset: Location status
            val locationPref = Preference(context).apply {
                key = "location_status"
                title = getString(R.string.pref_location_title)
                summary = if (prefs.hasLocation) {
                    val sunCalc = SunCalculator(prefs.locationLatitude, prefs.locationLongitude)
                    getString(
                        R.string.pref_location_summary_set,
                        sunCalc.formatTime(sunCalc.getSunriseMinutes()),
                        sunCalc.formatTime(sunCalc.getSunsetMinutes())
                    )
                } else {
                    getString(R.string.pref_location_summary_not_set)
                }
                isVisible = prefs.darkModeSchedule == DarkModeSchedule.SUNRISE_SUNSET
                setOnPreferenceClickListener {
                    requestLocationPermission()
                    true
                }
            }
            darkModeCategory.addPreference(locationPref)
            */ // End of dark mode section - temporarily disabled

            preferenceScreen = screen
        }

        /* Dark mode helper functions - temporarily disabled
        private fun updateDarkModePrefsVisibility(schedule: DarkModeSchedule) {
            findPreference<SwitchPreferenceCompat>("dark_mode")?.isVisible = 
                schedule == DarkModeSchedule.MANUAL
            findPreference<Preference>("dark_start_time")?.isVisible = 
                schedule == DarkModeSchedule.TIME_BASED
            findPreference<Preference>("dark_end_time")?.isVisible = 
                schedule == DarkModeSchedule.TIME_BASED
            findPreference<Preference>("location_status")?.isVisible = 
                schedule == DarkModeSchedule.SUNRISE_SUNSET
        }
        */

        private fun formatTime(hour: Int, minute: Int): String {
            return String.format("%02d:%02d", hour, minute)
        }

        private fun showTimePicker(currentHour: Int, currentMinute: Int, onTimeSet: (Int, Int) -> Unit) {
            TimePickerDialog(
                requireContext(),
                { _, hourOfDay, minute ->
                    onTimeSet(hourOfDay, minute)
                },
                currentHour,
                currentMinute,
                true
            ).show()
        }

        private fun requestLocationPermission() {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED -> {
                    requestLocation()
                }
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED -> {
                    requestLocation()
                }
                else -> {
                    locationPermissionRequest.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                }
            }
        }

        private fun requestLocation() {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            fusedLocationClient?.getCurrentLocation(Priority.PRIORITY_LOW_POWER, null)
                ?.addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        prefs.locationLatitude = location.latitude
                        prefs.locationLongitude = location.longitude
                        
                        val sunCalc = SunCalculator(location.latitude, location.longitude)
                        val summary = getString(
                            R.string.pref_location_summary_set,
                            sunCalc.formatTime(sunCalc.getSunriseMinutes()),
                            sunCalc.formatTime(sunCalc.getSunsetMinutes())
                        )
                        findPreference<Preference>("location_status")?.summary = summary
                        
                        Toast.makeText(
                            requireContext(),
                            R.string.location_updated,
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            R.string.location_not_available,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                ?.addOnFailureListener {
                    Toast.makeText(
                        requireContext(),
                        R.string.location_error,
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }
}
