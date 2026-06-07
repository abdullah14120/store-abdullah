package com.fix.engine.abdullah.ui.details

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.fix.engine.abdullah.R
import com.fix.engine.abdullah.data.model.AppModel
import com.fix.engine.abdullah.databinding.ActivityAppDetailsBinding
import com.fix.engine.abdullah.service.AndroidDownloadManager
import com.fix.engine.abdullah.service.DownloadTracker
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.tonyodev.fetch2.Fetch
import com.tonyodev.fetch2.Status
import java.io.File
import kotlin.concurrent.thread

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Abdullah Store
 * Refactored: Material 3 UI Integration with Fetch API & Smart States
 */
class AppDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppDetailsBinding
    private lateinit var downloadManager: AndroidDownloadManager
    private lateinit var tracker: DownloadTracker
    private var isDownloading = false
    private var currentApp: AppModel? = null
    
    private lateinit var databaseRef: DatabaseReference

    // 🔒 رابط الـ Firebase Realtime Database مشفر بترميز Base64
    private val firebaseSecUrlBase64 = "aHR0cHM6Ly9hYmR1bGxhaC1zdG9yZS1hOTVlZC1kZWZhdWx0LXJ0ZGIuZXVyb3BlLXdlc3QxLmZpcmViYXNlZGF0YWJhc2UuYXBw"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.google.firebase.FirebaseApp.initializeApp(this)
        binding = ActivityAppDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        downloadManager = AndroidDownloadManager(this)
        tracker = DownloadTracker(this)

        setupToolbar()

        try {
            val decodedBytes = android.util.Base64.decode(firebaseSecUrlBase64, android.util.Base64.DEFAULT)
            val decodedFirebaseUrl = String(decodedBytes, Charsets.UTF_8)
            
            databaseRef = FirebaseDatabase.getInstance(decodedFirebaseUrl).getReference("download_stats")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "خطأ في الاتصال الأمني بقاعدة البيانات", Toast.LENGTH_SHORT).show()
        }

        // 🚀 استقبال البيانات كـ Parcelable فائق السرعة
        val appData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("APP_DATA", AppModel::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("APP_DATA") as? AppModel
        }

        if (appData != null) {
            currentApp = appData
            
            displayDetails(appData)
            
            if (::databaseRef.isInitialized) {
                loadDownloadsCount(appData.packageName)
            }
            
            setupLogic(appData)
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
        
        val downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val finalFile = File(downloadFolder, fileName)

        // 1. حالة وجود ملف التثبيت مسبقاً
        if (finalFile.exists()) {
            binding.btnInstallState.text = "تثبيت"
            binding.btnInstallState.visibility = View.VISIBLE
            binding.layoutInstalledState.visibility = View.GONE
            binding.cardDownloadingState.visibility = View.GONE
            binding.btnInstallState.setOnClickListener { installApkLegacy(finalFile) }
            return
        }

        try {
            val pInfo = pm.getPackageInfo(app.packageName, 0)
            val installedVer = pInfo.versionName ?: ""

            // 2. حالة التحديث (مثبت بإصدار قديم)
            if (app.versionName.trim() != installedVer.trim()) {
                binding.btnInstallState.text = "تحديث"
                binding.btnInstallState.visibility = View.VISIBLE
                binding.layoutInstalledState.visibility = View.GONE
                binding.btnInstallState.setOnClickListener { checkStoragePermissionAndDownload(app) }
            } else {
                // 3. حالة تم التثبيت والتحديث (الإصدار متطابق)
                binding.btnInstallState.visibility = View.GONE
                binding.layoutInstalledState.visibility = View.VISIBLE
                
                // زر فتح التطبيق
                binding.btnOpenApp.setOnClickListener { 
                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    launchIntent?.let { startActivity(it) } ?: Toast.makeText(this, "لا يمكن فتح التطبيق", Toast.LENGTH_SHORT).show()
                }
                
                // زر إلغاء التثبيت
                binding.btnUninstall.setOnClickListener {
                    val intent = Intent(Intent.ACTION_DELETE)
                    intent.data = Uri.parse("package:${app.packageName}")
                    startActivity(intent)
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            // 4. حالة التطبيق غير مثبت نهائياً
            binding.btnInstallState.text = "تثبيت"
            binding.btnInstallState.visibility = View.VISIBLE
            binding.layoutInstalledState.visibility = View.GONE
            binding.btnInstallState.setOnClickListener { checkStoragePermissionAndDownload(app) }
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
        val fileName = app.getUniqueFileName()
        val tempFileName = "$fileName.tmp"
        val downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val tempFile = File(downloadFolder, tempFileName)

        val fetch = Fetch.Impl.getDefaultInstance()
        fetch.getDownloads { downloads ->
            val activeDownload = downloads.find { 
                it.file == tempFile.absolutePath && 
                (it.status == Status.DOWNLOADING || it.status == Status.QUEUED || it.status == Status.PAUSED) 
            }
            
            if (activeDownload != null) {
                runOnUiThread {
                    isDownloading = true
                    binding.btnInstallState.visibility = View.GONE
                    binding.layoutInstalledState.visibility = View.GONE
                    binding.cardDownloadingState.visibility = View.VISIBLE
                    startTrackingProcess(activeDownload.id)
                }
            }
        }
    }

    private fun displayDetails(app: AppModel) {
        binding.apply {
            txtDetailsName.text = app.name
            txtDetailsDev.text = app.developer
            txtDescription.text = app.description ?: "لا يوجد وصف متاح لهذا التطبيق."
            
            // ربط الحقول الجديدة للتصميم (Material 3)
            txtStatVersion.text = app.versionName
            txtStatSize.text = app.getFormattedSize()
            txtStatDownloads.text = "..."

            Glide.with(this@AppDetailsActivity)
                .load(app.iconUrl)
                .placeholder(R.mipmap.ic_launcher) 
                .error(R.mipmap.ic_launcher)       
                .dontAnimate() 
                .into(imgDetailsIcon)
        }
    }

    private fun loadDownloadsCount(packageName: String) {
        val safeKey = packageName.trim().lowercase().replace(".", "_")
        
        thread {
            if (!::databaseRef.isInitialized) return@thread
            databaseRef.child(safeKey).addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val count = if (snapshot.exists()) snapshot.getValue(Long::class.java) ?: 0L else 0L
                    runOnUiThread {
                        binding.txtStatDownloads.text = formatDownloads(count)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    runOnUiThread {
                        binding.txtStatDownloads.text = "0"
                    }
                }
            })
        }
    }

    private fun incrementDownloadCount(packageName: String) {
        val safeKey = packageName.trim().lowercase().replace(".", "_")
        
        thread {
            if (!::databaseRef.isInitialized) return@thread
            databaseRef.child(safeKey).setValue(com.google.firebase.database.ServerValue.increment(1))
                .addOnFailureListener { e -> e.printStackTrace() }
        }
    }

    private fun formatDownloads(count: Long): String {
        return when {
            count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }

    private fun startNewDownload(app: AppModel) {
        isDownloading = true
        
        // التحويل البصري لشكل التحميل
        binding.btnInstallState.visibility = View.GONE
        binding.layoutInstalledState.visibility = View.GONE
        binding.cardDownloadingState.visibility = View.VISIBLE

        incrementDownloadCount(app.packageName)

        val downloadId: Int = downloadManager.enqueueDownload(app.downloadUrl, app.getUniqueFileName())
        startTrackingProcess(downloadId)
    }

    private fun startTrackingProcess(downloadId: Int) {
        // زر الإلغاء الجديد (X)
        binding.btnCancelDownload.setOnClickListener {
            Fetch.Impl.getDefaultInstance().cancel(downloadId)
            tracker.stopTracking()
            isDownloading = false
            
            // إعادة الواجهة للوضع الأساسي
            binding.cardDownloadingState.visibility = View.GONE
            binding.btnInstallState.visibility = View.VISIBLE
            binding.btnInstallState.text = "تثبيت"
            Toast.makeText(this, "تم إلغاء التنزيل", Toast.LENGTH_SHORT).show()
        }

        tracker.startTracking(downloadId) { progress, sizeLabel ->
            runOnUiThread {
                binding.apply {
                    progressDownload.progress = progress
                    tvDownloadPercent.text = "جاري التنزيل... $progress%"
                    tvDownloadSize.text = sizeLabel
                    
                    if (progress >= 100) {
                        isDownloading = false
                        
                        // إعادة الواجهة لوضع التثبيت في حال أراد المستخدم المحاولة مجدداً
                        cardDownloadingState.visibility = View.GONE
                        btnInstallState.visibility = View.VISIBLE
                        btnInstallState.text = "تثبيت"
                    }
                }
            }
        }
    }

    private fun installApkLegacy(file: File) {
        if (!file.exists()) {
            Toast.makeText(this, "المعذرة، ملف الـ APK غير موجود!", Toast.LENGTH_SHORT).show()
            return
        }

        // إظهار بطاقة التحميل مؤقتاً كواجهة تجهيز التثبيت
        runOnUiThread {
            binding.btnInstallState.visibility = View.GONE
            binding.cardDownloadingState.visibility = View.VISIBLE
            binding.progressDownload.progress = 100
            binding.tvDownloadPercent.text = "تحضير التثبيت..."
            binding.tvDownloadSize.text = "يرجى الانتظار"
        }

        thread {
            try {
                Thread.sleep(800) // تأثير بصري سلس
                
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val providerAuthority = "$packageName.fileprovider" 
                        val uri: Uri = FileProvider.getUriForFile(this@AppDetailsActivity, providerAuthority, file)
                        
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } else {
                        val uri: Uri = Uri.fromFile(file)
                        setDataAndType(uri, "application/vnd.android.package-archive")
                    }
                }
                startActivity(intent)

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@AppDetailsActivity, "فشل فتح الحزمة", Toast.LENGTH_LONG).show()
                }
            } finally {
                runOnUiThread {
                    binding.cardDownloadingState.visibility = View.GONE
                    binding.btnInstallState.visibility = View.VISIBLE
                    binding.btnInstallState.text = "تثبيت"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::tracker.isInitialized) tracker.stopTracking()
    }
}
