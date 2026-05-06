package com.fix.engine.abdullah.ui.details

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.fix.engine.abdullah.data.model.AppModel
import com.fix.engine.abdullah.databinding.ActivityAppDetailsBinding
import com.tonyodev.fetch2.*
import com.tonyodev.fetch2core.DownloadBlock
import java.io.File

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE
 * Feature: Advanced In-App Downloader & Installer
 */
class AppDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppDetailsBinding
    private lateinit var fetch: Fetch
    private var currentDownloadId: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        initFetch()

        val appData = intent.getSerializableExtra("APP_DATA") as? AppModel
        appData?.let { displayDetails(it) }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbarDetails)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "" // نترك العنوان فارغاً للأناقة
        binding.toolbarDetails.setNavigationOnClickListener { finish() }
    }

    private fun initFetch() {
        val fetchConfiguration = FetchConfiguration.Builder(this)
            .setDownloadConcurrentLimit(3) // تحميل متعدد المسارات لزيادة السرعة
            .build()
        fetch = Fetch.Impl.getInstance(fetchConfiguration)
    }

    private fun displayDetails(app: AppModel) {
        binding.apply {
            txtDetailsName.text = app.name
            txtDetailsDev.text = app.developer
            txtDescription.text = app.description ?: "لا يوجد وصف متاح لهذا التطبيق."
            
            // تحديث بيانات الإصدار والحجم من الموديل
            // ملاحظة: تأكد أن layout_info_badge يحتوي على هذه المعرفات
            badgeVersion.root.findViewById<android.widget.TextView>(com.fix.engine.abdullah.R.id.txtValue).text = app.versionName
            badgeSize.root.findViewById<android.widget.TextView>(com.fix.engine.abdullah.R.id.txtValue).text = app.getFormattedSize()

            Glide.with(this@AppDetailsActivity)
                .load(app.iconUrl)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(imgDetailsIcon)

            btnInstallLarge.setOnClickListener {
                startDownload(app)
            }
        }
    }

    private fun startDownload(app: AppModel) {
        // التحويل البصري من الزر إلى شريط التقدم
        binding.btnInstallLarge.visibility = View.GONE
        binding.layoutDownloadProgress.visibility = View.VISIBLE

        val fileName = "${app.packageName}_v${app.versionCode}.apk"
        val filePath = File(getExternalFilesDir(null), "downloads/$fileName").absolutePath

        val request = Request(app.downloadUrl, filePath).apply {
            priority = Priority.HIGH
            networkType = NetworkType.ALL
        }

        fetch.enqueue(request, { req ->
            currentDownloadId = req.id
        }, { error ->
            Toast.makeText(this, "خطأ: ${error.name}", Toast.LENGTH_SHORT).show()
            resetUI()
        })

        fetch.addListener(fetchListener)
    }

    private val fetchListener = object : AbstractFetchListener() {
        override fun onProgress(download: Download, etaInMilliSeconds: Long, downloadedBytesPerSecond: Long) {
            if (download.id == currentDownloadId) {
                runOnUiThread {
                    binding.apply {
                        downloadProgress.progress = download.progress
                        txtDownloadPercent.text = "${download.progress}%"
                        txtDownloadSpeed.text = formatSpeed(downloadedBytesPerSecond)
                        txtDownloadETA.text = "متبقي: ${formatEta(etaInMilliSeconds)}"
                    }
                }
            }
        }

        override fun onCompleted(download: Download) {
            runOnUiThread {
                resetUI()
                installApk(File(download.file))
            }
        }

        override fun onError(download: Download, error: Error, throwable: Throwable?) {
            runOnUiThread {
                resetUI()
                Toast.makeText(this@AppDetailsActivity, "فشل التحميل: ${error.name}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun resetUI() {
        binding.btnInstallLarge.visibility = View.VISIBLE
        binding.layoutDownloadProgress.visibility = View.GONE
    }

    private fun formatSpeed(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1) String.format("%.1f MB/s", mb) else String.format("%.0f KB/s", kb)
    }

    private fun formatEta(milli: Long): String {
        val seconds = (milli / 1000) % 60
        val minutes = (milli / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "فشل فتح ملف التثبيت", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fetch.removeListener(fetchListener)
        fetch.close()
    }
}
