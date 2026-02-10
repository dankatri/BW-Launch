package com.bwlaunch.launcher

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bwlaunch.launcher.adapter.AllAppsAdapter
import com.bwlaunch.launcher.adapter.FavoritesAdapter
import com.bwlaunch.launcher.databinding.ActivityMainBinding
import com.bwlaunch.launcher.model.AppInfo
import com.bwlaunch.launcher.model.DarkModeSchedule
import com.bwlaunch.launcher.model.DisplayMode
import com.bwlaunch.launcher.model.FontType
import com.bwlaunch.launcher.util.Debouncer
import com.bwlaunch.launcher.util.EInkUtils
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main launcher activity optimized for e-ink displays.
 * 
 * Features:
 * - Minimal animations to reduce screen refreshes
 * - Configurable display modes (text, icons+text, icons)
 * - Long-press gestures for settings and label editing
 * - All Apps overlay drawer
 * - E-ink optimized rendering with debounced updates
 * 
 * Gesture Handling:
 * - Long-press on app item: Opens inline edit label dialog
 * - Long-press on empty space: Opens Settings
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var appLoader: AppLoader
    private lateinit var favoritesAdapter: FavoritesAdapter
    private lateinit var allAppsAdapter: AllAppsAdapter
    private val weatherService = WeatherService()

    // Debouncer for e-ink optimized updates
    private val uiDebouncer = Debouncer()
    
    // Gesture detector for empty space long-press
    private lateinit var emptySpaceGestureDetector: GestureDetector

    private var isAllAppsVisible = false
    
    // Currently active edit dialog (for dismissal)
    private var activeEditDialog: AlertDialog? = null
    
    // Receiver for dark mode changes from background service
    private val darkModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DarkModeService.ACTION_DARK_MODE_CHANGED) {
                checkThemeChange()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before super.onCreate
        prefs = PreferencesManager(this)
        // Dark mode disabled for now - always use light theme
        setTheme(R.style.Theme_BWLaunch)
        
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Optimize root layout for e-ink
        EInkUtils.optimizeForEInk(binding.root)

        appLoader = AppLoader(this)
        
        setupEmptySpaceGestureDetector()
        setupFavoritesList()
        setupAllAppsDrawer()
        setupClickListeners()
        // Dark mode service disabled
        // startDarkModeServiceIfNeeded()
        // registerDarkModeReceiver()
        
        // Check if this is first launch
        if (!prefs.isFirstLaunchDone) {
            showSetDefaultLauncherPrompt()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        uiDebouncer.cancel()
        activeEditDialog?.dismiss()
        activeEditDialog = null
        // Dark mode receiver disabled
        // try {
        //     unregisterReceiver(darkModeReceiver)
        // } catch (e: Exception) {
        //     // Receiver not registered
        // }
    }

    override fun onResume() {
        super.onResume()
        // Dark mode theme check disabled
        // checkThemeChange()
        // Reload favorites in case preferences changed (debounced for e-ink)
        uiDebouncer.debounce {
            loadFavorites()
        }
        // Update weather widget
        updateWeatherWidget()
    }
    
    private fun registerDarkModeReceiver() {
        val filter = IntentFilter(DarkModeService.ACTION_DARK_MODE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(darkModeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(darkModeReceiver, filter)
        }
    }
    
    private fun startDarkModeServiceIfNeeded() {
        val schedule = prefs.darkModeSchedule
        if (schedule == DarkModeSchedule.TIME_BASED || schedule == DarkModeSchedule.SUNRISE_SUNSET) {
            val serviceIntent = Intent(this, DarkModeService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }

    private fun applyTheme() {
        val useDarkMode = prefs.shouldUseDarkMode()
        setTheme(if (useDarkMode) R.style.Theme_BWLaunch_Dark else R.style.Theme_BWLaunch)
    }

    private fun checkThemeChange() {
        val shouldBeDark = prefs.shouldUseDarkMode()
        val currentlyDark = resources.configuration.uiMode and 
            android.content.res.Configuration.UI_MODE_NIGHT_MASK == 
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        // Only recreate if theme actually needs to change
        if (shouldBeDark != currentlyDark) {
            recreate()
        }
    }
    
    /**
     * Sets up GestureDetector to detect long-press on empty space in the RecyclerView.
     * Empty space is any area not occupied by an app item.
     */
    private fun setupEmptySpaceGestureDetector() {
        emptySpaceGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                // Check if the touch is on an app item or empty space
                val recyclerView = binding.favoritesRecyclerView
                val childView = recyclerView.findChildViewUnder(e.x, e.y)
                
                if (childView == null) {
                    // Long-press on empty space - open settings
                    recyclerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    openSettings()
                }
                // If childView != null, the app item's own long-press handler will handle it
            }
        })
    }

    private fun setupFavoritesList() {
        favoritesAdapter = FavoritesAdapter(
            displayMode = prefs.displayMode,
            fontType = prefs.fontType,
            textSizeSp = prefs.textSize,
            onAppClick = { app -> launchApp(app) },
            onAppLongClick = { app -> 
                binding.favoritesRecyclerView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                showEditLabelDialog(app)
                true 
            }
        )

        binding.favoritesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = favoritesAdapter
            // Disable item animations for e-ink
            itemAnimator = null
            // Set software layer for e-ink optimization
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            
            // Add touch listener for empty space detection
            addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    // Only handle gestures on empty space (where no child is under touch)
                    val childView = rv.findChildViewUnder(e.x, e.y)
                    if (childView == null) {
                        emptySpaceGestureDetector.onTouchEvent(e)
                    }
                    return false // Don't intercept, let items handle their own touches
                }
            })
        }
    }

    private fun setupAllAppsDrawer() {
        allAppsAdapter = AllAppsAdapter { app ->
            launchApp(app)
            hideAllApps()
        }

        binding.allAppsRecyclerView.apply {
            // 3-column grid for larger tap targets on e-ink
            layoutManager = GridLayoutManager(this@MainActivity, 3)
            adapter = allAppsAdapter
            // Disable item animations for e-ink
            itemAnimator = null
            // Set software layer for e-ink optimization
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        }
    }

    private fun setupClickListeners() {
        // All Apps button
        binding.allAppsButton.setOnClickListener {
            showAllApps()
        }

        // Close All Apps
        binding.closeAllAppsButton.setOnClickListener {
            hideAllApps()
        }
        
        // Long-press on home container (empty space) opens settings
        binding.homeContainer.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            openSettings()
            true
        }
        
        // Long-press on empty state text also opens settings
        binding.emptyStateText.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            openSettings()
            true
        }
        
        // Long-press on All Apps button opens settings (alternative access)
        binding.allAppsButton.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            openSettings()
            true
        }
    }
    
    /**
     * Shows a dialog prompting the user to set BW Launch as their default launcher.
     * Only shown on first launch.
     */
    private fun showSetDefaultLauncherPrompt() {
        AlertDialog.Builder(this, R.style.EInkDialogTheme)
            .setTitle(R.string.default_launcher_title)
            .setMessage(R.string.default_launcher_message)
            .setPositiveButton(R.string.set_default) { _, _ ->
                prefs.isFirstLaunchDone = true
                openDefaultLauncherSettings()
            }
            .setNegativeButton(R.string.not_now) { _, _ ->
                prefs.isFirstLaunchDone = true
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * Opens the system UI to let the user choose their default launcher.
     */
    private fun openDefaultLauncherSettings() {
        try {
            // Method 1: Show launcher chooser by simulating a HOME press
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(Intent.createChooser(intent, getString(R.string.default_launcher_title)))
        } catch (e: Exception) {
            // Method 2: Open default apps settings as fallback
            try {
                val intent = Intent(android.provider.Settings.ACTION_HOME_SETTINGS)
                startActivity(intent)
            } catch (e2: Exception) {
                // Last resort: open general settings
                val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
                startActivity(intent)
            }
        }
    }

    private fun loadFavorites() {
        lifecycleScope.launch {
            val favorites = appLoader.getFavoriteApps(prefs)
            favoritesAdapter.setDisplayMode(prefs.displayMode)
            favoritesAdapter.setFontType(prefs.fontType)
            favoritesAdapter.setTextSize(prefs.textSize)
            favoritesAdapter.submitList(favorites)
            
            // Show/hide empty state
            binding.emptyStateText.visibility = if (favorites.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun loadAllApps() {
        lifecycleScope.launch {
            val apps = appLoader.getAllApps()
            allAppsAdapter.submitList(apps)
        }
    }

    /**
     * Updates the weather widget based on preferences.
     * Uses cached weather data if still valid, otherwise fetches fresh data.
     */
    private fun updateWeatherWidget() {
        if (!prefs.weatherEnabled) {
            binding.weatherText.visibility = View.GONE
            return
        }

        // Check if we have location — if not, try to fetch it
        if (!prefs.hasLocation) {
            binding.weatherText.text = "\u2026"
            binding.weatherText.visibility = View.VISIBLE
            fetchLocationThenWeather()
            return
        }

        val useCelsius = prefs.useCelsius

        // Try to use cached weather first
        val cachedWeather = prefs.weatherCache
        val cacheTime = prefs.weatherCacheTime
        val now = System.currentTimeMillis()

        if (cachedWeather != null && (now - cacheTime) < WeatherService.CACHE_DURATION_MS) {
            // Use cached data only if unit matches current preference
            val weatherData = WeatherService.WeatherData.fromCache(cachedWeather)
            if (weatherData != null && weatherData.useCelsius == useCelsius) {
                displayWeather(weatherData)
                return
            }
        }

        // Show loading state while fetching
        if (binding.weatherText.visibility != View.VISIBLE) {
            binding.weatherText.text = "\u2026" // Ellipsis while loading
            binding.weatherText.visibility = View.VISIBLE
        }

        // Fetch fresh weather data
        lifecycleScope.launch {
            val weatherData = weatherService.fetchWeather(
                prefs.locationLatitude,
                prefs.locationLongitude,
                useCelsius
            )

            withContext(Dispatchers.Main) {
                if (weatherData != null) {
                    // Cache the result
                    prefs.weatherCache = weatherData.toCache()
                    prefs.weatherCacheTime = System.currentTimeMillis()
                    displayWeather(weatherData)
                } else {
                    // Show cached data if available (even with different unit), otherwise hide
                    val fallbackCache = prefs.weatherCache
                    if (fallbackCache != null) {
                        val fallbackData = WeatherService.WeatherData.fromCache(fallbackCache)
                        if (fallbackData != null) {
                            displayWeather(fallbackData)
                            return@withContext
                        }
                    }
                    binding.weatherText.text = getString(R.string.weather_unavailable)
                    binding.weatherText.visibility = View.VISIBLE
                }
            }
        }
    }

    /**
     * Displays weather data in the widget.
     */
    private fun displayWeather(weatherData: WeatherService.WeatherData) {
        binding.weatherText.text = weatherData.toDisplayString()
        binding.weatherText.visibility = View.VISIBLE
    }

    /**
     * Attempts to obtain location and then fetches weather.
     * Tries lastLocation first (instant, cached), then getCurrentLocation,
     * then falls back to LocationManager.
     */
    private fun fetchLocationThenWeather() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            binding.weatherText.text = getString(R.string.weather_location_required)
            return
        }

        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(this)

            // Step 1: Try lastLocation — instant, uses cached system location
            fusedClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        Log.d("MainActivity", "Got lastLocation: ${location.latitude}, ${location.longitude}")
                        saveLocationAndFetchWeather(location)
                    } else {
                        Log.d("MainActivity", "lastLocation null, requesting fresh location")
                        // Step 2: Request a fresh fix
                        requestFreshLocation(fusedClient)
                    }
                }
                .addOnFailureListener { e ->
                    Log.w("MainActivity", "lastLocation failed", e)
                    requestFreshLocation(fusedClient)
                }
        } catch (e: Exception) {
            Log.w("MainActivity", "Play Services unavailable, using LocationManager", e)
            fetchLocationFallbackThenWeather()
        }
    }

    private fun requestFreshLocation(fusedClient: com.google.android.gms.location.FusedLocationProviderClient) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            fetchLocationFallbackThenWeather()
            return
        }

        fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    Log.d("MainActivity", "Got fresh location: ${location.latitude}, ${location.longitude}")
                    saveLocationAndFetchWeather(location)
                } else {
                    Log.w("MainActivity", "getCurrentLocation null, trying LocationManager")
                    fetchLocationFallbackThenWeather()
                }
            }
            .addOnFailureListener { e ->
                Log.w("MainActivity", "getCurrentLocation failed, trying LocationManager", e)
                fetchLocationFallbackThenWeather()
            }
    }

    private fun fetchLocationFallbackThenWeather() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            binding.weatherText.text = getString(R.string.weather_location_required)
            return
        }

        try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = locationManager.getProviders(true)
            var bestLocation: Location? = null
            for (provider in providers) {
                val loc = locationManager.getLastKnownLocation(provider)
                if (loc != null && (bestLocation == null || loc.accuracy < bestLocation.accuracy)) {
                    bestLocation = loc
                }
            }

            if (bestLocation != null) {
                saveLocationAndFetchWeather(bestLocation)
            } else {
                binding.weatherText.text = getString(R.string.weather_location_required)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "LocationManager fallback failed", e)
            binding.weatherText.text = getString(R.string.weather_unavailable)
        }
    }

    private fun saveLocationAndFetchWeather(location: Location) {
        prefs.locationLatitude = location.latitude
        prefs.locationLongitude = location.longitude
        // Now that we have location, re-run the full weather update
        updateWeatherWidget()
    }

    private fun showAllApps() {
        if (!isAllAppsVisible) {
            isAllAppsVisible = true
            loadAllApps()
            // Direct visibility change without animation for e-ink
            binding.allAppsOverlay.visibility = View.VISIBLE
        }
    }

    private fun hideAllApps() {
        if (isAllAppsVisible) {
            isAllAppsVisible = false
            // Direct visibility change without animation for e-ink
            binding.allAppsOverlay.visibility = View.GONE
        }
    }

    /**
     * Launches an app with proper error handling.
     * Handles cases where app may have been uninstalled or changed.
     */
    private fun launchApp(app: AppInfo) {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(app.packageName, app.activityName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback: try launch by package
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                } else {
                    // App not found - may have been uninstalled
                    showAppNotFoundError(app)
                }
            } catch (e2: Exception) {
                showAppLaunchError(app)
            }
        }
    }
    
    /**
     * Shows error when an app cannot be found (likely uninstalled).
     * Triggers a refresh to clean up the favorites list.
     */
    private fun showAppNotFoundError(app: AppInfo) {
        android.widget.Toast.makeText(
            this,
            getString(R.string.error_app_not_found),
            android.widget.Toast.LENGTH_SHORT
        ).show()
        
        // Force reload to clean up uninstalled apps
        appLoader.invalidateCache()
        loadFavorites()
    }
    
    /**
     * Shows generic app launch error.
     */
    private fun showAppLaunchError(app: AppInfo) {
        android.widget.Toast.makeText(
            this,
            getString(R.string.error_cannot_launch),
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * Shows an inline edit dialog for changing an app's label.
     * 
     * Features:
     * - Pre-filled with current label
     * - Save button stores custom label to SharedPreferences
     * - Cancel button dismisses without changes
     * - Reset button clears custom label
     * - Immediate UI update without full refresh
     * - E-ink optimized (no animations)
     */
    private fun showEditLabelDialog(app: AppInfo) {
        // Dismiss any existing dialog
        activeEditDialog?.dismiss()
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_label, null)
        val editText = dialogView.findViewById<EditText>(R.id.labelEditText)
        editText.setText(app.displayLabel)
        editText.selectAll()
        
        // Handle keyboard "Done" action
        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveLabel(app, editText.text.toString().trim())
                activeEditDialog?.dismiss()
                activeEditDialog = null
                true
            } else {
                false
            }
        }
        
        // Choose dialog theme based on current dark mode
        val dialogTheme = if (prefs.shouldUseDarkMode()) {
            R.style.EInkDialogTheme_Dark
        } else {
            R.style.EInkDialogTheme
        }

        val dialog = AlertDialog.Builder(this, dialogTheme)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                saveLabel(app, editText.text.toString().trim())
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.reset) { _, _ ->
                prefs.setCustomLabel(app.packageName, null)
                updateAppLabelInList(app.packageName, app.label)
            }
            .create()
        
        activeEditDialog = dialog
        dialog.show()
        
        // Request focus and show keyboard
        editText.requestFocus()
    }
    
    /**
     * Saves the custom label and updates UI immediately.
     */
    private fun saveLabel(app: AppInfo, newLabel: String) {
        if (newLabel.isNotEmpty() && newLabel != app.label) {
            prefs.setCustomLabel(app.packageName, newLabel)
            updateAppLabelInList(app.packageName, newLabel)
        } else if (newLabel.isEmpty() || newLabel == app.label) {
            // Reset to original label
            prefs.setCustomLabel(app.packageName, null)
            updateAppLabelInList(app.packageName, app.label)
        }
    }
    
    /**
     * Updates a single app's label in the favorites list without full refresh.
     * Uses DiffUtil internally via submitList for efficient partial update.
     */
    private fun updateAppLabelInList(packageName: String, newLabel: String) {
        val currentList = favoritesAdapter.currentList.toMutableList()
        val index = currentList.indexOfFirst { it.packageName == packageName }
        
        if (index >= 0) {
            val updatedApp = currentList[index].copy(customLabel = 
                if (newLabel == currentList[index].label) null else newLabel
            )
            currentList[index] = updatedApp
            // Submit new list - DiffUtil will only update changed item
            favoritesAdapter.submitList(currentList)
        }
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java)
        startActivity(intent)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isAllAppsVisible) {
            hideAllApps()
        }
        // Don't call super - launcher should not exit on back press
    }
}
