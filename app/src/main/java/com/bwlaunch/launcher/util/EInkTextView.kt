package com.bwlaunch.launcher.util

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * Custom TextView optimized for e-ink displays.
 * 
 * Features:
 * - Disabled anti-aliasing option for sharper text on e-ink
 * - No subpixel rendering (causes color fringing on e-ink)
 * - High contrast text rendering
 */
class EInkTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    init {
        // Disable font smoothing for sharper edges on e-ink
        // Note: This can make text look jagged on LCD but is better for e-ink
        paint.isAntiAlias = true // Keep true for readability
        paint.isSubpixelText = false // Disable subpixel rendering
        paint.isFilterBitmap = false // Disable bitmap filtering
        
        // Ensure high contrast
        paint.isFakeBoldText = false
        
        // Use hinting for better glyph alignment
        paint.hinting = android.graphics.Paint.HINTING_ON
        
        // Set layer type to software for consistent rendering
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        
        // Disable shadows
        setShadowLayer(0f, 0f, 0f, 0)
    }

    /**
     * Enable or disable anti-aliasing.
     * Disabling can improve sharpness on some e-ink displays.
     */
    fun setAntiAlias(enabled: Boolean) {
        paint.isAntiAlias = enabled
        invalidate()
    }

    /**
     * Set typeface with e-ink optimizations.
     */
    override fun setTypeface(tf: Typeface?) {
        super.setTypeface(tf)
        // Re-apply e-ink optimizations after typeface change
        paint.isSubpixelText = false
    }
}
