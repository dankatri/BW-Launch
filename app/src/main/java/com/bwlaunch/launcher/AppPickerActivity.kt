package com.bwlaunch.launcher

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bwlaunch.launcher.adapter.ReorderableAppPickerAdapter
import com.bwlaunch.launcher.model.AppInfo
import kotlinx.coroutines.launch

/**
 * Activity for selecting and ordering favorite apps to display on the home screen.
 * Features:
 * - Checkbox selection for apps
 * - Drag-and-drop reordering of selected apps
 * - Up/down arrow buttons for fine-grained ordering
 */
class AppPickerActivity : AppCompatActivity() {

    private lateinit var prefs: PreferencesManager
    private lateinit var appLoader: AppLoader
    private lateinit var adapter: ReorderableAppPickerAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = PreferencesManager(this)
        setTheme(if (prefs.shouldUseDarkMode()) R.style.Theme_BWLaunch_Dark else R.style.Theme_BWLaunch)
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.pick_apps_title)

        appLoader = AppLoader(this)
        recyclerView = findViewById(R.id.appsRecyclerView)

        setupRecyclerView()
        loadApps()
    }

    private fun setupRecyclerView() {
        adapter = ReorderableAppPickerAdapter(
            selectedPackages = prefs.favorites.toMutableList(),
            maxSelection = prefs.favoriteCount,
            onSelectionChanged = { orderedSelection ->
                prefs.setFavoritesOrdered(orderedSelection)
            },
            onStartDrag = { viewHolder ->
                itemTouchHelper.startDrag(viewHolder)
            },
            onMoveItem = { fromPos, toPos ->
                // This is handled by the adapter
            }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@AppPickerActivity)
            adapter = this@AppPickerActivity.adapter
            itemAnimator = null // Disable animations for e-ink
        }

        // Setup drag-and-drop
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                
                // Only allow moving selected items among themselves
                if (adapter.canMove(fromPos, toPos)) {
                    adapter.moveItem(fromPos, toPos)
                    return true
                }
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // No swipe action
            }

            override fun isLongPressDragEnabled(): Boolean = false

            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                // Only allow dragging for selected items
                val position = viewHolder.adapterPosition
                if (position != RecyclerView.NO_POSITION && adapter.isItemSelected(position)) {
                    return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
                }
                return 0
            }
        }

        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun loadApps() {
        lifecycleScope.launch {
            val apps = appLoader.getAllApps(forceReload = true)
            adapter.submitList(apps)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
