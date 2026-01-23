package com.bwlaunch.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.bwlaunch.launcher.model.DarkModeSchedule

/**
 * Receiver to restart DarkModeService after device boot.
 * 
 * This ensures the dark mode scheduling continues working
 * even after the device is restarted.
 */
class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val preferencesManager = PreferencesManager(context)
            
            // Only start service if using automatic dark mode scheduling
            val schedule = preferencesManager.darkModeSchedule
            if (schedule == DarkModeSchedule.TIME_BASED || schedule == DarkModeSchedule.SUNRISE_SUNSET) {
                val serviceIntent = Intent(context, DarkModeService::class.java)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
