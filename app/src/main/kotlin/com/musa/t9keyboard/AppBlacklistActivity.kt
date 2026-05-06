package com.musa.t9keyboard

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.musa.t9keyboard.databinding.ActivityAppBlacklistBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppInfo(val packageName: String, val appName: String, val icon: Drawable, val isBlacklisted: Boolean)

class AppBlacklistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppBlacklistBinding
    private lateinit var preferences: PreferencesManager
    private lateinit var adapter: AppBlacklistAdapter
    private var appList = listOf<AppInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppBlacklistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = PreferencesManager(this)

        setupToolbar()
        setupRecyclerView()

        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                loadInstalledApps()
            }
            appList = apps
            adapter.submitList(apps)
        }
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = AppBlacklistAdapter { packageName ->
            preferences.toggleAppBlacklist(packageName)
            updateAppList()
        }
        binding.recyclerApps.adapter = adapter
    }

    private fun loadInstalledApps(): List<AppInfo> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        return try {
            pm.queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName != this.packageName }
                .map { info ->
                    val packageName = info.activityInfo.packageName
                    val appName = info.loadLabel(pm).toString()
                    val icon = info.loadIcon(pm)
                    val isBlacklisted = preferences.isAppBlacklisted(packageName)
                    AppInfo(packageName, appName, icon, isBlacklisted)
                }
                .sortedBy { it.appName.lowercase() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun updateAppList() {
        val updated = appList.map {
            it.copy(isBlacklisted = preferences.isAppBlacklisted(it.packageName))
        }
        appList = updated
        adapter.submitList(updated)
    }
}

class AppBlacklistAdapter(private val onToggle: (String) -> Unit) : ListAdapter<AppInfo, AppBlacklistAdapter.ViewHolder>(AppInfoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.app_blacklist_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = getItem(position)
        holder.bind(app)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imgIcon: ImageView = itemView.findViewById(R.id.img_app_icon)
        private val txtAppName: TextView = itemView.findViewById(R.id.txt_app_name)
        private val imgToggle: ImageView = itemView.findViewById(R.id.img_toggle)

        fun bind(app: AppInfo) {
            imgIcon.setImageDrawable(app.icon)
            txtAppName.text = app.appName
            if (app.isBlacklisted) {
                imgToggle.setImageResource(R.drawable.ic_toggle_on)
            } else {
                imgToggle.setImageResource(R.drawable.ic_toggle_off)
            }
            itemView.setOnClickListener {
                onToggle(app.packageName)
            }
        }
    }
}

class AppInfoDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
    override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
        return oldItem.packageName == newItem.packageName
    }

    override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
        return oldItem.isBlacklisted == newItem.isBlacklisted
    }
}
