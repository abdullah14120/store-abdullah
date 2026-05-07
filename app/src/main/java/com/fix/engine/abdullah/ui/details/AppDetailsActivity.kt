package com.fix.engine.abdullah.ui.details

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.fix.engine.abdullah.R
import com.fix.engine.abdullah.data.model.AppModel
import com.fix.engine.abdullah.databinding.ActivityAppDetailsBinding
import com.fix.engine.abdullah.service.AndroidDownloadManager
import com.fix.engine.abdullah.service.DownloadTracker

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE
 * Engine: Official Android DownloadManager Integration
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

        // إعداد المحركات الجديدة
        downloadManager = AndroidDownloadManager(this)
        tracker = DownloadTracker(this)

        setupToolbar()

        // استقبال البيانات مع دعم الإصدارات الحديثة
        val appData = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("APP_DATA", AppModel::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("APP_DATA") as? AppModel
        }

        appData?.let { displayDetails(it) }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarDetails)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""
        binding.toolbarDetails.setNavigationOnClickListener { finish() }
    }

    private fun displayDetails(app: AppModel) {
        binding.apply {
            txtDetailsName.text = app.name
            txtDetailsDev.text = app.developer
            txtDescription.text = app.description ?: "لا يوجد وصف متاح لهذا التطبيق."
            
            // ربط البيانات بالـ Badges
            badgeVersion.root.findViewById<TextView>(R.id.txtValue).text = app.versionName
            badgeSize.root.findViewById<TextView>(R.id.txtValue).text = app.getFormattedSize()

            Glide.with(this@AppDetailsActivity)
                .load(app.iconUrl)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(imgDetailsIcon)

            btnInstallLarge.setOnClickListener {
                if (!isDownloading) {
                    startDownloadProcess(app)
                }
            }
        }
    }

    private fun startDownloadProcess(app: AppModel) {
        isDownloading = true
        
        // تغيير الواجهة
        binding.btnInstallLarge.visibility = View.GONE
        binding.layoutDownloadProgress.visibility = View.VISIBLE

        // بدء التحميل عبر نظام أندرويد
        val fileName = "${app.packageName}.apk"
        val downloadId = downloadManager.enqueueDownload(app.downloadUrl, fileName)

        // بدء متابعة النسبة المئوية
        tracker.startTracking(downloadId) { progress, sizeLabel ->
            runOnUiThread {
                binding.apply {
                    downloadProgress.progress = progress
                    txtDownloadPercent.text = "$progress%"
                    txtDownloadSpeed.text = "جاري التحميل..." 
                    txtDownloadETA.text = sizeLabel
                    
                    if (progress >= 100) {
                        resetUI()
                    }
                }
            }
        }
    }

    private fun resetUI() {
        isDownloading = false
        binding.btnInstallLarge.visibility = View.VISIBLE
        binding.layoutDownloadProgress.visibility = View.GONE
        binding.btnInstallLarge.text = "فتح الملف"
    }

    override fun onDestroy() {
        super.onDestroy()
        // إيقاف التتبع عند الخروج لمنع استهلاك البطارية
        if (::tracker.isInitialized) {
            tracker.stopTracking()
        }
    }
}
