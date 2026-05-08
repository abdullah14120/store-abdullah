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
import android.view.Menu
import android.view.MenuItem
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
 * Project: FIX ENGINE - Enterprise Edition
 * Updates: No-Filter Mode, Update Dialog, and Background Notifications
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var appAdapter: AppAdapter

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

        setupToolbar()
        setupRecyclerView()
        setupObservers()
        registerDownloadReceiver()
        setupBackgroundWork()

        handleIncomingDeepLink(intent)
        refreshData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "FIX ENGINE"
            subtitle = "مركز التحديثات والخدمات"
        }
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

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { binding.progressBar.isVisible = it }

        viewModel.appsList.observe(this) { apps ->
            if (!apps.isNullOrEmpty()) {
                // تم تعطيل الفلترة الذكية بناءً على طلبك - عرض القائمة كاملة
                appAdapter.submitList(apps)
                
                // فحص التحديثات لإظهار الديالوج
                checkForUpdates(apps)
            }
        }

        viewModel.errorMessage.observe(this) { it?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() } }
    }

    /**
     * فحص الإصدارات المثبتة ومقارنتها بالمستودع
     */
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
                    break // إظهار ديالوج واحد فقط
                }
            } catch (e: PackageManager.NameNotFoundException) {
                // التطبيق غير مثبت، نتجاهل الفحص له
            }
        }
    }

    private fun showUpdateDialog(app: com.fix.engine.abdullah.data.model.AppModel) {
        MaterialAlertDialogBuilder(this)
            .setTitle("تحديث جديد متاح!")
            .setMessage("تم العثور على إصدار جديد لتطبيق ${app.name}. هل تود الانتقال لصفحة التحديث؟")
            .setPositiveButton("تحديث الآن") { _, _ ->
                val intent = Intent(this, AppDetailsActivity::class.java).apply {
                    putExtra("APP_DATA", app)
                }
                startActivity(intent)
            }
            .setNegativeButton("ليس الآن", null)
            .show()
    }

    /**
     * إعداد فحص الخلفية للإشعارات كل 24 ساعة
     */
    private fun setupBackgroundWork() {
        val updateRequest = PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "FixEngineUpdateCheck",
            ExistingPeriodicWorkPolicy.KEEP,
            updateRequest
        )
    }

    private fun handleIncomingDeepLink(intent: Intent?) {
        val uri: Uri? = intent?.data
        if (uri != null && uri.scheme == "fixengine") {
            val pkgName = uri.getQueryParameter("pkg")
            pkgName?.let { pkg ->
                viewModel.appsList.value?.find { it.packageName == pkg }?.let { app ->
                    val detailsIntent = Intent(this, AppDetailsActivity::class.java).apply {
                        putExtra("APP_DATA", app)
                    }
                    startActivity(detailsIntent)
                }
            }
        }
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
            Toast.makeText(this, "فشل التثبيت: ${e.message}", Toast.LENGTH_LONG).show()
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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_refresh) refreshData()
        return super.onOptionsItemSelected(item)
    }
}
