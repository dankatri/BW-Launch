package com.bwlaunch.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.bwlaunch.launcher.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads and caches installed applications.
 * Optimized for minimal reloads to reduce e-ink refreshes.
 * 
 * E-INK OPTIMIZATION:
 * - Extended cache validity (5 minutes) to minimize app list queries
 * - Icon caching to prevent repeated drawable loading
 * - Only invalidates cache when apps are installed/uninstalled
 * 
 * EDGE CASE HANDLING:
 * - Gracefully handles uninstalled apps (filters them from favorites)
 * - Handles apps with changed package names (removes stale entries)
 * - Safely loads icons with fallback for missing drawables
 */
class AppLoader(private val context: Context) {

    private var cachedApps: List<AppInfo>? = null
    private var lastLoadTime: Long = 0
    
    // Extended cache for e-ink - 5 minutes instead of 30 seconds
    // App list changes infrequently, so aggressive caching is beneficial
    private val cacheValidityMs = 300_000L // 5 minutes cache

    /**
     * Get all launchable apps, using cache when possible.
     */
    suspend fun getAllApps(forceReload: Boolean = false): List<AppInfo> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        
        if (!forceReload && cachedApps != null && (now - lastLoadTime) < cacheValidityMs) {
            return@withContext cachedApps!!
        }

        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfoList: List<ResolveInfo> = try {
            pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
        } catch (e: Exception) {
            emptyList()
        }
        
        val apps = resolveInfoList
            .filter { it.activityInfo.packageName != context.packageName } // Exclude self
            .mapNotNull { resolveInfo ->
                try {
                    AppInfo(
                        packageName = resolveInfo.activityInfo.packageName,
                        activityName = resolveInfo.activityInfo.name,
                        label = resolveInfo.loadLabel(pm)?.toString() ?: resolveInfo.activityInfo.packageName,
                        icon = try {
                            resolveInfo.loadIcon(pm)
                        } catch (e: Exception) {
                            null
                        }
                    )
                } catch (e: Exception) {
                    null // Skip apps that fail to load
                }
            }
            .sortedBy { it.label.lowercase() }

        cachedApps = apps
        lastLoadTime = now
        apps
    }

    /**
     * Get favorite apps based on saved preferences.
     * Automatically cleans up uninstalled apps from favorites.
     */
    suspend fun getFavoriteApps(prefs: PreferencesManager): List<AppInfo> {
        val allApps = getAllApps()
        val favoritePackages = prefs.favorites.toMutableList()
        var needsCleanup = false
        
        val validFavorites = favoritePackages
            .mapNotNull { packageName ->
                val app = allApps.find { it.packageName == packageName }
                if (app == null) {
                    // App was uninstalled - mark for cleanup
                    needsCleanup = true
                    null
                } else {
                    app.copy(customLabel = prefs.getCustomLabel(packageName))
                }
            }
        
        // Clean up uninstalled apps from favorites list
        if (needsCleanup) {
            val installedPackages = allApps.map { it.packageName }.toSet()
            val cleanedFavorites = favoritePackages.filter { it in installedPackages }
            prefs.favorites = cleanedFavorites
            // Also clean up custom labels for uninstalled apps
            favoritePackages.forEach { packageName ->
                if (packageName !in installedPackages) {
                    prefs.setCustomLabel(packageName, null)
                }
            }
        }
        
        return validFavorites
    }

    /**
     * Get app info for a specific package.
     * Returns null if app is not installed.
     */
    suspend fun getAppInfo(packageName: String): AppInfo? {
        return getAllApps().find { it.packageName == packageName }
    }
    
    /**
     * Check if an app is still installed.
     */
    suspend fun isAppInstalled(packageName: String): Boolean {
        return getAllApps().any { it.packageName == packageName }
    }
    
    /**
     * Invalidate the cache to force reload on next access.
     */
    fun invalidateCache() {
        cachedApps = null
        lastLoadTime = 0
    }
}
