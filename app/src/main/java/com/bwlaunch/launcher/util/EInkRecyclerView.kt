package com.bwlaunch.launcher.util

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView optimized for e-ink displays.
 * 
 * Features:
 * - Disabled over-scroll and edge effects
 * - No item change animations
 * - Optimized scrolling for e-ink refresh rates
 */
class EInkRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr) {

    init {
        // Disable all animations
        itemAnimator = null
        
        // Disable over-scroll effects
        overScrollMode = OVER_SCROLL_NEVER
        
        // Disable edge effects (glow/ripple)
        edgeEffectFactory = object : EdgeEffectFactory() {
            override fun createEdgeEffect(view: RecyclerView, direction: Int) =
                NoOpEdgeEffect(context)
        }
        
        // Disable scrollbars for cleaner rendering
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        
        // Use software layer for consistent e-ink rendering
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        
        // Disable nested scrolling to simplify scroll handling
        isNestedScrollingEnabled = false
        
        // Reduce scroll sensitivity for e-ink
        setScrollingTouchSlop(TOUCH_SLOP_PAGING)
    }

    /**
     * Edge effect that does nothing - prevents glow effects on e-ink.
     */
    private class NoOpEdgeEffect(context: Context) : android.widget.EdgeEffect(context) {
        override fun draw(canvas: android.graphics.Canvas): Boolean = false
        override fun onAbsorb(velocity: Int) {}
        override fun onPull(deltaDistance: Float) {}
        override fun onPull(deltaDistance: Float, displacement: Float) {}
        override fun onRelease() {}
        override fun isFinished(): Boolean = true
    }

    /**
     * Override fling to reduce scroll speed for e-ink displays.
     * Fast scrolling causes heavy ghosting on e-ink.
     */
    override fun fling(velocityX: Int, velocityY: Int): Boolean {
        // Reduce fling velocity by 50% for e-ink
        val reducedVelocityX = (velocityX * 0.5f).toInt()
        val reducedVelocityY = (velocityY * 0.5f).toInt()
        return super.fling(reducedVelocityX, reducedVelocityY)
    }
}
