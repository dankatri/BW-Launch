package com.bwlaunch.launcher.model

import android.graphics.drawable.Drawable

/**
 * Represents an installed application.
 */
data class AppInfo(
    val packageName: String,
    val activityName: String,
    val label: String,
    val customLabel: String? = null,
    val icon: Drawable? = null
) {
    val displayLabel: String
        get() = customLabel ?: label
}
