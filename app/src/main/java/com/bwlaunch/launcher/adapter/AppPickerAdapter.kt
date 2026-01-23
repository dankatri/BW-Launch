package com.bwlaunch.launcher.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bwlaunch.launcher.R
import com.bwlaunch.launcher.model.AppInfo

/**
 * Adapter for selecting favorite apps.
 */
class AppPickerAdapter(
    private val selectedPackages: MutableSet<String>,
    private val maxSelection: Int,
    private val onSelectionChanged: (Set<String>) -> Unit
) : ListAdapter<AppInfo, AppPickerAdapter.ViewHolder>(AppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_picker, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = getItem(position)
        val isSelected = selectedPackages.contains(app.packageName)
        holder.bind(app, isSelected) { toggleSelection(app) }
    }

    private fun toggleSelection(app: AppInfo) {
        if (selectedPackages.contains(app.packageName)) {
            selectedPackages.remove(app.packageName)
        } else if (selectedPackages.size < maxSelection) {
            selectedPackages.add(app.packageName)
        }
        notifyDataSetChanged()
        onSelectionChanged(selectedPackages.toSet())
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val checkBox: CheckBox = itemView.findViewById(R.id.appCheckbox)
        private val iconView: ImageView = itemView.findViewById(R.id.appIcon)
        private val labelView: TextView = itemView.findViewById(R.id.appLabel)

        fun bind(app: AppInfo, isSelected: Boolean, onClick: () -> Unit) {
            checkBox.isChecked = isSelected
            iconView.setImageDrawable(app.icon)
            labelView.text = app.label
            itemView.setOnClickListener { onClick() }
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
