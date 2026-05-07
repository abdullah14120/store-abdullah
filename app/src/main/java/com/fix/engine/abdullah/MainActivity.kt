package com.fix.engine.abdullah

import android.annotation.SuppressLint
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
 * Project: FIX ENGINE
 * Feature: Enhanced User Notifications & Auto-Installer
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var appAdapter: AppAdapter

    // مراقب أحداث التحميل لإخطار المستخدم بالنتائج
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
        
        // تسجيل المراقب (Receiver) مع مراعاة الحماية في أندرويد 13+
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
        supportActionBar?.title = "FIX ENGINE"
        // إضافة ملاحظة للمستخدم في الشريط العلوي
        binding.toolbar.subtitle = "متجر التطبيقات الخاص بك"
    }

    private fun setupRecyclerView() {
        appAdapter = AppAdapter { app ->
            // إبلاغ المستخدم بالانتقال
            Toast.makeText(this, "فتح تفاصيل ${app.name}", Toast.LENGTH_SHORT).show()
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
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.isVisible = isLoading
            if (isLoading) {
                // ملاحظة للمستخدم عند بدء جلب البيانات
                Toast.makeText(this, "جاري تحديث القائمة...", Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.appsList.observe(this) { apps ->
            if (apps.isNullOrEmpty()) {
                Toast.makeText(this, "لا توجد تطبيقات متاحة حالياً", Toast.LENGTH_LONG).show()
            } else {
                appAdapter.submitList(apps)
            }
        }

        viewModel.errorMessage.observe(this) { error ->
            error?.let { 
                // إظهار ملاحظة الخطأ بشكل بارز
                Toast.makeText(this, "تنبيه: $it", Toast.LENGTH_LONG).show() 
            }
        }
    }

    @SuppressLint("Range")
    private fun checkDownloadStatus(id: Long) {
        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(id)
        val cursor = downloadManager.query(query)

        if (cursor != null && cursor.moveToFirst()) {
            val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
            
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    Toast.makeText(this, "اكتمل التحميل! جاري تحضير المثبّت...", Toast.LENGTH_LONG).show()
                    val uriString = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                    uriString?.let {
                        val file = File(Uri.parse(it).path ?: "")
                        if (file.exists()) installApk(file)
                    }
                }
                DownloadManager.STATUS_FAILED -> {
                    Toast.makeText(this, "فشل التحميل، يرجى التحقق من اتصال الإنترنت", Toast.LENGTH_LONG).show()
                }
            }
            cursor.close()
        }
    }

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
            Toast.makeText(this, "خطأ في فتح ملف APK: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshData() {
        // رابط المستودع الخاص بك
        val repoUrl = "Abdullah-AlTamimi/FixEngine/main/apps.json"
        viewModel.loadApps(repoUrl)
    }

    override fun onDestroy() {
        super.onDestroy()
        // إلغاء التسجيل لتجنب تسريب الذاكرة (Memory Leak)
        try {
            unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            // تجاهل الخطأ إذا لم يكن مسجلاً
        }
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
