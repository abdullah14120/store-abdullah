package com.fix.engine.abdullah.ui.details

import android.content.Intent
import android.net.Uri
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

        val appData = try {
            @Suppress("DEPRECATION")
            intent.getSerializableExtra("APP_DATA") as? AppModel
        } catch (e: Exception) {
            null
        }

        if (appData != null) {
            displayDetails(appData)
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

    private fun displayDetails(app: AppModel) {
        binding.apply {
            txtDetailsName.text = app.name
            txtDetailsDev.text = app.developer
            txtDescription.text = app.description ?: "لا يوجد وصف متاح لهذا التطبيق."
            
            // تحديث حالة الزر بناءً على وجود الملف مسبقاً
            val uniqueFileName = "${app.packageName}_v${app.versionCode}.apk"
            val localFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), uniqueFileName)
            
            if (localFile.exists()) {
                btnInstallLarge.text = "تثبيت الملف الآن"
            }

            try {
                badgeVersion.root.findViewById<TextView>(R.id.txtValue)?.text = app.versionName
                badgeVersion.root.findViewById<TextView>(R.id.txtLabel)?.text = "الإصدار"
                
                badgeSize.root.findViewById<TextView>(R.id.txtValue)?.text = app.getFormattedSize()
                badgeSize.root.findViewById<TextView>(R.id.txtLabel)?.text = "الحجم"
            } catch (e: Exception) {
                e.printStackTrace()
            }

            Glide.with(this@AppDetailsActivity)
                .load(app.iconUrl)
                .placeholder(R.drawable.ic_launcher_foreground)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(imgDetailsIcon)

            btnInstallLarge.setOnClickListener {
                if (localFile.exists()) {
                    installApk(localFile)
                } else if (!isDownloading) {
                    startDownloadProcess(app, uniqueFileName)
                }
            }
        }
    }

    private fun startDownloadProcess(app: AppModel, fileName: String) {
        isDownloading = true
        binding.btnInstallLarge.visibility = View.GONE
        binding.layoutDownloadProgress.visibility = View.VISIBLE

        val downloadId = downloadManager.enqueueDownload(app.downloadUrl, fileName)

        tracker.startTracking(downloadId) { progress, sizeLabel ->
            runOnUiThread {
                binding.apply {
                    downloadProgress.progress = progress
                    txtDownloadPercent.text = "$progress%"
                    txtDownloadSpeed.text = "جاري التحميل..." 
                    txtDownloadETA.text = sizeLabel
                    
                    if (progress >= 100) {
                        isDownloading = false
                        binding.btnInstallLarge.visibility = View.VISIBLE
                        binding.layoutDownloadProgress.visibility = View.GONE
                        binding.btnInstallLarge.text = "تثبيت الآن"
                    }
                }
            }
        }
    }

    private fun installApk(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ في فتح ملف التثبيت", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::tracker.isInitialized) {
            tracker.stopTracking()
        }
    }
}
