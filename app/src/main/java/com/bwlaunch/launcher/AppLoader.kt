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
 */
class AppLoader(private val context: Context) {

    private var cachedApps: List<AppInfo>? = null
    private var lastLoadTime: Long = 0
    private val cacheValidityMs = 30_000L // 30 seconds cache

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

        val resolveInfoList: List<ResolveInfo> = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
        
        val apps = resolveInfoList
            .filter { it.activityInfo.packageName != context.packageName } // Exclude self
            .map { resolveInfo ->
                AppInfo(
                    packageName = resolveInfo.activityInfo.packageName,
                    activityName = resolveInfo.activityInfo.name,
                    label = resolveInfo.loadLabel(pm).toString(),
                    icon = try {
                        resolveInfo.loadIcon(pm)
                    } catch (e: Exception) {
                        null
                    }
                )
            }
            .sortedBy { it.label.lowercase() }

        cachedApps = apps
        lastLoadTime = now
        apps
    }

    /**
     * Get favorite apps based on saved preferences.
     */
    suspend fun getFavoriteApps(prefs: PreferencesManager): List<AppInfo> {
        val allApps = getAllApps()
        val favoritePackages = prefs.favorites
        
        return favoritePackages
            .take(prefs.favoriteCount)
            .mapNotNull { packageName ->
                allApps.find { it.packageName == packageName }?.copy(
                    customLabel = prefs.getCustomLabel(packageName)
                )
            }
    }

    /**
     * Get app info for a specific package.
     */
    suspend fun getAppInfo(packageName: String): AppInfo? {
        return getAllApps().find { it.packageName == packageName }
    }

    /**
     * Invalidate the cache to force reload on next access.
     */
    fun invalidateCache() {
        cachedApps = null
        lastLoadTime = 0
    }
}
