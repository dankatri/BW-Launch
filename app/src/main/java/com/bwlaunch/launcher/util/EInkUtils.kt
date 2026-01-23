package com.bwlaunch.launcher.util

import android.view.View

/**
 * Utility functions for optimizing views for e-ink displays.
 * 
 * E-ink displays have unique characteristics:
 * - Slow refresh rate (typically 100-300ms for full refresh)
 * - Partial refresh is faster but may leave ghosting
 * - No backlight, relying on ambient light
 * - Best with high contrast, solid colors
 */
object EInkUtils {

    /**
     * Optimize a view hierarchy for e-ink rendering.
     * Disables hardware acceleration features that cause issues on e-ink.
     */
    fun optimizeForEInk(view: View) {
        // Disable drawing cache (deprecated but still affects some devices)
        @Suppress("DEPRECATION")
        view.isDrawingCacheEnabled = false
        
        // Use software layer for consistent rendering
        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        
        // Disable over-scroll effects
        view.overScrollMode = View.OVER_SCROLL_NEVER
        
        // Disable scrollbars to reduce redraws
        view.isVerticalScrollBarEnabled = false
        view.isHorizontalScrollBarEnabled = false
    }

    /**
     * Mark a view as having transient state to hint the system
     * that it will be updated soon and shouldn't be cached.
     */
    fun setTransientState(view: View, isTransient: Boolean) {
        view.setHasTransientState(isTransient)
    }

    /**
     * Prepare a view for an upcoming change by marking it transient,
     * execute the change, then clear the transient flag.
     */
    inline fun withTransientState(view: View, action: () -> Unit) {
        view.setHasTransientState(true)
        try {
            action()
        } finally {
            view.post { view.setHasTransientState(false) }
        }
    }

    /**
     * Force a view to redraw only itself, not children.
     * Useful for partial updates on e-ink.
     */
    fun invalidatePartial(view: View) {
        view.invalidate()
    }

    /**
     * Force a specific region of a view to redraw.
     * More efficient than full invalidation on e-ink.
     */
    fun invalidateRegion(view: View, left: Int, top: Int, right: Int, bottom: Int) {
        view.invalidate(left, top, right, bottom)
    }

    /**
     * Calculate minimum tap target size for e-ink.
     * E-ink devices often have less precise touch, so larger targets help.
     */
    fun getMinTapTargetDp(): Int = 56 // Recommended minimum for e-ink

    /**
     * Check if a color is suitable for e-ink (pure black or white).
     */
    fun isEInkOptimalColor(color: Int): Boolean {
        return color == 0xFF000000.toInt() || color == 0xFFFFFFFF.toInt()
    }
}
