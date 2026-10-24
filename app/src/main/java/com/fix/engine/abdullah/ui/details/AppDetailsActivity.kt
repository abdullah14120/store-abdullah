package com.fix.engine.abdullah.ui.details

import android.annotation.SuppressLint
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
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fix.engine.abdullah.AbdullahStoreApp
import com.fix.engine.abdullah.R
import com.fix.engine.abdullah.data.model.AppModel
import com.fix.engine.abdullah.data.network.LinkExtractor
import com.fix.engine.abdullah.databinding.ActivityAppDetailsBinding
import com.fix.engine.abdullah.service.AndroidDownloadManager
import com.fix.engine.abdullah.service.DownloadTracker
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Developed by: Abdullah Al-Tamimi
 * Architecture: Multi-Format Installer (APK / APKM / XAPK / APKS), Smart Storage Gate & Live Reviews
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
        var isActivityVisible: Boolean = false

        init {
            System.loadLibrary("native-lib")
        }
    }

    private external fun getSecureFirebaseUrl(): String

    private val installStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.fix.engine.abdullah.UPDATE_UI" -> {
                    val pkgName = intent.getStringExtra("PACKAGE_NAME") ?: ""
                    val isSuccess = intent.getBooleanExtra("IS_SUCCESS", false)
                    val statusCode = intent.getIntExtra("STATUS_CODE", -999)
                    val errorMessage = intent.getStringExtra("ERROR_MESSAGE")

                    val confirmationIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra("CONFIRMATION_INTENT", Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra("CONFIRMATION_INTENT")
                    }

                    if (statusCode == PackageInstaller.STATUS_PENDING_USER_ACTION && confirmationIntent != null) {
                        runOnUiThread {
                            try {
                                startActivity(confirmationIntent)
                            } catch (e: Exception) {
                                val downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                                currentApp?.let { app ->
                                    val apkFile = File(downloadFolder, app.getUniqueFileName())
                                    if (!AbdullahStoreApp.isSplitApksBundle(apkFile)) {
                                        installWithStandardSystemIntent(apkFile)
                                    }
                                }
                            }
                        }
                        return
                    }

                    if (pkgName.isBlank() || pkgName == currentApp?.packageName) {
                        runOnUiThread {
                            binding.btnInstallState.isEnabled = true
                            binding.cardDownloadingState.visibility = View.GONE
                            binding.btnInstallState.visibility = View.VISIBLE

                            if (isSuccess) {
                                val downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                                currentApp?.getUniqueFileName()?.let { fileName ->
                                    File(downloadFolder, fileName).delete()
                                }
                                currentApp?.let { setupLogic(it) }
                            } else {
                                binding.btnInstallState.text = "تثبيت"
                                currentApp?.let { app ->
                                    setupLogic(app)
                                    if (statusCode != -999) {
                                        showInstallFailureDialog(app, statusCode, errorMessage)
                                    }
                                }
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
            binding.txtVerifiedBadge.text = "🛡️ آمن ومفحوص • ${appData.packageName}"
            setupCommentsSection(appData.packageName)
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
        isActivityVisible = true
        currentApp?.let { app ->
            syncDownloadAndUiState(app)
        }
    }

    override fun onPause() {
        super.onPause()
        isActivityVisible = false
    }

    override fun onDestroy() {
        super.onDestroy()
        isActivityVisible = false
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

    // ====================================================================
    // 🛡️ فحص المساحة الفعلي قبل السماح بالتنزيل أو التثبيت
    // ====================================================================

    private fun getAvailableInternalStorageBytes(): Long {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            Environment.getDataDirectory().usableSpace
        }
    }

    private fun getRequiredStorageForDownload(app: AppModel): Long {
        val appSizeBytes = app.size.toLongOrNull() ?: (130 * 1024 * 1024L)
        val safetyBuffer = 150 * 1024 * 1024L
        return (appSizeBytes * 2) + safetyBuffer
    }

    private fun getRequiredStorageForInstall(apkFile: File): Long {
        val fileSize = if (apkFile.exists() && apkFile.length() > 0) apkFile.length() else (130 * 1024 * 1024L)
        val safetyBuffer = 120 * 1024 * 1024L
        return fileSize + safetyBuffer
    }

    private fun ensureInstallPermissionGranted(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            if (!isFinishing && !isDestroyed) {
                MaterialAlertDialogBuilder(this, R.style.Theme_FixEngine_Dialog)
                    .setTitle("إذن التثبيت مطلوب 🔐")
                    .setMessage("لتتمكن من تثبيت التطبيقات وتحديثها، يرجى تفعيل خيار (السماح بتثبيت التطبيقات من هذا المصدر) لمتجر Abdullah.")
                    .setPositiveButton("تفعيل الإذن الآن") { dialog, _ ->
                        dialog.dismiss()
                        startActivity(
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                data = Uri.parse("package:$packageName")
                            }
                        )
                    }
                    .setNegativeButton("إلغاء", null)
                    .show()
            }
            return false
        }
        return true
    }

    private fun prepareAndDownload(app: AppModel) {
        val availableBytes = getAvailableInternalStorageBytes()
        val requiredBytes = getRequiredStorageForDownload(app)

        if (availableBytes < requiredBytes) {
            showStorageFullDialog(requiredBytes, availableBytes, isBeforeDownload = true)
            return
        }

        if (!ensureInstallPermissionGranted()) {
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
            try {
                val fetch = Fetch.getDefaultInstance()
                fetch.cancel(downloadId)
                fetch.remove(downloadId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
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

                        val avail = getAvailableInternalStorageBytes()
                        val req = currentApp?.let { getRequiredStorageForDownload(it) } ?: (200 * 1024 * 1024L)
                        if (avail < req) {
                            showStorageFullDialog(req, avail, isBeforeDownload = false)
                        } else {
                            Toast.makeText(this@AppDetailsActivity, sizeLabel, Toast.LENGTH_LONG).show()
                        }
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

    @SuppressLint("Range")
    private fun syncDownloadAndUiState(app: AppModel) {
        val fileName = app.getUniqueFileName()
        val downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val finalFile = File(downloadFolder, fileName)

        try {
            val fetch = Fetch.getDefaultInstance()
            fetch.getDownloads { downloads ->
                val matchedDownload = downloads
                    .filter { it.file == finalFile.absolutePath || it.tag == app.packageName }
                    .maxByOrNull { it.created }

                runOnUiThread {
                    if (matchedDownload != null && (matchedDownload.status == Status.DOWNLOADING || matchedDownload.status == Status.QUEUED || matchedDownload.status == Status.ADDED)) {
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
                        if (matchedDownload != null && matchedDownload.status != Status.COMPLETED) {
                            fetch.remove(matchedDownload.id)
                            finalFile.delete()
                        }
                        isDownloading = false
                        setupLogic(app)
                    }
                }
            }
        } catch (e: Exception) {
            isDownloading = false
            setupLogic(app)
        }
    }

    // ====================================================================
    // 🚀 محرك التثبيت الشامل (يدعم APK العادي + حزم APKM / XAPK / APKS)
    // ====================================================================

    private fun triggerAdvancedInstall(file: File) {
        if (!ensureInstallPermissionGranted()) {
            return
        }

        val availableBytes = getAvailableInternalStorageBytes()
        val requiredBytes = getRequiredStorageForInstall(file)
        if (availableBytes < requiredBytes) {
            showStorageFullDialog(requiredBytes, availableBytes, isBeforeDownload = false)
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            delay(150)

            val isSplitBundle = AbdullahStoreApp.isSplitApksBundle(file)
            val isValidSingleApk = AbdullahStoreApp.isApkCompleteAndValid(this@AppDetailsActivity, file)

            if (!isSplitBundle && !isValidSingleApk) {
                file.delete()
                withContext(Dispatchers.Main) {
                    binding.cardDownloadingState.visibility = View.GONE
                    binding.btnInstallState.visibility = View.VISIBLE
                    binding.btnInstallState.text = "إعادة التنزيل"
                    Toast.makeText(
                        this@AppDetailsActivity,
                        "الحزمة تالفة أو بصيغة APKM مشفرة (يرجى استخدام صيغة APK أو APKS/XAPK قياسية).",
                        Toast.LENGTH_LONG
                    ).show()
                }
                return@launch
            }

            if (!isSplitBundle && availableBytes < (file.length() * 2) + (100 * 1024 * 1024L)) {
                withContext(Dispatchers.Main) {
                    installWithStandardSystemIntent(file)
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                binding.btnInstallState.visibility = View.GONE
                binding.cardDownloadingState.visibility = View.VISIBLE
                binding.btnCancelDownload.visibility = View.GONE
                binding.progressDownload.isIndeterminate = true
                binding.tvDownloadPercent.text = if (isSplitBundle) {
                    "جاري تجميع وتثبيت حزمة APKM / XAPK..."
                } else {
                    "جاري تجهيز التثبيت..."
                }
                binding.tvDownloadSize.text = "يرجى التأكيد عند ظهور نافذة النظام"
            }

            var session: PackageInstaller.Session? = null
            try {
                val packageInstaller = packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    currentApp?.packageName?.let { setAppPackageName(it) }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    }
                }

                if (isSplitBundle) {
                    // 📦 مسار تثبيت الحزم المقسمة (.apkm / .xapk / .apks)
                    ZipFile(file).use { zipFile ->
                        val apkEntries = zipFile.entries().asSequence()
                            .filter { !it.isDirectory && it.name.lowercase().endsWith(".apk") && it.size > 0L }
                            .toList()

                        val filteredEntries = filterCompatibleSplitEntries(apkEntries)
                        val totalUncompressedSize = filteredEntries.sumOf { it.size }
                        params.setSize(totalUncompressedSize)

                        val sessionId = packageInstaller.createSession(params)
                        session = packageInstaller.openSession(sessionId)

                        filteredEntries.forEachIndexed { index, entry ->
                            val safeStreamName = "split_pkg_$index.apk"
                            zipFile.getInputStream(entry).use { inputStream ->
                                session!!.openWrite(safeStreamName, 0, entry.size).use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                    session!!.fsync(outputStream)
                                }
                            }
                        }

                        commitInstallerSession(session!!, sessionId)
                    }
                } else {
                    // 📱 مسار تثبيت ملف .apk المفرد
                    val sizeBytes = file.length()
                    params.setSize(sizeBytes)

                    val sessionId = packageInstaller.createSession(params)
                    session = packageInstaller.openSession(sessionId)

                    FileInputStream(file).use { inputStream ->
                        session!!.openWrite("base.apk", 0, sizeBytes).use { outputStream ->
                            inputStream.copyTo(outputStream)
                            session!!.fsync(outputStream)
                        }
                    }

                    commitInstallerSession(session!!, sessionId)
                }

            } catch (e: Exception) {
                session?.abandon()
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    binding.cardDownloadingState.visibility = View.GONE
                    binding.btnInstallState.visibility = View.VISIBLE
                    binding.btnInstallState.text = "تثبيت"
                    if (!isSplitBundle) {
                        installWithStandardSystemIntent(file)
                    } else {
                        Toast.makeText(this@AppDetailsActivity, "تعذر تثبيت الحزمة المقسمة: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

 /**
     * 🧠 فلترة ذكية لحزم APKM/XAPK الشاملة (مثل حزم واتساب الأعمال من APKMirror):
     * تختار فقط المعالج المطابق لهاتف المستخدم + دقة الشاشة الأنسب لمنع تعارض الـ Splits أثناء التثبيت
     */
    private fun filterCompatibleSplitEntries(entries: List<ZipEntry>): List<ZipEntry> {
        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull()?.lowercase()?.replace("-", "_") ?: "arm64_v8a"
        val allAbis = listOf("arm64_v8a", "armeabi_v7a", "armeabi", "x86_64", "x86")
        val allDpis = listOf("xxxhdpi", "xxhdpi", "xhdpi", "hdpi", "mdpi", "ldpi", "tvdpi")

        // 1. تحديد المعالج الأنسب الموجود داخل الحزمة
        val selectedAbi = Build.SUPPORTED_ABIS
            .map { it.lowercase().replace("-", "_") }
            .firstOrNull { abi ->
                entries.any { File(it.name).name.lowercase().replace("-", "_").contains("config.$abi") || File(it.name).name.lowercase().replace("-", "_").contains(abi) }
            } ?: primaryAbi

        // 2. تحديد دقة الشاشة الأنسب الموجودة داخل الحزمة بناءً على كثافة شاشة هاتف المستخدم
        val deviceDpi = resources.displayMetrics.densityDpi
        val preferredDpiOrder = when {
            deviceDpi >= 560 -> listOf("xxxhdpi", "xxhdpi", "xhdpi", "hdpi", "mdpi")
            deviceDpi >= 400 -> listOf("xxhdpi", "xxxhdpi", "xhdpi", "hdpi", "mdpi")
            deviceDpi >= 280 -> listOf("xhdpi", "xxhdpi", "hdpi", "xxxhdpi", "mdpi")
            else -> listOf("hdpi", "mdpi", "xhdpi", "xxhdpi", "xxxhdpi")
        }

        val selectedDpi = preferredDpiOrder.firstOrNull { dpi ->
            entries.any { entry ->
                val name = File(entry.name).name.lowercase()
                name.contains("config.$dpi") || name == "split_config.$dpi.apk"
            }
        }

        return entries.filter { entry ->
            val fileName = File(entry.name).name.lowercase().replace("-", "_")

            // فحص ما إذا كان الملف خاصاً بمعالج (ABI)
            val matchedAbi = allAbis.firstOrNull { abi ->
                fileName.contains("config.$abi") || fileName.contains(".$abi.") || fileName.endsWith("_$abi.apk")
            }
            if (matchedAbi != null) {
                return@filter matchedAbi == selectedAbi
            }

            // فحص ما إذا كان الملف خاصاً بكثافة الشاشة (DPI)
            val matchedDpi = allDpis.firstOrNull { dpi ->
                fileName.contains("config.$dpi") || fileName.contains(".$dpi.")
            }
            if (matchedDpi != null && selectedDpi != null) {
                return@filter matchedDpi == selectedDpi
            }

            // قبول الملف الأساسي (base.apk) وأي ملفات لغات أو ميزات أخرى
            true
        }
    }

    private fun commitInstallerSession(session: PackageInstaller.Session, sessionId: Int) {
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
    }

    private fun installWithStandardSystemIntent(file: File) {
        if (!file.exists()) {
            Toast.makeText(this, "ملف التثبيت غير موجود", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val apkUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(installIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "تعذر فتح مثبت النظام: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun showInstallFailureDialog(app: AppModel, statusCode: Int, errorMessage: String?) {
        if (isFinishing || isDestroyed) return

        if (statusCode == PackageInstaller.STATUS_FAILURE_STORAGE) {
            val downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val apkFile = File(downloadFolder, app.getUniqueFileName())
            showStorageFullDialog(
                requiredBytes = getRequiredStorageForInstall(apkFile),
                availableBytes = getAvailableInternalStorageBytes(),
                isBeforeDownload = false
            )
            return
        }

        if (statusCode == PackageInstaller.STATUS_FAILURE_ABORTED) {
            Toast.makeText(this, "تم إلغاء التثبيت", Toast.LENGTH_SHORT).show()
            return
        }

        val downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val apkFile = File(downloadFolder, app.getUniqueFileName())

        val builder = MaterialAlertDialogBuilder(this, R.style.Theme_FixEngine_Dialog)
            .setTitle("تعذر إتمام التثبيت ⚠️")
            .setMessage(errorMessage ?: "حدث خطأ أثناء محاولة تثبيت التطبيق.")
            .setNegativeButton("إغلاق", null)

        when (statusCode) {
            PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                builder.setPositiveButton("حذف النسخة القديمة") { dialog, _ ->
                    dialog.dismiss()
                    val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                        data = Uri.parse("package:${app.packageName}")
                    }
                    startActivity(uninstallIntent)
                }
            }
            PackageInstaller.STATUS_FAILURE_INVALID -> {
                builder.setPositiveButton("مسح وإعادة التنزيل") { dialog, _ ->
                    dialog.dismiss()
                    deleteAppFiles(app)
                    prepareAndDownload(app)
                }
            }
            else -> {
                if (apkFile.exists() && !AbdullahStoreApp.isSplitApksBundle(apkFile)) {
                    builder.setPositiveButton("تثبيت مباشر (الوضع المتوافق)") { dialog, _ ->
                        dialog.dismiss()
                        installWithStandardSystemIntent(apkFile)
                    }
                }
            }
        }

        builder.show()
    }

    private fun showStorageFullDialog(requiredBytes: Long, availableBytes: Long, isBeforeDownload: Boolean) {
        if (isFinishing || isDestroyed) return

        val requiredMb = requiredBytes / (1024.0 * 1024.0)
        val availableMb = availableBytes / (1024.0 * 1024.0)
        val actionText = if (isBeforeDownload) "تنزيل وتثبيت" else "تثبيت"

        val message = String.format(
            Locale.US,
            "عذراً، لا توجد مساحة تخزين كافية في جهازك لـ %s هذا التطبيق.\n\n" +
                    "• المساحة المطلوبة: %.0f MB\n" +
                    "• المساحة المتوفرة حالياً: %.0f MB\n\n" +
                    "يرجى تفريغ بعض المساحة من جهازك أولاً ثم المحاولة مجدداً.",
            actionText,
            requiredMb,
            availableMb
        )

        MaterialAlertDialogBuilder(this, R.style.Theme_FixEngine_Dialog)
            .setTitle("مساحة التخزين غير كافية ⚠️")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("تنظيف المساحة الآن") { dialog, _ ->
                dialog.dismiss()
                try {
                    startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
            .setNegativeButton("حسناً") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    // ====================================================================
    // 🎨 إدارة حالات الواجهة والأزرار
    // ====================================================================

    private fun setupLogic(app: AppModel) {
        if (isDownloading) return

        val pm = packageManager
        val fileName = app.getUniqueFileName()
        val downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val finalFile = File(downloadFolder, fileName)

        val isApkReady = AbdullahStoreApp.isApkCompleteAndValid(this, finalFile)
        if (!isApkReady && finalFile.exists()) {
            finalFile.delete()
        }

        if (isApkReady) {
            binding.btnDeleteApk.visibility = View.VISIBLE
            binding.btnDeleteApk.setOnClickListener { deleteAppFiles(app) }
        } else {
            binding.btnDeleteApk.visibility = View.GONE
        }

        try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(app.packageName, 0)
            }
            val installedVer = pInfo.versionName ?: ""
            val comparisonResult = compareVersionNames(app.versionName, installedVer)

            if (comparisonResult <= 0) {
                binding.cardDownloadingState.visibility = View.GONE
                binding.btnInstallState.visibility = View.GONE
                binding.layoutInstalledState.visibility = View.VISIBLE

                binding.btnShareApk.visibility = if (isApkReady) View.VISIBLE else View.GONE
                if (isApkReady) {
                    binding.btnShareApk.setOnClickListener { shareApkFile(finalFile, app.name) }
                }

                binding.btnOpenApp.setOnClickListener {
                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    launchIntent?.let { startActivity(it) } ?: Toast.makeText(this, "لا يمكن فتح التطبيق", Toast.LENGTH_SHORT).show()
                }

                binding.btnUninstall.setOnClickListener {
                    val intent = Intent(Intent.ACTION_DELETE)
                    intent.data = Uri.parse("package:${app.packageName}")
                    startActivity(intent)
                }
            } else {
                handleInstallOrUpdateState(app, finalFile, isApkReady = isApkReady, isUpdate = true)
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
            Toast.makeText(this, "عذراً، ملف الحزمة غير متوفر للمشاركة!", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val contentUri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "*/*"
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
        try {
            val fetch = Fetch.getDefaultInstance()
            if (currentDownloadId != -1) {
                fetch.cancel(currentDownloadId)
                fetch.remove(currentDownloadId)
            }
        } catch (e: Exception) {
            e.printStackTrace()
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

    // ====================================================================
    // 💬 محرك التعليقات والتقييمات الحي عبر Firebase Realtime Database
    // ====================================================================

    private fun setupCommentsSection(packageName: String) {
        val safeKey = packageName.trim().lowercase().replace(".", "_")
        val commentsRef = try {
            FirebaseDatabase.getInstance(getSecureFirebaseUrl())
                .getReference("app_comments")
                .child(safeKey)
        } catch (e: Exception) {
            return
        }

        binding.rvComments.layoutManager = LinearLayoutManager(this)

        binding.btnAddComment.setOnClickListener {
            showAddCommentDialog(commentsRef)
        }

        commentsRef.limitToLast(30).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val commentsList = mutableListOf<Map<String, Any>>()
                var totalStars = 0L

                for (child in snapshot.children) {
                    val user = child.child("user").getValue(String::class.java) ?: "مستخدم"
                    val text = child.child("text").getValue(String::class.java) ?: continue
                    val stars = child.child("stars").getValue(Long::class.java) ?: 5L
                    val time = child.child("time").getValue(String::class.java) ?: ""

                    totalStars += stars
                    commentsList.add(
                        mapOf(
                            "user" to user,
                            "text" to text,
                            "stars" to stars,
                            "time" to time
                        )
                    )
                }

                commentsList.reverse()

                runOnUiThread {
                    if (commentsList.isEmpty()) {
                        binding.txtEmptyComments.visibility = View.VISIBLE
                        binding.rvComments.visibility = View.GONE
                        binding.txtStatRating.text = "5.0 ★"
                        binding.txtStatReviewsCount.text = "جديد"
                    } else {
                        binding.txtEmptyComments.visibility = View.GONE
                        binding.rvComments.visibility = View.VISIBLE
                        val avg = totalStars.toDouble() / commentsList.size
                        binding.txtStatRating.text = String.format(Locale.US, "%.1f ★", avg)
                        binding.txtStatReviewsCount.text = "${commentsList.size} مراجعة"
                        binding.rvComments.adapter = SimpleCommentsAdapter(commentsList)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showAddCommentDialog(commentsRef: DatabaseReference) {
        val prefs = getSharedPreferences("FixEnginePrefs", Context.MODE_PRIVATE)
        val savedName = prefs.getString("user_display_name", "") ?: ""

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
        }

        val ratingBar = RatingBar(this, null, android.R.attr.ratingBarStyleIndicator).apply {
            numStars = 5
            stepSize = 1f
            rating = 5f
            setIsIndicator(false)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        }

        val etName = EditText(this).apply {
            hint = "اسمك (مثال: محمد)"
            setText(savedName)
            maxLines = 1
        }

        val etComment = EditText(this).apply {
            hint = "اكتب رأيك أو تجربتك للتطبيق هنا..."
            minLines = 2
            maxLines = 4
        }

        container.addView(ratingBar)
        container.addView(etName)
        container.addView(etComment)

        MaterialAlertDialogBuilder(this, R.style.Theme_FixEngine_Dialog)
            .setTitle("إضافة تقييم وتعليق ⭐")
            .setView(container)
            .setPositiveButton("نشر التعليق") { dialog, _ ->
                val name = etName.text.toString().trim().ifBlank { "مستخدم المتجر" }
                val comment = etComment.text.toString().trim()
                val stars = ratingBar.rating.toInt().coerceIn(1, 5)

                if (comment.length < 3) {
                    Toast.makeText(this, "يرجى كتابة تعليق واضح أولاً", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                prefs.edit().putString("user_display_name", name).apply()

                val dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date())
                val payload = mapOf(
                    "user" to name.take(25),
                    "text" to comment.take(250),
                    "stars" to stars,
                    "time" to dateStr
                )

                commentsRef.push().setValue(payload)
                    .addOnSuccessListener {
                        Toast.makeText(this, "تم نشر تعليقك بنجاح! شكراً لك ✨", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "تعذر نشر التعليق، تأكد من الاتصال بالإنترنت", Toast.LENGTH_SHORT).show()
                    }
                dialog.dismiss()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private inner class SimpleCommentsAdapter(
        private val items: List<Map<String, Any>>
    ) : RecyclerView.Adapter<SimpleCommentsAdapter.VH>() {

        inner class VH(val card: MaterialCardView, val tvHeader: TextView, val tvBody: TextView) :
            RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val density = parent.resources.displayMetrics.density
            val card = MaterialCardView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (10 * density).toInt() }
                radius = 14 * density
                cardElevation = 0f
                setCardBackgroundColor(ContextCompat.getColor(parent.context, R.color.md_theme_d_surfaceVariant))
            }

            val box = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                val p = (14 * density).toInt()
                setPadding(p, p, p, p)
            }

            val tvHeader = TextView(parent.context).apply {
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(parent.context, R.color.md_theme_d_primary))
            }

            val tvBody = TextView(parent.context).apply {
                textSize = 13.5f
                setPadding(0, (6 * density).toInt(), 0, 0)
                setTextColor(ContextCompat.getColor(parent.context, R.color.md_theme_d_onSurface))
            }

            box.addView(tvHeader)
            box.addView(tvBody)
            card.addView(box)
            return VH(card, tvHeader, tvBody)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val user = item["user"] as? String ?: "مستخدم"
            val stars = (item["stars"] as? Long ?: 5L).toInt()
            val time = item["time"] as? String ?: ""
            val text = item["text"] as? String ?: ""

            val starsText = "★".repeat(stars) + "☆".repeat(5 - stars)
            holder.tvHeader.text = "$user  •  $starsText   ($time)"
            holder.tvBody.text = text
        }

        override fun getItemCount(): Int = items.size
    }
}
