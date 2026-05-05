package com.musa.t9keyboard

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.musa.t9keyboard.databinding.ActivityAppBlacklistBinding

data class AppInfo(val packageName: String, val appName: String, val icon: Drawable, val isBlacklisted: Boolean)

class AppBlacklistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppBlacklistBinding
    private lateinit var preferences: PreferencesManager
    private lateinit var adapter: AppBlacklistAdapter
    private val appList = mutableListOf<AppInfo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppBlacklistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = PreferencesManager(this)

        setupToolbar()
        setupRecyclerView()
        loadInstalledApps()
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

    private fun loadInstalledApps() {
        val pm = packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val resolveInfos = pm.queryIntentActivities(intent, 0)

        appList.clear()
        resolveInfos.forEach { info ->
            val packageName = info.activityInfo.packageName
            if (packageName != this.packageName) {
                val appName = info.loadLabel(pm).toString()
                val icon = info.loadIcon(pm)
                val isBlacklisted = preferences.isAppBlacklisted(packageName)
                appList.add(AppInfo(packageName, appName, icon, isBlacklisted))
            }
        }
        appList.sortBy { it.appName.lowercase() }
        adapter.submitList(appList.toList())
    }

    private fun updateAppList() {
        val updated = appList.map {
            it.copy(isBlacklisted = preferences.isAppBlacklisted(it.packageName))
        }
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
