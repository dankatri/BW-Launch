package com.bwlaunch.launcher.util

import android.os.Handler
import android.os.Looper

/**
 * Utility class for debouncing UI updates to reduce e-ink refreshes.
 * 
 * E-ink displays have slow refresh rates, so rapid updates cause
 * visible flicker. Debouncing ensures only the final state is rendered.
 */
class Debouncer(private val delayMs: Long = DEFAULT_DELAY_MS) {

    companion object {
        const val DEFAULT_DELAY_MS = 100L
        const val LONG_DELAY_MS = 250L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pendingRunnable: Runnable? = null

    /**
     * Schedule an action to run after the debounce delay.
     * If called again before the delay expires, the previous action is cancelled.
     */
    fun debounce(action: () -> Unit) {
        pendingRunnable?.let { handler.removeCallbacks(it) }
        pendingRunnable = Runnable {
            action()
            pendingRunnable = null
        }
        handler.postDelayed(pendingRunnable!!, delayMs)
    }

    /**
     * Cancel any pending debounced action.
     */
    fun cancel() {
        pendingRunnable?.let { handler.removeCallbacks(it) }
        pendingRunnable = null
    }

    /**
     * Execute immediately and cancel any pending action.
     */
    fun executeNow(action: () -> Unit) {
        cancel()
        action()
    }
}
