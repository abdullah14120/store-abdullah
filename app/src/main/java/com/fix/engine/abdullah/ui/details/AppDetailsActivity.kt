package com.fix.engine.abdullah.ui.details

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
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
 * Feature: Smart APK Detection with Temp File Support (.tmp)
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
                // التحقق من العنوان لربط العملية الجارية بالواجهة
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

            // المنطق المحدث لفحص الملفات
            btnInstallLarge.setOnClickListener {
                val fileName = app.getUniqueFileName()
                // نبحث فقط عن الملف النهائي (.apk)
                val finalFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)
                // نتحقق من وجود الملف المؤقت (.tmp)
                val tempFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$fileName.tmp")

                if (finalFile.exists() && !isDownloading) {
                    installApk(finalFile)
                } else if (tempFile.exists() || isDownloading) {
                    // إذا كان الملف المؤقت موجوداً، نمنع بدء تحميل جديد وننبه المستخدم
                    Toast.makeText(this@AppDetailsActivity, "التحميل مستمر في الخلفية...", Toast.LENGTH_SHORT).show()
                } else {
                    startNewDownload(app)
                }
            }
            
            // تحديث نص الزر عند الدخول إذا كان الملف مكتملاً تماماً
            val checkFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), app.getUniqueFileName())
            if (checkFile.exists() && !isDownloading) {
                btnInstallLarge.text = "تثبيت الآن"
            }
        }
    }

    private fun startNewDownload(app: AppModel) {
        isDownloading = true
        binding.btnInstallLarge.visibility = View.GONE
        binding.layoutDownloadProgress.visibility = View.VISIBLE

        // استدعاء المانجر الذي يضيف لاحقة .tmp تلقائياً
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
                        // ملاحظة: لا نظهر زر التثبيت هنا فوراً لأن الـ Receiver يحتاج أجزاء من الثانية لتغيير اسم الملف
                        // سيتغير الزر تلقائياً عند إعادة الدخول أو عبر بث محلي (LocalBroadcast) إذا أردت تطويره لاحقاً
                        layoutDownloadProgress.visibility = View.GONE
                        btnInstallLarge.visibility = View.VISIBLE
                        btnInstallLarge.text = "تثبيت الآن"
                    }
                }
            }
        }
    }

    private fun installApk(file: File) {
    if (!file.exists()) {
        Toast.makeText(this, "الملف غير موجود!", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        // إنشاء الـ Intent أولاً
        val intent = Intent(Intent.ACTION_VIEW)
        
        // تحويل الملف إلى URI آمن باستخدام FileProvider
        val uri = FileProvider.getUriForFile(
            this, 
            "$packageName.provider", 
            file
        )

        // تحديد نوع البيانات (APK) وربط الـ URI
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        
        // الأعلام (Flags) هما سر النجاح هنا:
        // 1. منح إذن القراءة للـ URI (إلزامي لأندرويد 7 فما فوق)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // 2. تشغيل التثبيت في مهمة جديدة لضمان عدم تأثر التطبيق
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // 3. منع الاحتفاظ بالـ Intent في السجل (اختياري لزيادة الأمان)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

        startActivity(intent)
        
    } catch (e: Exception) {
        // في حال فشل التحليل، غالباً ما يكون الملف ناقصاً أو غير مكتمل
        Toast.makeText(this, "حدث خطأ في تحليل الحزمة، جرب إعادة التحميل", Toast.LENGTH_LONG).show()
        
        // إذا أردت حذف الملف التالف ليتيح للمستخدم التحميل مجدداً
        if (file.exists()) {
            file.delete()
        }
        
        // إعادة إنعاش الواجهة لتحديث حالة الأزرار
        recreate()
    }
}


    override fun onDestroy() {
        super.onDestroy()
        if (::tracker.isInitialized) tracker.stopTracking()
    }
}
