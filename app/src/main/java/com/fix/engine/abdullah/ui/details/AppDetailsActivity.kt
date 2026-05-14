package com.fix.engine.abdullah.ui.details

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.fix.engine.abdullah.R
import com.fix.engine.abdullah.data.model.AppModel
import com.fix.engine.abdullah.databinding.ActivityAppDetailsBinding
import com.fix.engine.abdullah.service.AndroidDownloadManager
import com.fix.engine.abdullah.service.DownloadTracker
import java.io.File

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Abdullah Store
 * Feature: Smart Logic (Download, Update, Install, Open)
 */
class AppDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppDetailsBinding
    private lateinit var downloadManager: AndroidDownloadManager
    private lateinit var tracker: DownloadTracker
    private var isDownloading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        downloadManager = AndroidDownloadManager(this)
        tracker = DownloadTracker(this)

        setupToolbar()

        val appData = intent.getSerializableExtra("APP_DATA") as? AppModel

        if (appData != null) {
            displayDetails(appData)
            // تحديد حالة الزر بناءً على ذكاء المتجر
            setupLogic(appData)
            // فحص حالة التحميل الجاري فور الدخول لمنع تداخل الواجهات
            checkCurrentStatus(appData)
        } else {
            Toast.makeText(this, "بيانات التطبيق غير صالحة", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarDetails)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""
        binding.toolbarDetails.setNavigationOnClickListener { finish() }
    }

    private fun setupLogic(app: AppModel) {
        val pm = packageManager
        val fileName = app.getUniqueFileName()
        val downloadFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val finalFile = File(downloadFolder, fileName)

        // 1. الحالة الأولى: إذا كان ملف الـ APK محمل مسبقاً في التنزيلات
        if (finalFile.exists()) {
            binding.btnInstallLarge.text = "تثبيت الآن"
            binding.btnInstallLarge.visibility = View.VISIBLE
            binding.btnOpenApp.visibility = View.GONE
            binding.btnInstallLarge.setOnClickListener { installApk(finalFile) }
            return
        }

        try {
            // 2. الحالة الثانية: فحص هل التطبيق مثبت للمقارنة بين الإصدارات
            val pInfo = pm.getPackageInfo(app.packageName, 0)
            val installedVer = pInfo.versionName ?: ""

            if (app.versionName.trim() != installedVer.trim()) {
                // توفر تحديث جديد
                binding.btnInstallLarge.text = "تحديث الآن"
                binding.btnInstallLarge.visibility = View.VISIBLE
                binding.btnOpenApp.visibility = View.GONE
                binding.btnInstallLarge.setOnClickListener { startNewDownload(app) }
            } else {
                // التطبيق مثبت ومحدث بالكامل
                binding.btnInstallLarge.visibility = View.GONE
                binding.btnOpenApp.visibility = View.VISIBLE
                binding.btnOpenApp.setOnClickListener { 
                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    launchIntent?.let { startActivity(it) }
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            // 3. الحالة الثالثة: التطبيق غير موجود نهائياً على الجهاز
            binding.btnInstallLarge.text = "تنزيل الآن"
            binding.btnInstallLarge.visibility = View.VISIBLE
            binding.btnOpenApp.visibility = View.GONE
            binding.btnInstallLarge.setOnClickListener { startNewDownload(app) }
        }
    }

    @SuppressLint("Range")
    private fun checkCurrentStatus(app: AppModel) {
        val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterByStatus(
            DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_PENDING or DownloadManager.STATUS_PAUSED
        )
        val cursor = manager.query(query)
        
        var activeDownloadId: Long = -1

        if (cursor != null) {
            while (cursor.moveToNext()) {
                val title = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_TITLE))
                if (title == app.name || title == app.getUniqueFileName()) {
                    activeDownloadId = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_ID))
                    break
                }
            }
            cursor.close()
        }

        if (activeDownloadId != -1L) {
            isDownloading = true
            binding.btnInstallLarge.visibility = View.GONE
            binding.btnOpenApp.visibility = View.GONE
            binding.layoutDownloadProgress.visibility = View.VISIBLE
            startTrackingProcess(activeDownloadId)
        }
    }

    private fun displayDetails(app: AppModel) {
        binding.apply {
            txtDetailsName.text = app.name
            txtDetailsDev.text = app.developer
            txtDescription.text = app.description ?: "لا يوجد وصف متاح لهذا التطبيق."
            
            badgeVersion.root.findViewById<TextView>(R.id.txtLabel)?.text = "الإصدار"
            badgeVersion.root.findViewById<TextView>(R.id.txtValue)?.text = app.versionName
            
            badgeSize.root.findViewById<TextView>(R.id.txtLabel)?.text = "الحجم"
            badgeSize.root.findViewById<TextView>(R.id.txtValue)?.text = app.getFormattedSize()

            Glide.with(this@AppDetailsActivity)
                .load(app.iconUrl)
                .placeholder(R.drawable.ic_launcher_foreground)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(imgDetailsIcon)
        }
    }

    private fun startNewDownload(app: AppModel) {
        isDownloading = true
        binding.btnInstallLarge.visibility = View.GONE
        binding.btnOpenApp.visibility = View.GONE
        binding.layoutDownloadProgress.visibility = View.VISIBLE

        val downloadId = downloadManager.enqueueDownload(app.downloadUrl, app.getUniqueFileName())
        startTrackingProcess(downloadId)
    }

    private fun startTrackingProcess(downloadId: Long) {
        tracker.startTracking(downloadId) { progress, sizeLabel ->
            runOnUiThread {
                binding.apply {
                    downloadProgress.progress = progress
                    txtDownloadPercent.text = "$progress%"
                    txtDownloadETA.text = sizeLabel
                    txtDownloadSpeed.text = if (progress < 100) "جاري التحميل..." else "اكتمل التحميل"
                    
                    if (progress >= 100) {
                        isDownloading = false
                        layoutDownloadProgress.visibility = View.GONE
                        btnInstallLarge.visibility = View.VISIBLE
                        btnInstallLarge.text = "تثبيت الآن"
                        // إعادة ربط الزر بملف التثبيت بعد اكتمال التحميل
                        val fileName = app.getUniqueFileName() // نحتاج للوصول لبيانات التطبيق هنا
                        // ملاحظة: يفضل تحديث الواجهة بالكامل عند اكتمال التحميل
                    }
                }
            }
        }
    }

    private fun installApk(file: File) {
        if (!file.exists()) {
            Toast.makeText(this, "المعذرة، الملف غير موجود!", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "فشل تحليل الحزمة، قد يكون الملف غير مكتمل", Toast.LENGTH_LONG).show()
            if (file.exists()) file.delete()
            recreate()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::tracker.isInitialized) tracker.stopTracking()
    }
}
