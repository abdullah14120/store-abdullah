package com.fix.engine.abdullah.ui.details

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.fix.engine.abdullah.AbdullahStoreApp
import com.fix.engine.abdullah.R
import com.fix.engine.abdullah.data.model.AppModel
import com.fix.engine.abdullah.data.network.LinkExtractor
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
import java.util.Locale

/**
 * Developed by: Abdullah Al-Tamimi
 * Architecture: Direct APK Downloading, No .tmp, No Resume, Persistent Background Progress
 */
class AppDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppDetailsBinding
    private lateinit var downloadManager: AndroidDownloadManager
    private lateinit var tracker: DownloadTracker

    private var isDownloading = false
    private var currentDownloadId = -1
    private var currentApp: AppModel? = null

    private lateinit var databaseRef: DatabaseReference
    private var downloadsListener: ValueEventListener? = null
    private var activeFirebaseKey: String? = null

    companion object {
        init {
            System.loadLibrary("native-lib")
        }
    }

    private external fun getSecureFirebaseUrl(): String

    private val installStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.fix.engine.abdullah.UPDATE_UI" -> {
                    val pkgName = intent.getStringExtra("PACKAGE_NAME")
                    val isSuccess = intent.getBooleanExtra("IS_SUCCESS", false)

                    if (pkgName == currentApp?.packageName) {
                        runOnUiThread {
                            binding.btnInstallState.isEnabled = true
                            if (isSuccess) {
                                val downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                                currentApp?.getUniqueFileName()?.let { fileName ->
                                    File(downloadFolder, fileName).delete()
                                }
                                currentApp?.let { setupLogic(it) }
                            } else {
                                binding.btnInstallState.text = "تثبيت"
                                binding.btnInstallState.visibility = View.VISIBLE
                                binding.cardDownloadingState.visibility = View.GONE
                            }
                        }
                    }
                }
                "com.fix.engine.abdullah.DOWNLOAD_STATE_CHANGED" -> {
                    currentApp?.let { app ->
                        runOnUiThread {
                            syncDownloadAndUiState(app)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.google.firebase.FirebaseApp.initializeApp(this)
        binding = ActivityAppDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        downloadManager = AndroidDownloadManager(this)
        tracker = DownloadTracker(this)

        setupToolbar()
        initFirebase()
        registerInstallReceiver()

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
        } else {
            Toast.makeText(this, "بيانات التطبيق غير صالحة", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        currentApp?.let { app ->
            syncDownloadAndUiState(app)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::tracker.isInitialized) tracker.stopTracking()
        try {
            unregisterReceiver(installStateReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (::databaseRef.isInitialized && activeFirebaseKey != null && downloadsListener != null) {
            databaseRef.child(activeFirebaseKey!!).removeEventListener(downloadsListener!!)
        }
    }

    private fun registerInstallReceiver() {
        val filter = IntentFilter().apply {
            addAction("com.fix.engine.abdullah.UPDATE_UI")
            addAction("com.fix.engine.abdullah.DOWNLOAD_STATE_CHANGED")
        }
        ContextCompat.registerReceiver(this, installStateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun initFirebase() {
        try {
            val decodedFirebaseUrl = getSecureFirebaseUrl()
            databaseRef = FirebaseDatabase.getInstance(decodedFirebaseUrl).getReference("download_stats")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hasEnoughStorage(): Boolean {
        val usableSpace = Environment.getDataDirectory().usableSpace
        val minimumRequired = 300 * 1024 * 1024L
        return usableSpace >= minimumRequired
    }

    private fun prepareAndDownload(app: AppModel) {
        if (!hasEnoughStorage()) {
            showStorageFullDialog()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            Toast.makeText(this, "يرجى منح إذن تثبيت التطبيقات من هذا المتجر", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply { data = Uri.parse("package:$packageName") })
            return
        }

        isDownloading = true
        binding.btnInstallState.visibility = View.GONE
        binding.layoutInstalledState.visibility = View.GONE
        binding.btnShareApk.visibility = View.GONE
        binding.btnDeleteApk.visibility = View.GONE
        binding.cardDownloadingState.visibility = View.VISIBLE
        binding.btnCancelDownload.visibility = View.VISIBLE
        binding.progressDownload.isIndeterminate = true
        binding.tvDownloadPercent.text = "جاري بدء التنزيل..."
        binding.tvDownloadSize.text = "يرجى الانتظار..."

        incrementDownloadCount(app.packageName)

        lifecycleScope.launch(Dispatchers.IO) {
            val directUrl = LinkExtractor.extract(app.downloadUrl)
            withContext(Dispatchers.Main) {
                val downloadId = downloadManager.enqueueDownload(directUrl, app.getUniqueFileName(), app.packageName)
                currentDownloadId = downloadId
                startTrackingProcess(downloadId)
            }
        }
    }

    private fun startTrackingProcess(downloadId: Int) {
        currentDownloadId = downloadId

        binding.btnCancelDownload.setOnClickListener {
            val fetch = Fetch.getDefaultInstance()
            fetch.cancel(downloadId)
            fetch.remove(downloadId)
            tracker.stopTracking()
            isDownloading = false

            val downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            currentApp?.let { app ->
                File(downloadFolder, app.getUniqueFileName()).delete()
            }

            binding.cardDownloadingState.visibility = View.GONE
            binding.btnInstallState.visibility = View.VISIBLE
            Toast.makeText(this, "تم إلغاء التنزيل", Toast.LENGTH_SHORT).show()

            currentApp?.let { setupLogic(it) }
        }

        tracker.startTracking(downloadId) { progress, sizeLabel ->
            runOnUiThread {
                binding.apply {
                    if (progress == -1) {
                        isDownloading = false
                        cardDownloadingState.visibility = View.GONE
                        btnInstallState.visibility = View.VISIBLE
                        Toast.makeText(this@AppDetailsActivity, sizeLabel, Toast.LENGTH_LONG).show()
                        currentApp?.let { setupLogic(it) }
                        return@apply
                    }

                    if (progress in 1..99) {
                        btnCancelDownload.visibility = View.VISIBLE
                        progressDownload.isIndeterminate = false
                        progressDownload.progress = progress
                        tvDownloadPercent.text = "جاري التنزيل... $progress%"
                        tvDownloadSize.text = sizeLabel
                    } else if (progress == 0) {
                        btnCancelDownload.visibility = View.VISIBLE
                        progressDownload.isIndeterminate = true
                        tvDownloadPercent.text = "جاري التنزيل..."
                        tvDownloadSize.text = sizeLabel
                    } else if (progress >= 100) {
                        isDownloading = false
                        cardDownloadingState.visibility = View.GONE
                        btnInstallState.visibility = View.VISIBLE
                        btnInstallState.text = "تثبيت"

                        val downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        val finalFile = File(downloadFolder, currentApp!!.getUniqueFileName())

                        showReadyToInstallNotification(currentApp!!.name, finalFile)
                        setupLogic(currentApp!!)
                        triggerAdvancedInstall(finalFile)
                    }
                }
            }
        }
    }

    /**
     * مزامنة حالة الصفحة عند الخروج والعودة إليها بدون استئناف وبدون .tmp
     */
    @SuppressLint("Range")
    private fun syncDownloadAndUiState(app: AppModel) {
        val fileName = app.getUniqueFileName()
        val downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val finalFile = File(downloadFolder, fileName)

        val fetch = Fetch.getDefaultInstance()
        fetch.getDownloads { downloads ->
            val matchedDownload = downloads
                .filter { it.file == finalFile.absolutePath || it.tag == app.packageName }
                .maxByOrNull { it.created }

            runOnUiThread {
                if (matchedDownload != null && (matchedDownload.status == Status.DOWNLOADING || matchedDownload.status == Status.QUEUED || matchedDownload.status == Status.ADDED)) {
                    // التحميل ما زال جارياً في الخلفية: نعرض النسبة الحالية فوراً ونكمل المتابعة
                    isDownloading = true
                    currentDownloadId = matchedDownload.id

                    binding.btnInstallState.visibility = View.GONE
                    binding.layoutInstalledState.visibility = View.GONE
                    binding.btnShareApk.visibility = View.GONE
                    binding.btnDeleteApk.visibility = View.GONE
                    binding.cardDownloadingState.visibility = View.VISIBLE
                    binding.btnCancelDownload.visibility = View.VISIBLE

                    val currentProg = matchedDownload.progress.coerceIn(0, 99)
                    val dlMb = matchedDownload.downloaded / (1024.0 * 1024.0)
                    val totMb = matchedDownload.total / (1024.0 * 1024.0)

                    if (currentProg > 0) {
                        binding.progressDownload.isIndeterminate = false
                        binding.progressDownload.progress = currentProg
                        binding.tvDownloadPercent.text = "جاري التنزيل... $currentProg%"
                        binding.tvDownloadSize.text = if (matchedDownload.total > 0) {
                            String.format(Locale.ENGLISH, "%.1f/%.1f MB", dlMb, totMb)
                        } else {
                            String.format(Locale.ENGLISH, "%.1f MB", dlMb)
                        }
                    } else {
                        binding.progressDownload.isIndeterminate = true
                        binding.tvDownloadPercent.text = "جاري التنزيل..."
                    }

                    startTrackingProcess(matchedDownload.id)
                } else {
                    // إذا كان هناك تحميل متوقف أو فاشل سابقاً، نمسحه تماماً لأننا ألغينا الاستئناف
                    if (matchedDownload != null && matchedDownload.status != Status.COMPLETED) {
                        fetch.remove(matchedDownload.id)
                        finalFile.delete()
                    }
                    isDownloading = false
                    setupLogic(app)
                }
            }
        }
    }

    private fun triggerAdvancedInstall(file: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            delay(200)

            if (!AbdullahStoreApp.isApkCompleteAndValid(this@AppDetailsActivity, file)) {
                file.delete()
                withContext(Dispatchers.Main) {
                    binding.cardDownloadingState.visibility = View.GONE
                    binding.btnInstallState.visibility = View.VISIBLE
                    binding.btnInstallState.text = "إعادة التنزيل"
                    Toast.makeText(this@AppDetailsActivity, "الملف غير مكتمل، يرجى التنزيل من جديد.", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                binding.btnInstallState.visibility = View.GONE
                binding.cardDownloadingState.visibility = View.VISIBLE
                binding.btnCancelDownload.visibility = View.GONE
                binding.progressDownload.isIndeterminate = true
                binding.tvDownloadPercent.text = "جاري التثبيت..."
                binding.tvDownloadSize.text = "يرجى التأكيد عند ظهور نافذة النظام"
            }

            try {
                val packageInstaller = packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
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
                    Toast.makeText(this@AppDetailsActivity, "فشل تهيئة محرك التثبيت.", Toast.LENGTH_LONG).show()
                    binding.btnInstallState.text = "تثبيت"
                    binding.btnInstallState.visibility = View.VISIBLE
                    binding.cardDownloadingState.visibility = View.GONE
                }
            }
        }
    }

    private fun setupLogic(app: AppModel) {
        if (isDownloading) return

        val pm = packageManager
        val fileName = app.getUniqueFileName()
        val downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val finalFile = File(downloadFolder, fileName)

        // التحقق من أن ملف الـ APK موجود ومكتمل 100%
        val isApkReady = AbdullahStoreApp.isApkCompleteAndValid(this, finalFile)
        if (!isApkReady && finalFile.exists()) {
            // إذا كان الملف تالفاً أو ناقصاً والآن لا يوجد تحميل نشط، نحذفه فوراً
            finalFile.delete()
        }

        if (isApkReady) {
            binding.btnDeleteApk.visibility = View.VISIBLE
            binding.btnDeleteApk.setOnClickListener { deleteAppFiles(app) }
        } else {
            binding.btnDeleteApk.visibility = View.GONE
        }

        try {
            val pInfo = pm.getPackageInfo(app.packageName, 0)
            val installedVer = pInfo.versionName ?: ""
            val comparisonResult = compareVersionNames(app.versionName, installedVer)

            if (isApkReady) {
                handleInstallOrUpdateState(app, finalFile, isApkReady = true, isUpdate = comparisonResult > 0)
            } else if (comparisonResult > 0) {
                handleInstallOrUpdateState(app, finalFile, isApkReady = false, isUpdate = true)
            } else {
                binding.cardDownloadingState.visibility = View.GONE
                binding.btnInstallState.visibility = View.GONE
                binding.layoutInstalledState.visibility = View.VISIBLE
                binding.btnShareApk.visibility = View.GONE

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
            handleInstallOrUpdateState(app, finalFile, isApkReady = isApkReady, isUpdate = false)
        }
    }

    private fun compareVersionNames(serverVersion: String, installedVersion: String): Int {
        try {
            val sParts = serverVersion.trim().split(".")
            val iParts = installedVersion.trim().split(".")
            val maxLen = maxOf(sParts.size, iParts.size)

            for (i in 0 until maxLen) {
                val sVal = sParts.getOrNull(i)?.toIntOrNull() ?: 0
                val iVal = iParts.getOrNull(i)?.toIntOrNull() ?: 0

                if (sVal > iVal) return 1
                if (sVal < iVal) return -1
            }
        } catch (e: Exception) {
            return if (serverVersion != installedVersion) 1 else 0
        }
        return 0
    }

    private fun handleInstallOrUpdateState(app: AppModel, finalFile: File, isApkReady: Boolean, isUpdate: Boolean) {
        binding.layoutInstalledState.visibility = View.GONE
        binding.cardDownloadingState.visibility = View.GONE
        binding.btnInstallState.visibility = View.VISIBLE

        if (isApkReady) {
            binding.btnInstallState.text = "تثبيت"
            binding.btnShareApk.visibility = View.VISIBLE
            binding.btnShareApk.setOnClickListener { shareApkFile(finalFile, app.name) }
            binding.btnInstallState.setOnClickListener { triggerAdvancedInstall(finalFile) }
        } else {
            binding.btnInstallState.text = if (isUpdate) "تحديث" else "تنزيل"
            binding.btnShareApk.visibility = View.GONE
            binding.btnInstallState.setOnClickListener { prepareAndDownload(app) }
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
            count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }

    private fun showStorageFullDialog() {
        AlertDialog.Builder(this)
            .setTitle("مساحة التخزين ممتلئة ⚠️")
            .setMessage("عذراً، لا توجد مساحة كافية في جهازك لتنزيل وتثبيت هذا التطبيق. يرجى تفريغ بعض المساحة والمحاولة مجدداً.")
            .setPositiveButton("حسناً") { dialog, _ -> dialog.dismiss() }
            .setNegativeButton("إدارة التخزين") { dialog, _ ->
                startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
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
        val fetch = Fetch.getDefaultInstance()
        if (currentDownloadId != -1) {
            fetch.cancel(currentDownloadId)
            fetch.remove(currentDownloadId)
        }

        val downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val finalFile = File(downloadFolder, app.getUniqueFileName())
        val tempFile = File(downloadFolder, "${app.getUniqueFileName()}.tmp")

        var isDeleted = false
        if (finalFile.exists() && finalFile.delete()) isDeleted = true
        if (tempFile.exists() && tempFile.delete()) isDeleted = true

        if (isDeleted) {
            Toast.makeText(this, "تم مسح ملف التثبيت بنجاح", Toast.LENGTH_SHORT).show()
        }
        binding.btnShareApk.visibility = View.GONE
        binding.btnDeleteApk.visibility = View.GONE
        isDownloading = false
        setupLogic(app)
    }
}
