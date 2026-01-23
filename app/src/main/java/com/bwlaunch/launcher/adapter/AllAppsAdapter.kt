package com.bwlaunch.launcher.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bwlaunch.launcher.R
import com.bwlaunch.launcher.model.AppInfo

/**
 * Adapter for the All Apps grid drawer.
 * Uses DiffUtil to minimize UI updates and reduce e-ink refreshes.
 * Includes accessibility support for TalkBack.
 */
class AllAppsAdapter(
    private val onAppClick: (AppInfo) -> Unit
) : ListAdapter<AppInfo, AllAppsAdapter.ViewHolder>(AppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_grid, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position, itemCount, onAppClick)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.appIcon)
        private val labelView: TextView = itemView.findViewById(R.id.appLabel)

        fun bind(app: AppInfo, position: Int, totalCount: Int, onClick: (AppInfo) -> Unit) {
            iconView.setImageDrawable(app.icon)
            labelView.text = app.displayLabel
            
            // Set up content description for TalkBack
            val context = itemView.context
            itemView.contentDescription = context.getString(
                R.string.cd_app_position,
                app.displayLabel,
                position + 1,
                totalCount
            )
            
            // Icon is decorative when parent has content description
            iconView.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            
            // Custom accessibility delegate for better TalkBack experience
            ViewCompat.setAccessibilityDelegate(itemView, object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfoCompat
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.roleDescription = context.getString(R.string.cd_role_app)
                }
            })
            
            itemView.setOnClickListener { onClick(app) }
        }
    }

    private class AppDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.label == newItem.label
        }
    }
}
