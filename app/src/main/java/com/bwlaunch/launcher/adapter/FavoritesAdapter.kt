package com.bwlaunch.launcher.adapter

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bwlaunch.launcher.R
import com.bwlaunch.launcher.model.AppInfo
import com.bwlaunch.launcher.model.DisplayMode
import com.bwlaunch.launcher.model.FontType

/**
 * Adapter for displaying favorite apps on the home screen.
 * Uses DiffUtil to minimize UI updates and reduce e-ink refreshes.
 */
class FavoritesAdapter(
    private var displayMode: DisplayMode,
    private var fontType: FontType = FontType.SANS_SERIF,
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo) -> Boolean
) : ListAdapter<AppInfo, FavoritesAdapter.ViewHolder>(AppDiffCallback()) {

    fun setDisplayMode(mode: DisplayMode) {
        if (displayMode != mode) {
            displayMode = mode
            notifyDataSetChanged()
        }
    }

    fun setFontType(type: FontType) {
        if (fontType != type) {
            fontType = type
            notifyDataSetChanged()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return displayMode.ordinal
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutRes = when (DisplayMode.entries[viewType]) {
            DisplayMode.TEXT -> R.layout.item_app_text
            DisplayMode.ICONS_TEXT -> R.layout.item_app_icons_text
            DisplayMode.ICONS -> R.layout.item_app_icons
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return ViewHolder(view, displayMode)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), displayMode, fontType, onAppClick, onAppLongClick)
    }

    class ViewHolder(itemView: View, mode: DisplayMode) : RecyclerView.ViewHolder(itemView) {
        private val labelView: TextView? = itemView.findViewById(R.id.appLabel)
        private val iconView: ImageView? = itemView.findViewById(R.id.appIcon)

        fun bind(
            app: AppInfo,
            mode: DisplayMode,
            fontType: FontType,
            onClick: (AppInfo) -> Unit,
            onLongClick: (AppInfo) -> Boolean
        ) {
            // Apply font type
            val typeface = when (fontType) {
                FontType.SANS_SERIF -> Typeface.SANS_SERIF
                FontType.SERIF -> Typeface.SERIF
            }
            labelView?.typeface = typeface
            
            when (mode) {
                DisplayMode.TEXT -> {
                    labelView?.text = app.displayLabel
                }
                DisplayMode.ICONS_TEXT -> {
                    labelView?.text = app.displayLabel
                    iconView?.setImageDrawable(app.icon)
                }
                DisplayMode.ICONS -> {
                    iconView?.setImageDrawable(app.icon)
                    iconView?.contentDescription = app.displayLabel
                }
            }

            itemView.setOnClickListener { onClick(app) }
            itemView.setOnLongClickListener { onLongClick(app) }
        }
    }

    private class AppDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.displayLabel == newItem.displayLabel
        }
    }
}
