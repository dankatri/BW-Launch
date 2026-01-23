package com.bwlaunch.launcher.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bwlaunch.launcher.R
import com.bwlaunch.launcher.model.AppInfo

/**
 * Adapter for selecting and reordering favorite apps.
 * Selected apps appear at the top with drag handles.
 * Unselected apps appear below in alphabetical order.
 */
class ReorderableAppPickerAdapter(
    private val selectedPackages: MutableList<String>,
    private val onSelectionChanged: (List<String>) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onMoveItem: (Int, Int) -> Unit
) : ListAdapter<AppInfo, ReorderableAppPickerAdapter.ViewHolder>(AppDiffCallback()) {

    private var allApps: List<AppInfo> = emptyList()

    override fun submitList(list: List<AppInfo>?) {
        allApps = list ?: emptyList()
        updateDisplayList()
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun updateDisplayList() {
        // Build display list: selected apps first (in order), then unselected apps (alphabetically)
        val selectedApps = selectedPackages.mapNotNull { pkg ->
            allApps.find { it.packageName == pkg }
        }
        val unselectedApps = allApps.filter { !selectedPackages.contains(it.packageName) }
        
        val displayList = selectedApps + unselectedApps
        super.submitList(displayList)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_picker_reorderable, parent, false)
        return ViewHolder(view)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = getItem(position)
        val isSelected = selectedPackages.contains(app.packageName)
        val selectedIndex = selectedPackages.indexOf(app.packageName)
        
        holder.bind(app, isSelected, selectedIndex, selectedPackages.size)
        
        // Item click toggles selection
        holder.itemView.setOnClickListener {
            toggleSelection(app)
        }
        
        // Drag handle touch
        holder.dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN && isSelected) {
                onStartDrag(holder)
            }
            false
        }
        
        // Up button
        holder.upButton.setOnClickListener {
            val currentPos = holder.adapterPosition
            if (currentPos > 0 && isItemSelected(currentPos) && isItemSelected(currentPos - 1)) {
                moveItem(currentPos, currentPos - 1)
            }
        }
        
        // Down button
        holder.downButton.setOnClickListener {
            val currentPos = holder.adapterPosition
            if (currentPos < selectedPackages.size - 1 && isItemSelected(currentPos) && isItemSelected(currentPos + 1)) {
                moveItem(currentPos, currentPos + 1)
            }
        }
    }

    private fun toggleSelection(app: AppInfo) {
        if (selectedPackages.contains(app.packageName)) {
            selectedPackages.remove(app.packageName)
        } else {
            selectedPackages.add(app.packageName)
        }
        onSelectionChanged(selectedPackages.toList())
        updateDisplayList()
    }

    fun isItemSelected(position: Int): Boolean {
        if (position < 0 || position >= itemCount) return false
        val item = getItem(position)
        return selectedPackages.contains(item.packageName)
    }

    fun canMove(fromPos: Int, toPos: Int): Boolean {
        return isItemSelected(fromPos) && isItemSelected(toPos)
    }

    fun moveItem(fromPos: Int, toPos: Int) {
        if (!canMove(fromPos, toPos)) return
        
        val fromApp = getItem(fromPos)
        val toApp = getItem(toPos)
        
        val fromIndex = selectedPackages.indexOf(fromApp.packageName)
        val toIndex = selectedPackages.indexOf(toApp.packageName)
        
        if (fromIndex != -1 && toIndex != -1) {
            val item = selectedPackages.removeAt(fromIndex)
            selectedPackages.add(toIndex, item)
            onSelectionChanged(selectedPackages.toList())
            updateDisplayList()
        }
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkBox: CheckBox = itemView.findViewById(R.id.appCheckbox)
        val iconView: ImageView = itemView.findViewById(R.id.appIcon)
        val labelView: TextView = itemView.findViewById(R.id.appLabel)
        val orderView: TextView = itemView.findViewById(R.id.orderNumber)
        val dragHandle: ImageView = itemView.findViewById(R.id.dragHandle)
        val upButton: ImageButton = itemView.findViewById(R.id.upButton)
        val downButton: ImageButton = itemView.findViewById(R.id.downButton)
        private val reorderControls: View = itemView.findViewById(R.id.reorderControls)

        fun bind(app: AppInfo, isSelected: Boolean, selectedIndex: Int, totalSelected: Int) {
            checkBox.isChecked = isSelected
            iconView.setImageDrawable(app.icon)
            labelView.text = app.label
            
            // Show reorder controls only for selected items
            reorderControls.visibility = if (isSelected) View.VISIBLE else View.GONE
            
            if (isSelected) {
                orderView.text = "${selectedIndex + 1}"
                upButton.isEnabled = selectedIndex > 0
                upButton.alpha = if (selectedIndex > 0) 1f else 0.3f
                downButton.isEnabled = selectedIndex < totalSelected - 1
                downButton.alpha = if (selectedIndex < totalSelected - 1) 1f else 0.3f
            }
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
