package com.fix.engine.abdullah.ui.details

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.fix.engine.abdullah.R
import com.fix.engine.abdullah.data.model.AppModel
import com.fix.engine.abdullah.databinding.ActivityAppDetailsBinding
import com.fix.engine.abdullah.installer.SessionInstaller
import com.fix.engine.abdullah.service.AndroidDownloadManager
import com.fix.engine.abdullah.service.DownloadTracker
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.io.File
import kotlin.concurrent.thread

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Abdullah Store
 * Feature: Background SessionInstaller & Live Firebase Download Statistics
 */
class AppDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppDetailsBinding
    private lateinit var downloadManager: AndroidDownloadManager
    private lateinit var tracker: DownloadTracker
    private lateinit var sessionInstaller: SessionInstaller
    private var isDownloading = false
    private var currentApp: AppModel? = null
    
    // تهيئة مرجع قاعدة بيانات الفايربيس
    private val databaseRef = FirebaseDatabase.getInstance().getReference("download_stats")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        downloadManager = AndroidDownloadManager(this)
        tracker = DownloadTracker(this)
        sessionInstaller = SessionInstaller(this)

        setupToolbar()

        val appData = intent.getSerializableExtra("APP_DATA") as? AppModel

        if (appData != null) {
            currentApp = appData
            displayDetails(appData)
            setupLogic(appData)
            checkCurrentStatus(appData)
            loadDownloadsCount(appData.packageName) // جلب الإحصائية فور فتح الصفحة
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

        if (finalFile.exists()) {
            binding.btnInstallLarge.text = "تثبيت الآن"
            binding.btnInstallLarge.visibility = View.VISIBLE
            binding.btnOpenApp.visibility = View.GONE
            binding.btnInstallLarge.setOnClickListener { installApkWithSession(finalFile, app.packageName) }
            return
        }

        try {
            val pInfo = pm.getPackageInfo(app.packageName, 0)
            val installedVer = pInfo.versionName ?: ""

            if (app.versionName.trim() != installedVer.trim()) {
                binding.btnInstallLarge.text = "تحديث الآن"
                binding.btnInstallLarge.visibility = View.VISIBLE
                binding.btnOpenApp.visibility = View.GONE
                binding.btnInstallLarge.setOnClickListener { checkStoragePermissionAndDownload(app) }
            } else {
                binding.btnInstallLarge.visibility = View.GONE
                binding.btnOpenApp.visibility = View.VISIBLE
                binding.btnOpenApp.setOnClickListener { 
                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    launchIntent?.let { startActivity(it) }
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            binding.btnInstallLarge.text = "تنزيل الآن"
            binding.btnInstallLarge.visibility = View.VISIBLE
            binding.btnOpenApp.visibility = View.GONE
            binding.btnInstallLarge.setOnClickListener { checkStoragePermissionAndDownload(app) }
        }
    }

    private fun checkStoragePermissionAndDownload(app: AppModel) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startNewDownload(app)
        } else {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                startNewDownload(app)
            } else {
                requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 101)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            currentApp?.let { startNewDownload(it) }
        } else {
            Toast.makeText(this, "عذراً، يجب السماح بصلاحية التخزين لبدء تحميل التطبيق", Toast.LENGTH_LONG).show()
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

            // تهيئة الكبسولة الجديدة للتنزيلات بشكل افتراضي مؤقت لحين اكتمال استعلام الفايربيس
            badgeDownloads.root.findViewById<TextView>(R.id.txtLabel)?.text = "التنزيلات"
            badgeDownloads.root.findViewById<TextView>(R.id.txtValue)?.text = "..."

            Glide.with(this@AppDetailsActivity)
                .load(app.iconUrl)
                .placeholder(R.drawable.ic_launcher_foreground)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(imgDetailsIcon)
        }
    }

    /**
     * دالة للاستماع لعدد التنزيلات الحقيقي من Firebase وعرضه بشكل منسق
     */
    private fun loadDownloadsCount(packageName: String) {
        // تحويل النقاط في اسم الحزمة إلى شرطة تحتية لأن الفايربيس يمنع استخدام النقطة كمفتاح
        val safeKey = packageName.replace(".", "_")
        
        databaseRef.child(safeKey).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val count = snapshot.getValue(Long::class.java) ?: 0
                runOnUiThread {
                    binding.badgeDownloads.root.findViewById<TextView>(R.id.txtValue)?.text = formatDownloads(count)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    /**
     * زيادة العداد بمقدار +1 في Firebase عند نقر الزر الفعلي لبدء التحميل
     */
    private fun incrementDownloadCount(packageName: String) {
        val safeKey = packageName.replace(".", "_")
        databaseRef.child(safeKey).setValue(com.google.firebase.database.ServerValue.increment(1))
    }

    /**
     * دالة لتنسيق الأرقام الضخمة بشكل رياضي ناعم وأنيق (مثال: 1500 يصبح 1.5K)
     */
    private fun formatDownloads(count: Long): String {
        return when {
            count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }

    private fun startNewDownload(app: AppModel) {
        isDownloading = true
        binding.btnInstallLarge.visibility = View.GONE
        binding.btnOpenApp.visibility = View.GONE
        binding.layoutDownloadProgress.visibility = View.VISIBLE

        // استدعاء دالة زيادة العداد فوراً في الفايربيس عند بدء التنزيل
        incrementDownloadCount(app.packageName)

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
                        
                        currentApp?.let { app ->
                            val finalFile = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), app.getUniqueFileName())
                            btnInstallLarge.setOnClickListener { installApkWithSession(finalFile, app.packageName) }
                        }
                    }
                }
            }
        }
    }

    private fun installApkWithSession(file: File, packageName: String) {
        if (!file.exists()) {
            Toast.makeText(this, "المعذرة، ملف الـ APK غير موجود!", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnInstallLarge.visibility = View.GONE
        binding.layoutDownloadProgress.visibility = View.VISIBLE
        binding.txtDownloadSpeed.text = "جاري تهيئة التثبيت الذكي..."
        binding.txtDownloadETA.text = "يرجى عدم إغلاق المتجر"

        thread {
            val success = sessionInstaller.installApk(file, packageName) { progress ->
                runOnUiThread {
                    binding.downloadProgress.progress = progress
                    binding.txtDownloadPercent.text = "$progress%"
                    binding.txtDownloadSpeed.text = "جاري حَقن ملفات التطبيق..."
                }
            }

            runOnUiThread {
                binding.layoutDownloadProgress.visibility = View.GONE
                binding.btnInstallLarge.visibility = View.VISIBLE
                binding.btnInstallLarge.text = "تثبيت الآن"

                if (!success) {
                    Toast.makeText(this, "فشل بدء جلسة التثبيت، قد يكون الملف معطوباً", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::tracker.isInitialized) tracker.stopTracking()
    }
}
