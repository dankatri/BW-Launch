package com.bwlaunch.launcher

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bwlaunch.launcher.adapter.AllAppsAdapter
import com.bwlaunch.launcher.adapter.FavoritesAdapter
import com.bwlaunch.launcher.databinding.ActivityMainBinding
import com.bwlaunch.launcher.model.AppInfo
import com.bwlaunch.launcher.model.DisplayMode
import com.bwlaunch.launcher.model.FontType
import kotlinx.coroutines.launch

/**
 * Main launcher activity optimized for e-ink displays.
 * 
 * Features:
 * - Minimal animations to reduce screen refreshes
 * - Configurable display modes (text, icons+text, icons)
 * - Long-press gestures for settings and label editing
 * - All Apps overlay drawer
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var appLoader: AppLoader
    private lateinit var favoritesAdapter: FavoritesAdapter
    private lateinit var allAppsAdapter: AllAppsAdapter

    private var isAllAppsVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply theme before super.onCreate
        prefs = PreferencesManager(this)
        applyTheme()
        
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        appLoader = AppLoader(this)
        
        setupFavoritesList()
        setupAllAppsDrawer()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        // Check if theme needs to change (scheduled dark mode)
        checkThemeChange()
        // Reload favorites in case preferences changed
        loadFavorites()
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

    private fun setupFavoritesList() {
        favoritesAdapter = FavoritesAdapter(
            displayMode = prefs.displayMode,
            onAppClick = { app -> launchApp(app) },
            onAppLongClick = { app -> showEditLabelDialog(app); true }
        )

        binding.favoritesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = favoritesAdapter
            // Disable item animations for e-ink
            itemAnimator = null
        }
    }

    private fun setupAllAppsDrawer() {
        allAppsAdapter = AllAppsAdapter { app ->
            launchApp(app)
            hideAllApps()
        }

        binding.allAppsRecyclerView.apply {
            // 4-column grid for app drawer
            layoutManager = GridLayoutManager(this@MainActivity, 4)
            adapter = allAppsAdapter
            // Disable item animations for e-ink
            itemAnimator = null
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

        // Long-press on empty space opens settings
        binding.homeContainer.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            openSettings()
            true
        }
    }

    private fun loadFavorites() {
        lifecycleScope.launch {
            val favorites = appLoader.getFavoriteApps(prefs)
            favoritesAdapter.setDisplayMode(prefs.displayMode)
            favoritesAdapter.setFontType(prefs.fontType)
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
                }
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    private fun showEditLabelDialog(app: AppInfo) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_label, null)
        val editText = dialogView.findViewById<EditText>(R.id.labelEditText)
        editText.setText(app.displayLabel)
        editText.selectAll()

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val newLabel = editText.text.toString().trim()
                if (newLabel.isNotEmpty() && newLabel != app.label) {
                    prefs.setCustomLabel(app.packageName, newLabel)
                } else {
                    prefs.setCustomLabel(app.packageName, null)
                }
                loadFavorites()
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.reset) { _, _ ->
                prefs.setCustomLabel(app.packageName, null)
                loadFavorites()
            }
            .show()
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
