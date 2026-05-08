package com.fix.engine.abdullah.ui.details

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

        // الطريقة الأكثر أماناً لاستقبال البيانات وتجنب الـ Crash
        val appData = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                // تعديل بسيط هنا: استخدام getSerializableExtra التقليدي مع cast آمن
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("APP_DATA") as? AppModel
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("APP_DATA") as? AppModel
            }
        } catch (e: Exception) {
            null
        }

        if (appData != null) {
            displayDetails(appData)
        } else {
            Toast.makeText(this, "بيانات التطبيق غير صالحة", Toast.LENGTH_SHORT).show()
            finish() // إغلاق الصفحة بأمان بدل الانهيار
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
            
            // حماية الوصول للـ Badges لمنع NullPointerException
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
        binding.btnInstallLarge.visibility = View.GONE
        binding.layoutDownloadProgress.visibility = View.VISIBLE

        val fileName = "${app.packageName}.apk"
        val downloadId = downloadManager.enqueueDownload(app.downloadUrl, fileName)

        tracker.startTracking(downloadId) { progress, sizeLabel ->
            runOnUiThread {
                binding.apply {
                    downloadProgress.progress = progress
                    txtDownloadPercent.text = "$progress%"
                    txtDownloadSpeed.text = "جاري التحميل..." 
                    txtDownloadETA.text = sizeLabel
                    
                    if (progress >= 100) {
                        resetUI()
                        txtDownloadSpeed.text = "اكتمل التحميل"
                    }
                }
            }
        }
    }

    private fun resetUI() {
        isDownloading = false
        binding.btnInstallLarge.visibility = View.VISIBLE
        binding.layoutDownloadProgress.visibility = View.GONE
        binding.btnInstallLarge.text = "تثبيت الملف"
    }

    override fun onDestroy() {
        super.onDestroy()
        // منع الـ Crash إذا لم يتم تهيئة الـ tracker بشكل صحيح
        if (::tracker.isInitialized) {
            tracker.stopTracking()
        }
    }
}
