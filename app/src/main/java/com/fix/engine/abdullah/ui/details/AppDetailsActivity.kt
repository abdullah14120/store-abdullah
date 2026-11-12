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
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
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
 * Architecture: Multi-Format Installer (APK / APKM / XAPK / APKS), Smart Storage Gate & Lifecycle-Safe Live Reviews
 */
class AppDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppDetailsBinding
    private lateinit var downloadManager: AndroidDownloadManager
    private lateinit var tracker: DownloadTracker

    private var isDownloading = false
    private var isInstalling = false
    private var activeSessionId = -1
    private var currentDownloadId = -1
    private var currentApp: AppModel? = null

    private lateinit var databaseRef: DatabaseReference
    private var downloadsListener: ValueEventListener? = null
    private var activeFirebaseKey: String? = null

    private var commentsQuery: Query? = null
    private var commentsListener: ValueEventListener? = null

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
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            isInstalling = true
                            showInstallingStateUi(
                                statusText = "جاري التثبيت في الخلفية...",
                                subText = "يرجى التأكيد والانتظار حتى اكتمال التثبيت..."
                            )
                            try {
                                startActivity(confirmationIntent)
                            } catch (e: Exception) {
                                isInstalling = false
                                activeSessionId = -1
                                val downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                                currentApp?.let { app ->
                                    val apkFile = File(downloadFolder, app.getUniqueFileName())
                                    if (!AbdullahStoreApp.isSplitApksBundle(apkFile)) {
                                        installWithStandardSystemIntent(apkFile)
                                    } else {
                                        setupLogic(app)
                                    }
                                }
                            }
                        }
                        return
                    }

                    if (pkgName.isBlank() || pkgName == currentApp?.packageName) {
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            isInstalling = false
                            activeSessionId = -1
                            binding.btnInstallState.isEnabled = true

                            if (isSuccess) {
                                val downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                                currentApp?.getUniqueFileName()?.let { fileName ->
                                    File(downloadFolder, fileName).delete()
                                }
                                currentApp?.let { app ->
                                    showInstalledStateDirectly(app)
                                    lifecycleScope.launch {
                                        delay(350)
                                        if (!isFinishing && !isDestroyed) {
                                            setupLogic(app)
                                        }
                                    }
                                }
                            } else {
                                binding.cardDownloadingState.visibility = View.GONE
                                binding.btnInstallState.visibility = View.VISIBLE
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
                            if (!isFinishing && !isDestroyed) {
                                syncDownloadAndUiState(app)
                            }
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
            verifyBackgroundInstallSessionState(app)
        }
    }

    /**
     * 🛡️ مراقبة ذكية لجلسة التثبيت في الخلفية:
     * تضمن بقاء الشريط الدائري ظاهراً طوال فترة التثبيت، وتعيد الأزرار لوضعها الطبيعي فقط إذا انتهت الجلسة أو ألغاها المستخدم
     */
    private fun verifyBackgroundInstallSessionState(app: AppModel) {
        if (!isInstalling) return
        lifecycleScope.launch(Dispatchers.IO) {
            // فحص سريع بعد عودة المستخدم للشاشة (مثلاً بعد إغلاق أو إلغاء نافذة تأكيد النظام)
            delay(600)
            if (!isInstalling) return@launch

            val sessionId = activeSessionId
            if (sessionId != -1) {
                val sessionInfo = try {
                    packageManager.packageInstaller.getSessionInfo(sessionId)
                } catch (_: Exception) {
                    null
                }

                // إذا انتهت الجلسة أو تم إلغاؤها من نافذة النظام، نعيد زر "تثبيت" فوراً
                if (sessionInfo == null || !sessionInfo.isActive) {
                    withContext(Dispatchers.Main) {
                        if (!isFinishing && !isDestroyed && isInstalling) {
                            isInstalling = false
                            activeSessionId = -1
                            setupLogic(app)
                        }
                    }
                    return@launch
                }
            }

            // صمام أمان إضافي: إذا ظلت الجلسة معلقة بدون استجابة لأكثر من 4 ثوانٍ بعد العودة للشاشة، نعيد فحصها وتحرير الزر
            delay(3500)
            if (!isInstalling) return@launch
            val latestInfo = if (activeSessionId != -1) {
                try {
                    packageManager.packageInstaller.getSessionInfo(activeSessionId)
                } catch (_: Exception) {
                    null
                }
            } else null

            if (latestInfo == null || !latestInfo.isActive) {
                withContext(Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed && isInstalling) {
                        isInstalling = false
                        activeSessionId = -1
                        setupLogic(app)
                    }
                }
            }
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

        if (commentsQuery != null && commentsListener != null) {
            commentsQuery?.removeEventListener(commentsListener!!)
        }
    }

    private fun registerInstallReceiver() {
        val filter = IntentFilter().apply {
            addAction("com.fix.engine.abdullah.UPDATE_UI")
            addAction("com.fix.engine.abdullah.DOWNLOAD_STATE_CHANGED")
        }
        ContextCompat.registerReceiver(this, installStateReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
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
                        try {
                            startActivity(
                                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                    data = Uri.parse("package:$packageName")
                                }
                            )
                        } catch (e: Exception) {
                            try {
                                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES))
                            } catch (_: Exception) {}
                        }
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
                if (isFinishing || isDestroyed) return@withContext
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
                if (isFinishing || isDestroyed) return@runOnUiThread
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

                        val downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        val finalFile = File(downloadFolder, currentApp!!.getUniqueFileName())

                        showReadyToInstallNotification(currentApp!!, finalFile)

                        // 🛡️ إذا كان المستخدم داخل صفحة التطبيق، نبدأ التثبيت تلقائياً فوراً
                        // أما إذا اكتمل التحميل والمستخدم خارج المتجر، نجهز زر "تثبيت" لكي لا يحظر النظام نافذة التأكيد في الخلفية
                        if (isActivityVisible && !isFinishing && !isDestroyed) {
                            isInstalling = true
                            showInstallingStateUi(
                                statusText = "جاري تجهيز التثبيت...",
                                subText = "اكتمل التنزيل، جاري البدء في التثبيت..."
                            )
                            triggerAdvancedInstall(finalFile)
                        } else {
                            isInstalling = false
                            activeSessionId = -1
                            setupLogic(currentApp!!)
                        }
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
                    if (isFinishing || isDestroyed) return@runOnUiThread
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

    private fun showInstallingStateUi(
        statusText: String = "جاري التثبيت في الخلفية...",
        subText: String = "يرجى الانتظار حتى اكتمال التثبيت..."
    ) {
        binding.btnInstallState.visibility = View.GONE
        binding.layoutInstalledState.visibility = View.GONE
        binding.btnShareApk.visibility = View.GONE
        binding.btnDeleteApk.visibility = View.GONE
        binding.cardDownloadingState.visibility = View.VISIBLE
        binding.btnCancelDownload.visibility = View.GONE
        binding.progressDownload.isIndeterminate = true
        binding.tvDownloadPercent.text = statusText
        binding.tvDownloadSize.text = subText
    }

    private fun triggerAdvancedInstall(file: File) {
        if (!ensureInstallPermissionGranted()) {
            isInstalling = false
            activeSessionId = -1
            currentApp?.let { setupLogic(it) }
            return
        }

        val availableBytes = getAvailableInternalStorageBytes()
        val requiredBytes = getRequiredStorageForInstall(file)
        if (availableBytes < requiredBytes) {
            isInstalling = false
            activeSessionId = -1
            currentApp?.let { setupLogic(it) }
            showStorageFullDialog(requiredBytes, availableBytes, isBeforeDownload = false)
            return
        }

        // إخفاء زر التثبيت فوراً وإظهار الشريط الدائري طوال عملية التثبيت في الخلفية
        isInstalling = true
        showInstallingStateUi(
            statusText = "جاري تجهيز التثبيت...",
            subText = "يرجى الانتظار..."
        )

        lifecycleScope.launch(Dispatchers.IO) {
            delay(100)

            val isSplitBundle = AbdullahStoreApp.isSplitApksBundle(file)
            val isValidSingleApk = AbdullahStoreApp.isApkCompleteAndValid(this@AppDetailsActivity, file)

            if (!isSplitBundle && !isValidSingleApk) {
                file.delete()
                withContext(Dispatchers.Main) {
                    if (isFinishing || isDestroyed) return@withContext
                    isInstalling = false
                    activeSessionId = -1
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
                    if (!isFinishing && !isDestroyed) {
                        isInstalling = false
                        activeSessionId = -1
                        installWithStandardSystemIntent(file)
                    }
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                if (isFinishing || isDestroyed) return@withContext
                showInstallingStateUi(
                    statusText = if (isSplitBundle) {
                        "جاري تجميع وتثبيت حزمة APKM / XAPK..."
                    } else {
                        "جاري التثبيت في الخلفية..."
                    },
                    subText = "يرجى الانتظار حتى اكتمال التثبيت..."
                )
            }

            var session: PackageInstaller.Session? = null
            try {
                val packageInstaller = packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    currentApp?.packageName?.let { setAppPackageName(it) }
                    // 🛡️ تصريح رسمي لخدمة Google Play Protect بأن التثبيت يتم بطلب المستخدم ومن متجر تطبيقات شرعي
                    setInstallReason(PackageManager.INSTALL_REASON_USER)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    }
                }

                if (isSplitBundle) {
                    ZipFile(file).use { zipFile ->
                        val apkEntries = zipFile.entries().asSequence()
                            .filter { !it.isDirectory && it.name.lowercase().endsWith(".apk") && it.size > 0L }
                            .toList()

                        if (apkEntries.isEmpty()) {
                            throw IllegalStateException("لا توجد ملفات APK صالحة داخل الحزمة")
                        }

                        val filteredEntries = filterCompatibleSplitEntries(apkEntries)
                        val totalUncompressedSize = filteredEntries.sumOf { it.size.coerceAtLeast(0L) }
                        if (totalUncompressedSize > 0L) {
                            params.setSize(totalUncompressedSize)
                        }

                        val sessionId = packageInstaller.createSession(params)
                        activeSessionId = sessionId
                        session = packageInstaller.openSession(sessionId)

                        filteredEntries.forEachIndexed { index, entry ->
                            val safeStreamName = "split_pkg_$index.apk"
                            val entrySize = if (entry.size > 0L) entry.size else -1L
                            zipFile.getInputStream(entry).use { inputStream ->
                                session!!.openWrite(safeStreamName, 0, entrySize).use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                    session!!.fsync(outputStream)
                                }
                            }
                        }

                        commitInstallerSession(session!!, sessionId)
                    }
                } else {
                    val sizeBytes = file.length()
                    if (sizeBytes > 0L) {
                        params.setSize(sizeBytes)
                    }

                    val sessionId = packageInstaller.createSession(params)
                    activeSessionId = sessionId
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
                try {
                    session?.abandon()
                } catch (_: Exception) {}
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    if (isFinishing || isDestroyed) return@withContext
                    isInstalling = false
                    activeSessionId = -1
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

    private fun filterCompatibleSplitEntries(entries: List<ZipEntry>): List<ZipEntry> {
        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull()?.lowercase()?.replace("-", "_") ?: "arm64_v8a"
        val allAbis = listOf("arm64_v8a", "armeabi_v7a", "armeabi", "x86_64", "x86")
        val allDpis = listOf("xxxhdpi", "xxhdpi", "xhdpi", "hdpi", "mdpi", "ldpi", "tvdpi")

        val selectedAbi = Build.SUPPORTED_ABIS
            .map { it.lowercase().replace("-", "_") }
            .firstOrNull { abi ->
                entries.any {
                    val name = File(it.name).name.lowercase().replace("-", "_")
                    name.contains("config.$abi") || name.contains(abi)
                }
            } ?: primaryAbi

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

            val matchedAbi = allAbis.firstOrNull { abi ->
                fileName.contains("config.$abi") || fileName.contains(".$abi.") || fileName.endsWith("_$abi.apk")
            }
            if (matchedAbi != null) {
                return@filter matchedAbi == selectedAbi
            }

            val matchedDpi = allDpis.firstOrNull { dpi ->
                fileName.contains("config.$dpi") || fileName.contains(".$dpi.")
            }
            if (matchedDpi != null && selectedDpi != null) {
                return@filter matchedDpi == selectedDpi
            }

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
        isInstalling = false
        activeSessionId = -1
        binding.cardDownloadingState.visibility = View.GONE
        binding.btnInstallState.visibility = View.VISIBLE
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

    private fun showInstalledStateDirectly(app: AppModel) {
        val pm = packageManager
        val downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val finalFile = File(downloadFolder, app.getUniqueFileName())
        val isApkReady = AbdullahStoreApp.isApkCompleteAndValid(this, finalFile)

        binding.cardDownloadingState.visibility = View.GONE
        binding.btnInstallState.visibility = View.GONE
        binding.layoutInstalledState.visibility = View.VISIBLE

        binding.btnDeleteApk.visibility = if (isApkReady) View.VISIBLE else View.GONE
        binding.btnShareApk.visibility = if (isApkReady) View.VISIBLE else View.GONE
        if (isApkReady) {
            binding.btnDeleteApk.setOnClickListener { deleteAppFiles(app) }
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
    }

    private fun setupLogic(app: AppModel) {
        if (isDownloading || isFinishing || isDestroyed) return

        val pm = packageManager
        val fileName = app.getUniqueFileName()
        val downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val finalFile = File(downloadFolder, fileName)

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
                // التطبيق مثبت ومحدث لآخر إصدار: نوقف حالة التثبيت ونعرض أزرار (فتح / إلغاء التثبيت)
                isInstalling = false
                activeSessionId = -1
                showInstalledStateDirectly(app)
                return
            }

            // إذا كانت عملية التثبيت في الخلفية جارية حالياً، نحافظ على الشريط الدائري ونمنع ظهور زر "تثبيت"
            if (isInstalling) {
                showInstallingStateUi()
                return
            }

            val isApkReady = AbdullahStoreApp.isApkCompleteAndValid(this, finalFile)
            if (!isApkReady && finalFile.exists()) {
                finalFile.delete()
            }
            binding.btnDeleteApk.visibility = if (isApkReady) View.VISIBLE else View.GONE
            if (isApkReady) {
                binding.btnDeleteApk.setOnClickListener { deleteAppFiles(app) }
            }

            handleInstallOrUpdateState(app, finalFile, isApkReady = isApkReady, isUpdate = true)
        } catch (e: PackageManager.NameNotFoundException) {
            // إذا كانت عملية التثبيت في الخلفية جارية حالياً، نحافظ على الشريط الدائري ونمنع ظهور زر "تثبيت"
            if (isInstalling) {
                showInstallingStateUi()
                return
            }

            val isApkReady = AbdullahStoreApp.isApkCompleteAndValid(this, finalFile)
            if (!isApkReady && finalFile.exists()) {
                finalFile.delete()
            }
            binding.btnDeleteApk.visibility = if (isApkReady) View.VISIBLE else View.GONE
            if (isApkReady) {
                binding.btnDeleteApk.setOnClickListener { deleteAppFiles(app) }
            }

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
                        if (!isFinishing && !isDestroyed) {
                            binding.txtStatDownloads.text = formatDownloads(count)
                        }
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        if (!isFinishing && !isDestroyed) {
                            binding.txtStatDownloads.text = "0"
                        }
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

    /**
     * 🔔 إشعار ذكي: يفتح صفحة التطبيق داخل المتجر إذا كان الملف بصيغة APKM/XAPK لكي يتولى المتجر تثبيته
     */
    private fun showReadyToInstallNotification(app: AppModel, file: File) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "INSTALL_CHANNEL"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, "تثبيت التطبيقات", NotificationManager.IMPORTANCE_HIGH)
                notificationManager.createNotificationChannel(channel)
            }

            val isSplitBundle = AbdullahStoreApp.isSplitApksBundle(file)

            val intent = if (isSplitBundle) {
                Intent(this@AppDetailsActivity, AppDetailsActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("APP_DATA", app)
                }
            } else {
                Intent(Intent.ACTION_VIEW).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val uri = FileProvider.getUriForFile(this@AppDetailsActivity, "$packageName.fileprovider", file)
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } else {
                        setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
                    }
                }
            }

            val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getActivity(this, app.name.hashCode(), intent, pendingFlags)

            val notification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_notification_transparent)
                .setContentTitle("اكتمل تنزيل ${app.name}")
                .setContentText("اضغط هنا للبدء في التثبيت")
                .setColor(Color.parseColor("#4CAF50"))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            notificationManager.notify(app.name.hashCode(), notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
    // 💬 محرك التعليقات والتقييمات الاحترافي الحي عبر Firebase Realtime Database
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

        // ربط جميع مداخل التقييم (الزر الرئيسي، شريط الإحصائيات العلوي، البطاقة الفارغة، والنجوم الـ 5 التفاعلية)
        binding.btnAddComment.setOnClickListener {
            showAddCommentDialog(commentsRef, initialStars = 5)
        }
        binding.layoutStatRating.setOnClickListener {
            showAddCommentDialog(commentsRef, initialStars = 5)
        }
        binding.txtEmptyComments.setOnClickListener {
            showAddCommentDialog(commentsRef, initialStars = 5)
        }

        val quickStarViews = listOf(
            binding.starRate1,
            binding.starRate2,
            binding.starRate3,
            binding.starRate4,
            binding.starRate5
        )
        quickStarViews.forEachIndexed { index, starView ->
            starView.setOnClickListener { view ->
                val chosenStars = index + 1
                // تحديث بصري سريع للنجوم قبل فتح نافذة التقييم
                quickStarViews.forEachIndexed { i, tv ->
                    tv.text = if (i < chosenStars) "★" else "☆"
                    tv.setTextColor(if (i < chosenStars) Color.parseColor("#FFB300") else Color.parseColor("#888888"))
                }
                view.animate().scaleX(1.25f).scaleY(1.25f).setDuration(110).withEndAction {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(110).start()
                    showAddCommentDialog(commentsRef, initialStars = chosenStars)
                }.start()
            }
        }

        commentsQuery = commentsRef.limitToLast(40)
        commentsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val commentsList = mutableListOf<Map<String, Any>>()
                var totalStars = 0L

                for (child in snapshot.children) {
                    val user = child.child("user").getValue(String::class.java) ?: "مستخدم"
                    val text = child.child("text").getValue(String::class.java) ?: continue
                    val stars = (child.child("stars").getValue(Long::class.java) ?: 5L).coerceIn(1L, 5L)
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
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (commentsList.isEmpty()) {
                        binding.txtEmptyComments.visibility = View.VISIBLE
                        binding.rvComments.visibility = View.GONE
                        binding.txtStatRating.text = "5.0 ★"
                        binding.txtStatReviewsCount.text = "قيّم الآن"
                        binding.txtRatingBigScore.text = "5.0"
                        binding.txtRatingBigStars.text = "★★★★★"
                        binding.txtRatingBigCount.text = "جديد • كن أول المقيّمين"
                    } else {
                        binding.txtEmptyComments.visibility = View.GONE
                        binding.rvComments.visibility = View.VISIBLE
                        val avg = totalStars.toDouble() / commentsList.size
                        val avgFormatted = String.format(Locale.US, "%.1f", avg)
                        val roundedStars = kotlin.math.round(avg).toInt().coerceIn(1, 5)

                        binding.txtStatRating.text = "$avgFormatted ★"
                        binding.txtStatReviewsCount.text = "${commentsList.size} مراجعة"
                        binding.txtRatingBigScore.text = avgFormatted
                        binding.txtRatingBigStars.text = "★".repeat(roundedStars) + "☆".repeat(5 - roundedStars)
                        binding.txtRatingBigCount.text = "${commentsList.size} مراجعة موثّقة"
                        binding.rvComments.adapter = SimpleCommentsAdapter(commentsList)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {}
        }
        commentsQuery?.addValueEventListener(commentsListener!!)
    }

    private fun showAddCommentDialog(commentsRef: DatabaseReference, initialStars: Int = 5) {
        if (isFinishing || isDestroyed) return
        val prefs = getSharedPreferences("FixEnginePrefs", Context.MODE_PRIVATE)
        val savedName = prefs.getString("user_display_name", "") ?: ""
        val density = resources.displayMetrics.density

        val colorPrimary = MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary)
        val colorOnSurface = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface)
        val colorOnSurfaceVariant = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant)
        val colorSurfaceVariant = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurfaceVariant)
        val colorGold = Color.parseColor("#FFB300")

        var selectedStars = initialStars.coerceIn(1, 5)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padH = (22 * density).toInt()
            val padTop = (12 * density).toInt()
            setPadding(padH, padTop, padH, (4 * density).toInt())
        }

        // 1. صندوق النجوم التفاعلي الفخم داخل النافذة
        val ratingCard = MaterialCardView(this).apply {
            radius = 18 * density
            cardElevation = 0f
            setCardBackgroundColor(colorSurfaceVariant)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (16 * density).toInt() }
        }

        val ratingBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val p = (14 * density).toInt()
            setPadding(p, p, p, p)
        }

        val tvRatingStatus = TextView(this).apply {
            textSize = 13.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(colorPrimary)
            gravity = Gravity.CENTER
        }

        val starsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = View.LAYOUT_DIRECTION_LTR
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (6 * density).toInt() }
        }

        val starButtons = mutableListOf<TextView>()

        fun updateStarsUi(stars: Int) {
            selectedStars = stars.coerceIn(1, 5)
            val label = when (selectedStars) {
                5 -> " ممتاز جداً • 5/5 😍"
                4 -> " جيد جداً • 4/5 👍"
                3 -> " جيد • 3/5 🙂"
                2 -> " مقبول • 2/5 😐"
                else -> " يحتاج تحسين • 1/5 😕"
            }
            tvRatingStatus.text = label
            starButtons.forEachIndexed { idx, starTv ->
                val isSelected = idx < selectedStars
                starTv.text = if (isSelected) "★" else "☆"
                starTv.setTextColor(if (isSelected) colorGold else colorOnSurfaceVariant)
            }
        }

        for (i in 1..5) {
            val starTv = TextView(this).apply {
                textSize = 32f
                gravity = Gravity.CENTER
                val sizePx = (46 * density).toInt()
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
                setOnClickListener { v ->
                    updateStarsUi(i)
                    v.animate().scaleX(1.25f).scaleY(1.25f).setDuration(100).withEndAction {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    }.start()
                }
            }
            starButtons.add(starTv)
            starsRow.addView(starTv)
        }

        updateStarsUi(selectedStars)
        ratingBox.addView(tvRatingStatus)
        ratingBox.addView(starsRow)
        ratingCard.addView(ratingBox)

        // 2. حقل الاسم بتصميم Material OutlinedBox
        val tilName = TextInputLayout(this).apply {
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            val corner = 14 * density
            setBoxCornerRadii(corner, corner, corner, corner)
            hint = "اسمك الظاهر في المراجعة (اختياري)"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * density).toInt() }
        }

        val etName = TextInputEditText(tilName.context).apply {
            setText(savedName)
            maxLines = 1
            isSingleLine = true
            setTextColor(colorOnSurface)
            textSize = 14f
        }
        tilName.addView(etName)

        // 3. حقل التعليق بتصميم Material OutlinedBox مع عداد حروف
        val tilComment = TextInputLayout(this).apply {
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            val corner = 14 * density
            setBoxCornerRadii(corner, corner, corner, corner)
            hint = "اكتب تجربتك أو رأيك حول التطبيق..."
            isCounterEnabled = true
            counterMaxLength = 250
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val etComment = TextInputEditText(tilComment.context).apply {
            minLines = 3
            maxLines = 5
            gravity = Gravity.TOP or Gravity.START
            setTextColor(colorOnSurface)
            textSize = 14f
        }
        tilComment.addView(etComment)

        container.addView(ratingCard)
        container.addView(tilName)
        container.addView(tilComment)

        MaterialAlertDialogBuilder(this, R.style.Theme_FixEngine_Dialog)
            .setTitle("إضافة تقييم ومراجعة ⭐")
            .setView(container)
            .setPositiveButton("نشر المراجعة") { dialog, _ ->
                val name = etName.text?.toString()?.trim().orEmpty().ifBlank { "مستخدم المتجر" }
                val comment = etComment.text?.toString()?.trim().orEmpty()
                val stars = selectedStars.coerceIn(1, 5)

                if (comment.length < 3) {
                    Toast.makeText(this, "يرجى كتابة تعليق واضح أولاً (3 أحرف على الأقل)", Toast.LENGTH_SHORT).show()
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
                        if (!isFinishing && !isDestroyed) {
                            Toast.makeText(this, "تم نشر تقييمك بنجاح! شكراً لمشاركتك ✨", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        if (!isFinishing && !isDestroyed) {
                            Toast.makeText(this, "تعذر نشر التعليق، تأكد من الاتصال بالإنترنت", Toast.LENGTH_SHORT).show()
                        }
                    }
                dialog.dismiss()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    /**
     * 🎨 محول برمجي احترافي لعرض بطاقات مراجعات المستخدمين بهوية بصرية فخمة
     * يدعم الوضع النهاري والليلي تلقائياً ويتمدد بأمان مع الخطوط الكبيرة
     */
    private inner class SimpleCommentsAdapter(
        private val items: List<Map<String, Any>>
    ) : RecyclerView.Adapter<SimpleCommentsAdapter.VH>() {

        inner class VH(
            val card: MaterialCardView,
            val tvAvatar: TextView,
            val tvUserName: TextView,
            val tvStars: TextView,
            val tvDate: TextView,
            val tvBody: TextView
        ) : RecyclerView.ViewHolder(card)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val density = parent.resources.displayMetrics.density

            val colorPrimary = MaterialColors.getColor(parent, androidx.appcompat.R.attr.colorPrimary)
            val colorPrimaryContainer = MaterialColors.getColor(parent, com.google.android.material.R.attr.colorPrimaryContainer)
            val colorOnPrimaryContainer = MaterialColors.getColor(parent, com.google.android.material.R.attr.colorOnPrimaryContainer)
            val colorSurfaceVariant = MaterialColors.getColor(parent, com.google.android.material.R.attr.colorSurfaceVariant)
            val colorOnSurface = MaterialColors.getColor(parent, com.google.android.material.R.attr.colorOnSurface)
            val colorOnSurfaceVariant = MaterialColors.getColor(parent, com.google.android.material.R.attr.colorOnSurfaceVariant)
            val colorOutlineVariant = MaterialColors.getColor(parent, com.google.android.material.R.attr.colorOutlineVariant)

            val card = MaterialCardView(ctx).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (12 * density).toInt() }
                radius = 20 * density
                cardElevation = 0f
                strokeWidth = (1 * density).toInt()
                strokeColor = colorOutlineVariant
                setCardBackgroundColor(colorSurfaceVariant)
            }

            val rootBox = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                val p = (16 * density).toInt()
                setPadding(p, p, p, p)
            }

            // الصف العلوي: الصورة الرمزية + اسم المستخدم والنجوم + التاريخ
            val headerRow = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            // 1. دائرة الحرف الأول من اسم المستخدم (Avatar)
            val avatarSize = (42 * density).toInt()
            val tvAvatar = TextView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(avatarSize, avatarSize)
                gravity = Gravity.CENTER
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(colorOnPrimaryContainer)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(colorPrimaryContainer)
                }
            }

            // 2. عمود الاسم وشريط النجوم
            val userInfoCol = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginStart = (12 * density).toInt()
                    marginEnd = (8 * density).toInt()
                }
            }

            val tvUserName = TextView(ctx).apply {
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(colorOnSurface)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }

            val tvStars = TextView(ctx).apply {
                textSize = 12.5f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.parseColor("#FFB300"))
                setPadding(0, (2 * density).toInt(), 0, 0)
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }

            userInfoCol.addView(tvUserName)
            userInfoCol.addView(tvStars)

            // 3. شارة التاريخ الأنيقة
            val tvDate = TextView(ctx).apply {
                textSize = 11f
                setTextColor(colorOnSurfaceVariant)
                val ph = (8 * density).toInt()
                val pv = (3 * density).toInt()
                setPadding(ph, pv, ph, pv)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 10 * density
                    setColor(Color.argb(25, Color.red(colorPrimary), Color.green(colorPrimary), Color.blue(colorPrimary)))
                }
            }

            headerRow.addView(tvAvatar)
            headerRow.addView(userInfoCol)
            headerRow.addView(tvDate)

            // خط فاصل خفيف بين الهيدر ونص المراجعة
            val divider = View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (1 * density).toInt().coerceAtLeast(1)
                ).apply {
                    topMargin = (12 * density).toInt()
                    bottomMargin = (10 * density).toInt()
                }
                setBackgroundColor(colorOutlineVariant)
                alpha = 0.45f
            }

            // 4. نص المراجعة (يدعم التمدد والخطوط الكبيرة بسلاسة)
            val tvBody = TextView(ctx).apply {
                textSize = 13.5f
                setTextColor(colorOnSurface)
                setLineSpacing(4 * density, 1.05f)
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }

            rootBox.addView(headerRow)
            rootBox.addView(divider)
            rootBox.addView(tvBody)
            card.addView(rootBox)

            return VH(card, tvAvatar, tvUserName, tvStars, tvDate, tvBody)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val user = (item["user"] as? String)?.trim().orEmpty().ifBlank { "مستخدم" }
            val stars = (item["stars"] as? Long ?: 5L).toInt().coerceIn(1, 5)
            val time = (item["time"] as? String)?.trim().orEmpty()
            val text = (item["text"] as? String)?.trim().orEmpty()

            val firstChar = user.firstOrNull()?.uppercaseChar()?.toString() ?: "م"
            holder.tvAvatar.text = firstChar
            holder.tvUserName.text = user

            val starsVisual = "★".repeat(stars) + "☆".repeat(5 - stars)
            holder.tvStars.text = "$starsVisual  ($stars.0)"

            if (time.isNotBlank()) {
                holder.tvDate.visibility = View.VISIBLE
                holder.tvDate.text = time
            } else {
                holder.tvDate.visibility = View.GONE
            }

            holder.tvBody.text = text
        }

        override fun getItemCount(): Int = items.size
    }
}
