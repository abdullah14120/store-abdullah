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
import com.fix.engine.abdullah.databinding.ActivityMainBinding
import com.fix.engine.abdullah.ui.adapter.AppAdapter
import com.fix.engine.abdullah.ui.details.AppDetailsActivity
import com.fix.engine.abdullah.ui.viewmodel.MainViewModel
import java.io.File

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Intelligent Bridge
 * Updates: GBWhatsApp Tag Filtering & Deep Link Handling
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

        // معالجة الرابط القادم من الدالة المحقونة (Smali)
        handleIncomingDeepLink(intent)
        
        refreshData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "FIX ENGINE"
            subtitle = "نظام التحديث الذكي مفعل"
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
                // الفلترة: عرض تطبيقاتك فقط (المثبتة بوسم GBWhatsApp أو غير المثبتة إطلاقاً)
                val filteredList = apps.filter { app ->
                    try {
                        val pInfo = packageManager.getPackageInfo(app.packageName, PackageManager.GET_ACTIVITIES)
                        pInfo.activities?.any { it.nonLocalizedLabel == "GBWhatsApp" } ?: false
                    } catch (e: PackageManager.NameNotFoundException) {
                        true // إظهار التطبيق في المتجر إذا لم يكن ثبّت بعد
                    }
                }
                appAdapter.submitList(filteredList)
                checkVersionUpdates(filteredList)
            }
        }

        viewModel.errorMessage.observe(this) { it?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() } }
    }

    /**
     * معالجة روابط: fixengine://details?pkg=com.package.name
     */
    private fun handleIncomingDeepLink(intent: Intent?) {
        val uri: Uri? = intent?.data
        if (uri != null && uri.scheme == "fixengine") {
            val pkgName = uri.getQueryParameter("pkg")
            pkgName?.let { pkg ->
                // البحث عن الحزمة وفتح صفحة تفاصيلها مباشرة
                viewModel.appsList.value?.find { it.packageName == pkg }?.let { app ->
                    val detailsIntent = Intent(this, AppDetailsActivity::class.java).apply {
                        putExtra("APP_DATA", app)
                    }
                    startActivity(detailsIntent)
                }
            }
        }
    }

    /**
     * مقارنة النسخة الحالية بالنسخة في المستودع (بناءً على versionName)
     */
    private fun checkVersionUpdates(apps: List<com.fix.engine.abdullah.data.model.AppModel>) {
        apps.forEach { app ->
            try {
                val currentVersion = packageManager.getPackageInfo(app.packageName, 0).versionName
                if (currentVersion != app.versionName) {
                    // يمكنك هنا إظهار إشعار بسيط بوجود تحديث
                }
            } catch (e: Exception) {}
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
