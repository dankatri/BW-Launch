package com.bwlaunch.launcher

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/**
 * Background service that monitors time and location changes
 * to trigger dark mode updates without requiring activity recreation.
 * 
 * Optimized for e-ink by using long polling intervals (1 minute)
 * to minimize battery and CPU usage.
 */
class DarkModeService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastDarkModeState: Boolean? = null
    
    companion object {
        private const val CHECK_INTERVAL_MS = 60_000L // Check every minute
        const val ACTION_DARK_MODE_CHANGED = "com.bwlaunch.launcher.DARK_MODE_CHANGED"
        const val EXTRA_IS_DARK_MODE = "is_dark_mode"
    }

    private val checkRunnable = object : Runnable {
        override fun run() {
            checkDarkModeState()
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Start checking immediately
        handler.post(checkRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun checkDarkModeState() {
        val prefs = PreferencesManager(this)
        val shouldBeDark = prefs.shouldUseDarkMode()
        
        // Only broadcast if state changed
        if (lastDarkModeState != shouldBeDark) {
            lastDarkModeState = shouldBeDark
            
            val broadcastIntent = Intent(ACTION_DARK_MODE_CHANGED).apply {
                putExtra(EXTRA_IS_DARK_MODE, shouldBeDark)
                setPackage(packageName)
            }
            sendBroadcast(broadcastIntent)
        }
    }
}
