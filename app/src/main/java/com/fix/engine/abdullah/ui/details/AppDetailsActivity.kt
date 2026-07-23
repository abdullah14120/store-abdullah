package com.fix.engine.abdullah.ui.details

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.fix.engine.abdullah.R
import com.fix.engine.abdullah.core.AppNotificationCore
import com.fix.engine.abdullah.core.PackageInstallerCore
import com.fix.engine.abdullah.core.ShareManagerCore
import com.fix.engine.abdullah.core.StorageManagerCore
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

class AppDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppDetailsBinding
    private lateinit var downloadManager: AndroidDownloadManager
    private lateinit var tracker: DownloadTracker

    private var isDownloading = false
    private var currentApp: AppModel? = null

    private lateinit var databaseRef: DatabaseReference
    private var downloadsListener: ValueEventListener? = null
    private var activeFirebaseKey: String? = null

    private val installStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.fix.engine.abdullah.UPDATE_UI") {
                val pkgName = intent.getStringExtra("PACKAGE_NAME")
                val isSuccess = intent.getBooleanExtra("IS_SUCCESS", false)

                if (pkgName == currentApp?.packageName) {
                    runOnUiThread {
                        binding.btnInstallState.isEnabled = true

                        if (isSuccess) {
                            currentApp?.getUniqueFileName()?.let { fileName ->
                                StorageManagerCore.cleanupAppFiles(this@AppDetailsActivity, fileName)
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

            setupLogic(appData)
            checkCurrentStatus(appData)
        } else {
            Toast.makeText(this, "بيانات التطبيق غير صالحة", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        currentApp?.let { app ->
            setupLogic(app)
            checkCurrentStatus(app)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::tracker.isInitialized) tracker.stopTracking()
        unregisterReceiver(installStateReceiver)

        if (::databaseRef.isInitialized && activeFirebaseKey != null && downloadsListener != null) {
            databaseRef.child(activeFirebaseKey!!).removeEventListener(downloadsListener!!)
        }
    }

    private fun registerInstallReceiver() {
        val filter = IntentFilter("com.fix.engine.abdullah.UPDATE_UI")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(installStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(installStateReceiver, filter)
        }
    }

    private fun initFirebase() {
        try {
            val decodedFirebaseUrl = PackageInstallerCore.getSecureFirebaseUrl()
            databaseRef = FirebaseDatabase.getInstance(decodedFirebaseUrl).getReference("download_stats")
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "خطأ في الاتصال بقاعدة البيانات", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkStoragePermissionAndDownload(app: AppModel) {
        if (!StorageManagerCore.hasEnoughStorage()) {
            showStorageFullDialog()
            return
        }

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

    private fun startNewDownload(app: AppModel) {
        isDownloading = true
        binding.btnInstallState.visibility = View.GONE
        binding.layoutInstalledState.visibility = View.GONE
        binding.btnShareApk.visibility = View.GONE
        binding.btnDeleteApk.visibility = View.GONE
        binding.cardDownloadingState.visibility = View.VISIBLE

        binding.btnCancelDownload.visibility = View.VISIBLE
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
                        btnCancelDownload.visibility = View.VISIBLE
                        progressDownload.isIndeterminate = false
                        progressDownload.progress = progress
                        tvDownloadPercent.text = "جاري التنزيل... $progress%"
                    } else {
                        btnCancelDownload.visibility = View.VISIBLE
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

                        AppNotificationCore.showReadyToInstallNotification(this@AppDetailsActivity, currentApp!!.name, finalFile)

                        binding.btnShareApk.visibility = View.VISIBLE
                        binding.btnShareApk.setOnClickListener { executeShareApk(finalFile, currentApp!!.name) }

                        binding.btnDeleteApk.visibility = View.VISIBLE
                        binding.btnDeleteApk.setOnClickListener { executeDeleteAppFiles(currentApp!!) }

                        binding.btnInstallState.setOnClickListener { triggerAdvancedInstall(finalFile) }
                        triggerAdvancedInstall(finalFile)
                    }
                }
            }
        }
    }

    private fun triggerAdvancedInstall(file: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            delay(500)

            if (!file.exists() || file.length() == 0L) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AppDetailsActivity, "عذراً، الملف غير صالح للتثبيت.", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val packageInfo = packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            if (packageInfo == null) {
                file.delete()
                withContext(Dispatchers.Main) {
                    binding.cardDownloadingState.visibility = View.GONE
                    binding.btnInstallState.visibility = View.VISIBLE
                    binding.btnInstallState.text = "إعادة التنزيل"
                    Toast.makeText(this@AppDetailsActivity, "حزمة التطبيق تالفة، يرجى إعادة التنزيل.", Toast.LENGTH_LONG).show()
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                binding.btnInstallState.visibility = View.GONE
                binding.cardDownloadingState.visibility = View.VISIBLE
                binding.btnCancelDownload.visibility = View.GONE
                binding.progressDownload.isIndeterminate = true
                binding.tvDownloadPercent.text = "جاري التثبيت في الخلفية..."
                binding.tvDownloadSize.text = "يرجى الانتظار، سيتم الفتح تلقائياً"
            }

            try {
                val success = PackageInstallerCore.createInstallSession(this@AppDetailsActivity, file)
                if (!success) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AppDetailsActivity, "فشل فتح جلسة التثبيت.", Toast.LENGTH_LONG).show()
                    }
                }
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
                    binding.btnCancelDownload.visibility = View.VISIBLE
                    startTrackingProcess(activeDownload.id)
                }
            }
        }
    }

    private fun setupLogic(app: AppModel) {
        val pm = packageManager
        val fileName = app.getUniqueFileName()
        val downloadFolder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val finalFile = File(downloadFolder, fileName)
        val tempFile = File(downloadFolder, "$fileName.tmp")

        if (finalFile.exists() || tempFile.exists()) {
            binding.btnDeleteApk.visibility = View.VISIBLE
            binding.btnDeleteApk.setOnClickListener { executeDeleteAppFiles(app) }
        } else {
            binding.btnDeleteApk.visibility = View.GONE
        }

        try {
            val pInfo = pm.getPackageInfo(app.packageName, 0)
            val installedVer = pInfo.versionName ?: ""

            if (app.versionName.trim() != installedVer.trim()) {
                handleInstallOrUpdateState(app, finalFile, isUpdate = true)
            } else {
                binding.btnInstallState.visibility = View.GONE
                binding.layoutInstalledState.visibility = View.VISIBLE

                binding.btnShareApk.visibility = if (finalFile.exists()) View.VISIBLE else View.GONE
                if (finalFile.exists()) {
                    binding.btnShareApk.setOnClickListener { executeShareApk(finalFile, app.name) }
                }

                binding.btnOpenApp.setOnClickListener {
                    val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                    launchIntent?.let { startActivity(it) } ?: Toast.makeText(this, "لا يمكن فتح التطبيق", Toast.LENGTH_SHORT).show()
                }

                binding.btnUninstall.setOnClickListener {
                    val intent = Intent(Intent.ACTION_DELETE).apply {
                        data = Uri.parse("package:${app.packageName}")
                    }
                    startActivity(intent)
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            handleInstallOrUpdateState(app, finalFile, isUpdate = false)
        }
    }

    private fun handleInstallOrUpdateState(app: AppModel, finalFile: File, isUpdate: Boolean) {
        binding.layoutInstalledState.visibility = View.GONE
        binding.cardDownloadingState.visibility = View.GONE
        binding.btnInstallState.visibility = View.VISIBLE

        if (finalFile.exists()) {
            binding.btnInstallState.text = "تثبيت"
            binding.btnShareApk.visibility = View.VISIBLE
            binding.btnShareApk.setOnClickListener { executeShareApk(finalFile, app.name) }
            binding.btnInstallState.setOnClickListener { triggerAdvancedInstall(finalFile) }
        } else {
            binding.btnInstallState.text = if (isUpdate) "تحديث" else "تنزيل"
            binding.btnShareApk.visibility = View.GONE
            binding.btnInstallState.setOnClickListener { checkStoragePermissionAndDownload(app) }
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
            count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
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

    private fun executeShareApk(file: File, appName: String) {
        val shareIntent = ShareManagerCore.getShareIntent(this, file, appName)
        if (shareIntent != null) {
            startActivity(Intent.createChooser(shareIntent, "مشاركة تطبيق $appName عبر:"))
        } else {
            Toast.makeText(this, "عذراً، ملف الـ APK غير متوفر للمشاركة!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun executeDeleteAppFiles(app: AppModel) {
        val isDeleted = StorageManagerCore.cleanupAppFiles(this, app.getUniqueFileName())
        if (isDeleted) {
            Toast.makeText(this, "تم تنظيف ملفات التنزيل بنجاح", Toast.LENGTH_SHORT).show()
        }
        binding.btnShareApk.visibility = View.GONE
        binding.btnDeleteApk.visibility = View.GONE
        setupLogic(app)
    }
}
