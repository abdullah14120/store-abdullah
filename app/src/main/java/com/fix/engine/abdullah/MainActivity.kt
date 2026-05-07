package com.fix.engine.abdullah

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
 * Project: FIX ENGINE - Core Store Activity
 * Updates: Android DownloadManager Integration & Auto-Installer
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var appAdapter: AppAdapter

    // مراقب انتهاء التحميل الخاص بالنظام
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id != -1L) {
                checkDownloadStatus(id)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupObservers()
        
        // تسجيل المراقب لسماع إشارة انتهاء التحميل
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(downloadReceiver, filter)
        }

        refreshData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "FIX ENGINE"
            subtitle = "By Abdullah Al-Tamimi"
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
            setHasFixedSize(true)
        }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.isVisible = isLoading
        }

        viewModel.appsList.observe(this) { apps ->
            if (!apps.isNullOrEmpty()) {
                appAdapter.submitList(apps)
            }
        }

        viewModel.errorMessage.observe(this) { error ->
            error?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
        }
    }

    /**
     * التحقق من حالة الملف المحمل وبدء التثبيت
     */
    private fun checkDownloadStatus(id: Long) {
        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(id)
        val cursor = downloadManager.query(query)

        if (cursor.moveToFirst()) {
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (cursor.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL) {
                val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val fileUri = Uri.parse(cursor.getString(uriIndex))
                
                // تحويل الـ URI إلى ملف حقيقي للبدء بالتثبيت
                val file = fileUri.path?.let { File(it) }
                if (file != null && file.exists()) {
                    installApk(file)
                } else {
                    // في بعض الأجهزة الحديثة نحتاج للحصول على المسار عبر ContentResolver
                    Toast.makeText(this, "اكتمل التحميل بنجاح", Toast.LENGTH_SHORT).show()
                }
            }
        }
        cursor.close()
    }

    /**
     * دالة التثبيت التلقائي - Auto Installer
     */
    private fun installApk(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ في تشغيل المثبّت: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshData() {
        val repoUrl = "https://raw.githubusercontent.com/Abdullah-AlTamimi/FixEngine/main/apps.json"
        viewModel.loadApps(repoUrl)
    }

    override fun onDestroy() {
        super.onDestroy()
        // إلغاء التسجيل لمنع تسريب الذاكرة
        unregisterReceiver(downloadReceiver)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                refreshData()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
