package com.fix.engine.abdullah.ui.details

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * Developed by: Abdullah Al-Tamimi
 * Refactored: Coroutine Threading, Firebase Leak Fix & Advanced PackageInstaller
 */
class AppDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppDetailsBinding
    private lateinit var downloadManager: AndroidDownloadManager
    private lateinit var tracker: DownloadTracker
    private var isDownloading = false
    private var currentApp: AppModel? = null
    
    private lateinit var databaseRef: DatabaseReference
    private var downloadsListener: ValueEventListener? = null // لمنع تسريب الذاكرة
    private var activeFirebaseKey: String? = null

    // TODO: يجب نقل هذا الرابط لاحقاً إلى C++ JNI كما فعلنا مع apps.json
    private val firebaseSecUrlBase64 = "aHR0cHM6Ly9hYmR1bGxhaC1zdG9yZS1hOTVlZC1kZWZhdWx0LXJ0ZGIuZXVyb3BlLXdlc3QxLmZpcmViYXNlZGF0YWJhc2UuYXBw"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.google.firebase.FirebaseApp.initializeApp(this)
        binding = ActivityAppDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        downloadManager = AndroidDownloadManager(this)
        tracker = DownloadTracker(this)

        setupToolbar()
        initFirebase()

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

    private fun initFirebase() {
        try {
            val decodedBytes = android.util.Base64.decode(firebaseSecUrlBase64, android.util.Base64.DEFAULT)
            val decodedFirebaseUrl = String(decodedBytes, Charsets.UTF_8)
            databaseRef = FirebaseDatabase.getInstance(decodedFirebaseUrl).getReference("download_stats")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "خطأ في الاتصال بقاعدة البيانات", Toast.LENGTH_SHORT).show()
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
        val tempFile = File(downloadFolder, "$fileName.tmp")

        if (finalFile.exists() || tempFile.exists()) {
            binding.btnDeleteApk.visibility = View.VISIBLE
            binding.btnDeleteApk.setOnClickListener { deleteAppFiles(app) }
        } else {
            binding.btnDeleteApk.visibility = View.GONE
        }

        if (finalFile.exists()) {
            binding.btnInstallState.text = "تثبيت"
            binding.btnInstallState.visibility = View.VISIBLE
            binding.layoutInstalledState.visibility = View.GONE
            binding.cardDownloadingState.visibility = View.GONE
            
            binding.btnShareApk.visibility = View.VISIBLE
            binding.btnShareApk.setOnClickListener { shareApkFile(finalFile, app.name) }
            
            // 🚀 استخدام محرك التثبيت المتقدم
            binding.btnInstallState.setOnClickListener { triggerAdvancedInstall(finalFile) }
            return
        } else {
            binding.btnShareApk.visibility = View.GONE
        }

        try {
            val pInfo = pm.getPackageInfo(app.packageName, 0)
            val installedVer = pInfo.versionName ?: ""

            if (app.versionName.trim() != installedVer.trim()) {
                binding.btnInstallState.text = "تحديث"
                binding.btnInstallState.visibility = View.VISIBLE
                binding.layoutInstalledState.visibility = View.GONE
                binding.btnInstallState.setOnClickListener { checkStoragePermissionAndDownload(app) }
            } else {
                binding.btnInstallState.visibility = View.GONE
                binding.layoutInstalledState.visibility = View.VISIBLE
                
                binding.btnOpenApp.setOnClickListener { 
                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    launchIntent?.let { startActivity(it) } ?: Toast.makeText(this, "لا يمكن فتح التطبيق", Toast.LENGTH_SHORT).show()
                }
                
                binding.btnUninstall.setOnClickListener {
                    val intent = Intent(Intent.ACTION_DELETE)
                    intent.data = Uri.parse("package:${app.packageName}")
                    startActivity(intent)
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
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

    @SuppressLint("Range")
    private fun checkCurrentStatus(app: AppModel) {
        val fileName = app.getUniqueFileName()
        val tempFileName = "$fileName.tmp"
        val downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val tempFile = File(downloadFolder, tempFileName)

        val fetch = Fetch.getDefaultInstance()
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
                    binding.btnShareApk.visibility = View.GONE
                    binding.btnDeleteApk.visibility = View.GONE
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

    // 🚀 تحديث لمنع تسريب الذاكرة باستخدام Coroutines وحفظ الـ Listener
    private fun loadDownloadsCount(packageName: String) {
        activeFirebaseKey = packageName.trim().lowercase().replace(".", "_")
        
        lifecycleScope.launch(Dispatchers.IO) {
            if (!::databaseRef.isInitialized) return@launch
            
            downloadsListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val count = if (snapshot.exists()) snapshot.getValue(Long::class.java) ?: 0L else 0L
                    lifecycleScope.launch(Dispatchers.Main) {
                        binding.txtStatDownloads.text = formatDownloads(count)
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        binding.txtStatDownloads.text = "0"
                    }
                }
            }
            databaseRef.child(activeFirebaseKey!!).addValueEventListener(downloadsListener!!)
        }
    }

    private fun incrementDownloadCount(packageName: String) {
        val safeKey = packageName.trim().lowercase().replace(".", "_")
        lifecycleScope.launch(Dispatchers.IO) {
            if (!::databaseRef.isInitialized) return@launch
            databaseRef.child(safeKey).setValue(com.google.firebase.database.ServerValue.increment(1))
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
        binding.btnInstallState.visibility = View.GONE
        binding.layoutInstalledState.visibility = View.GONE
        binding.btnShareApk.visibility = View.GONE
        binding.btnDeleteApk.visibility = View.GONE
        binding.cardDownloadingState.visibility = View.VISIBLE

        binding.progressDownload.isIndeterminate = true
        binding.tvDownloadPercent.text = "جاري الاتصال..."
        binding.tvDownloadSize.text = "جاري حساب الحجم"

        incrementDownloadCount(app.packageName)

        val downloadId: Int = downloadManager.enqueueDownload(app.downloadUrl, app.getUniqueFileName(), app.packageName)
        startTrackingProcess(downloadId)
    }

    private fun startTrackingProcess(downloadId: Int) {
        binding.btnCancelDownload.setOnClickListener {
            Fetch.getDefaultInstance().cancel(downloadId)
            tracker.stopTracking()
            isDownloading = false
            
            binding.cardDownloadingState.visibility = View.GONE
            binding.btnInstallState.visibility = View.VISIBLE
            binding.btnInstallState.text = "تثبيت"
            Toast.makeText(this, "تم إلغاء التنزيل", Toast.LENGTH_SHORT).show()
            
            currentApp?.let { setupLogic(it) }
        }

        tracker.startTracking(downloadId) { progress, sizeLabel ->
            runOnUiThread {
                binding.apply {
                    if (progress > 0) {
                        progressDownload.isIndeterminate = false
                        progressDownload.progress = progress
                        tvDownloadPercent.text = "جاري التنزيل... $progress%"
                    } else {
                        progressDownload.isIndeterminate = true
                        tvDownloadPercent.text = "جاري بدء التنزيل..."
                    }
                    
                    tvDownloadSize.text = sizeLabel
                    
                    if (progress >= 100) {
                        isDownloading = false
                        cardDownloadingState.visibility = View.GONE
                        btnInstallState.visibility = View.VISIBLE
                        btnInstallState.text = "تثبيت"

                        val downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        val tempFile = File(downloadFolder, "${currentApp!!.getUniqueFileName()}.tmp")
                        val finalFile = File(downloadFolder, currentApp!!.getUniqueFileName())

                        if (tempFile.exists()) tempFile.renameTo(finalFile)

                        showReadyToInstallNotification(currentApp!!.name, finalFile)
                        
                        binding.btnShareApk.visibility = View.VISIBLE
                        binding.btnShareApk.setOnClickListener { shareApkFile(finalFile, currentApp!!.name) }
                        
                        binding.btnDeleteApk.visibility = View.VISIBLE
                        binding.btnDeleteApk.setOnClickListener { deleteAppFiles(currentApp!!) }
                        
                        // بدء التثبيت المتطور تلقائياً بعد التحميل
                        triggerAdvancedInstall(finalFile)
                    }
                }
            }
        }
    }

    // 🚀 استبدال installApkLegacy بالتثبيت المتقدم المتوافق مع DownloadTracker
    private fun triggerAdvancedInstall(file: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            delay(500) 

            if (!file.exists() || file.length() == 0L) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AppDetailsActivity, "عذراً، الملف قيد التجهيز أو غير صالح.", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // فحص سلامة الحزمة
            val packageInfo = packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            if (packageInfo == null) {
                file.delete() 
                withContext(Dispatchers.Main) {
                    binding.cardDownloadingState.visibility = View.GONE
                    binding.btnInstallState.visibility = View.VISIBLE
                    binding.btnInstallState.text = "إعادة التنزيل"
                    Toast.makeText(this@AppDetailsActivity, "حزمة التطبيق غير مكتملة أو تالفة، يرجى إعادة التنزيل.", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                binding.btnInstallState.visibility = View.GONE
                binding.cardDownloadingState.visibility = View.VISIBLE
                binding.progressDownload.isIndeterminate = true
                binding.tvDownloadPercent.text = "تحضير التثبيت المتقدم..."
                binding.tvDownloadSize.text = "جاري إنشاء جلسة آمنة"
            }

            try {
                val packageInstaller = packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    params.setRequireUserAction(PackageInstaller.SessionParams.REQUIRE_USER_ACTION_UNSPECIFIED)
                }

                val sessionId = packageInstaller.createSession(params)
                val session = packageInstaller.openSession(sessionId)

                val sizeBytes = file.length()
                FileInputStream(file).use { inputStream ->
                    session.openWrite("StoreInstallSession", 0, sizeBytes).use { outputStream ->
                        inputStream.copyTo(outputStream)
                        session.fsync(outputStream)
                    }
                }

                val intent = Intent(this@AppDetailsActivity, com.fix.engine.abdullah.installer.InstallStatusReceiver::class.java).apply {
                    action = "com.fix.engine.abdullah.COMMIT_INSTALL"
                }

                val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    this@AppDetailsActivity,
                    sessionId,
                    intent,
                    pendingIntentFlags
                )

                session.commit(pendingIntent.intentSender)
                session.close()

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AppDetailsActivity, "فشل تهيئة محرك التثبيت: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    binding.cardDownloadingState.visibility = View.GONE
                    binding.btnInstallState.visibility = View.VISIBLE
                    binding.btnInstallState.text = "تثبيت"
                }
            }
        }
    }

    private fun shareApkFile(file: File, appName: String) {
        if (!file.exists()) {
            Toast.makeText(this, "عذراً، ملف الـ APK غير متوفر للمشاركة!", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val contentUri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) 
            }
            startActivity(Intent.createChooser(shareIntent, "مشاركة تطبيق $appName عبر:"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "خطأ أثناء محاولة مشاركة الملف", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteAppFiles(app: AppModel) {
        val downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val finalFile = File(downloadFolder, app.getUniqueFileName())
        val tempFile = File(downloadFolder, "${app.getUniqueFileName()}.tmp")

        var isDeleted = false
        if (finalFile.exists() && finalFile.delete()) isDeleted = true
        if (tempFile.exists() && tempFile.delete()) isDeleted = true

        if (isDeleted) {
            Toast.makeText(this, "تم تنظيف ملفات التنزيل بنجاح", Toast.LENGTH_SHORT).show()
        }
        binding.btnShareApk.visibility = View.GONE
        binding.btnDeleteApk.visibility = View.GONE
        setupLogic(app)
    }

    private fun showReadyToInstallNotification(appName: String, file: File) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "INSTALL_CHANNEL"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "تثبيت التطبيقات", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val uri = FileProvider.getUriForFile(this@AppDetailsActivity, "$packageName.fileprovider", file)
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
            }
        }

        val pendingIntent = PendingIntent.getActivity(this, appName.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification_transparent) 
            .setContentTitle("اكتمل تنزيل $appName")
            .setContentText("اضغط هنا للبدء في التثبيت")
            .setColor(Color.parseColor("#4CAF50")) 
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(appName.hashCode(), notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::tracker.isInitialized) tracker.stopTracking()
        
        // 🧹 تنظيف الذاكرة ومنع التسريب بإزالة مستمع الفايربيس
        if (::databaseRef.isInitialized && activeFirebaseKey != null && downloadsListener != null) {
            databaseRef.child(activeFirebaseKey!!).removeEventListener(downloadsListener!!)
        }
    }
}
