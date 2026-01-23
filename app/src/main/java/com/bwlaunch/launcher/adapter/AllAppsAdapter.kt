package com.bwlaunch.launcher.adapter

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

/**
 * Adapter for the All Apps grid drawer.
 * Uses DiffUtil to minimize UI updates and reduce e-ink refreshes.
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
        holder.bind(getItem(position), onAppClick)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.appIcon)
        private val labelView: TextView = itemView.findViewById(R.id.appLabel)

        fun bind(app: AppInfo, onClick: (AppInfo) -> Unit) {
            iconView.setImageDrawable(app.icon)
            labelView.text = app.displayLabel
            itemView.contentDescription = app.displayLabel
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
