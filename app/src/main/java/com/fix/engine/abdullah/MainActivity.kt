package com.fix.engine.abdullah

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.*
import com.fix.engine.abdullah.databinding.ActivityMainBinding
import com.fix.engine.abdullah.ui.adapter.AppAdapter
import com.fix.engine.abdullah.ui.details.AppDetailsActivity
import com.fix.engine.abdullah.ui.viewmodel.MainViewModel
import com.fix.engine.abdullah.service.UpdateWorker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: Abdullah Store - Professional UI Edition
 * Features: Live Search, Auto-Update Check, and Dynamic UI
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var appAdapter: AppAdapter
    private var fullList: List<com.fix.engine.abdullah.data.model.AppModel> = emptyList()

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id != -1L) checkDownloadStatus(id)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupObservers()
        setupSearchLogic()
        registerDownloadReceiver()
        setupBackgroundWork()

        refreshData()
    }

    private fun setupRecyclerView() {
        appAdapter = AppAdapter { app ->
            val intent = Intent(this, AppDetailsActivity::class.java).apply {
                putExtra("APP_DATA", app)
            }
            startActivity(intent)
        }
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = appAdapter
        }
    }

    /**
     * منطق البحث المباشر (Live Search)
     */
    private fun setupSearchLogic() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterApps(query: String) {
        val filteredList = if (query.isEmpty()) {
            fullList
        } else {
            fullList.filter { it.name.contains(query, ignoreCase = true) || it.developer.contains(query, ignoreCase = true) }
        }
        appAdapter.submitList(filteredList)
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { binding.progressBar.isVisible = it }

        viewModel.appsList.observe(this) { apps ->
            if (!apps.isNullOrEmpty()) {
                fullList = apps
                appAdapter.submitList(apps)
                checkForUpdates(apps)
            }
        }

        viewModel.errorMessage.observe(this) { it?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() } }
    }

    private fun checkForUpdates(apps: List<com.fix.engine.abdullah.data.model.AppModel>) {
        for (app in apps) {
            try {
                val pInfo = packageManager.getPackageInfo(app.packageName, 0)
                val installedVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pInfo.versionCode.toLong()
                }

                if (app.versionCode > installedVersionCode) {
                    showUpdateDialog(app)
                    break 
                }
            } catch (e: PackageManager.NameNotFoundException) {}
        }
    }

    private fun showUpdateDialog(app: com.fix.engine.abdullah.data.model.AppModel) {
        MaterialAlertDialogBuilder(this)
            .setTitle("تحديث متاح")
            .setMessage("هناك نسخة جديدة من ${app.name} جاهزة للتحميل. هل تريد التحديث؟")
            .setPositiveButton("تحديث الآن") { _, _ ->
                val intent = Intent(this, AppDetailsActivity::class.java).apply {
                    putExtra("APP_DATA", app)
                }
                startActivity(intent)
            }
            .setNegativeButton("تجاهل", null)
            .show()
    }

    private fun setupBackgroundWork() {
        val updateRequest = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "AbdullahStoreUpdate",
            ExistingPeriodicWorkPolicy.KEEP,
            updateRequest
        )
    }

    private fun registerDownloadReceiver() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(downloadReceiver, filter)
        }
    }

    @SuppressLint("Range")
    private fun checkDownloadStatus(id: Long) {
        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(id)
        val cursor = downloadManager.query(query)

        if (cursor != null && cursor.moveToFirst()) {
            val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                val uriString = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                uriString?.let { installApk(File(Uri.parse(it).path ?: "")) }
            }
            cursor.close()
        }
    }

    private fun installApk(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "حدث خطأ أثناء التثبيت", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshData() {
        val repoUrl = "https://raw.githubusercontent.com/abdullah14120/store-abdullah/refs/heads/main/apps.json"
        viewModel.loadApps(repoUrl)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(downloadReceiver) } catch (e: Exception) {}
    }
}
