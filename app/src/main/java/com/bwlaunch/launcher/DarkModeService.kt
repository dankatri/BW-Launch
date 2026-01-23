package com.bwlaunch.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * Background service that monitors time and location changes
 * to trigger dark mode updates without requiring activity recreation.
 * 
 * Optimized for e-ink by using long polling intervals (1 minute)
 * to minimize battery and CPU usage.
 * 
 * Runs as a foreground service to ensure reliable dark mode switching.
 */
class DarkModeService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastDarkModeState: Boolean? = null
    
    companion object {
        private const val CHECK_INTERVAL_MS = 60_000L // Check every minute
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "dark_mode_service"
        
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
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
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
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.dark_mode_service_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.dark_mode_service_description)
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.dark_mode_monitoring))
            .setSmallIcon(R.drawable.ic_dark_mode)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

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
