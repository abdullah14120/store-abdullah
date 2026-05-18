package com.fix.engine.abdullah.ui.details

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.io.File
import kotlin.concurrent.thread

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Abdullah Store
 * Feature: Legacy FileProvider Installer with Advanced Interactive Progress UI Flow
 */
class AppDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppDetailsBinding
    private lateinit var downloadManager: AndroidDownloadManager
    private lateinit var tracker: DownloadTracker
    private var isDownloading = false
    private var currentApp: AppModel? = null
    
    // ⬇️ قم بلصق رابط سيرفرك الخاص هنا داخل دالة getInstance
private val databaseRef = FirebaseDatabase.getInstance("https://abdullah-store-a95ed.firebasestorage.app")
    .getReference("download_stats")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.google.firebase.FirebaseApp.initializeApp(this)
        binding = ActivityAppDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        downloadManager = AndroidDownloadManager(this)
        tracker = DownloadTracker(this)

        setupToolbar()

        val appData = intent.getSerializableExtra("APP_DATA") as? AppModel

        if (appData != null) {
            currentApp = appData
            displayDetails(appData)
            setupLogic(appData)
            checkCurrentStatus(appData)
            loadDownloadsCount(appData.packageName)
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
            binding.btnInstallLarge.setOnClickListener { installApkLegacy(finalFile) }
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

            badgeDownloads.root.findViewById<TextView>(R.id.txtLabel)?.text = "التنزيلات"
            badgeDownloads.root.findViewById<TextView>(R.id.txtValue)?.text = "..."

            Glide.with(this@AppDetailsActivity)
                .load(app.iconUrl)
                .placeholder(R.drawable.ic_launcher_foreground)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(imgDetailsIcon)
        }
    }

    private fun loadDownloadsCount(packageName: String) {
    // تحويل الأحرف كلها إلى صغيرة (lowercase) وتغيير النقاط لضمان التطابق التام في السيرفر
    val safeKey = packageName.trim().lowercase().replace(".", "_")
    
    thread {
        databaseRef.child(safeKey).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // الفحص الذكي: إذا كان المسار موجوداً نأخذ القيمة، وإذا لم يكن موجوداً نضع 0 بدلاً من النقاط المعلقة
                val count = if (snapshot.exists()) {
                    snapshot.getValue(Long::class.java) ?: 0L
                } else {
                    0L
                }
                
                runOnUiThread {
                    binding.badgeDownloads.root.findViewById<TextView>(R.id.txtValue)?.text = formatDownloads(count)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                runOnUiThread {
                    // في حال فشل الاتصال المؤقت، نظهر 0 لكي لا تبقى النقاط معلقة
                    binding.badgeDownloads.root.findViewById<TextView>(R.id.txtValue)?.text = "0"
                }
            }
        })
    }
}

private fun incrementDownloadCount(packageName: String) {
    // استخدام نفس الصيغة النظيفة (lowercase) لضمان الزيادة في نفس مكان القراءة تماماً
    val safeKey = packageName.trim().lowercase().replace(".", "_")
    
    thread {
        databaseRef.child(safeKey).setValue(com.google.firebase.database.ServerValue.increment(1))
            .addOnFailureListener { e ->
                e.printStackTrace()
            }
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
        binding.btnInstallLarge.visibility = View.GONE
        binding.btnOpenApp.visibility = View.GONE
        binding.layoutDownloadProgress.visibility = View.VISIBLE

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
                            btnInstallLarge.setOnClickListener { installApkLegacy(finalFile) }
                        }
                    }
                }
            }
        }
    }

    /**
     * تشغيل مثبت النظام التقليدي الآمن مع محاكاة نصوص التحميل التفاعلية للحفاظ على جمالية التصميم
     */
    private fun installApkLegacy(file: File) {
        if (!file.exists()) {
            Toast.makeText(this, "المعذرة، ملف الـ APK غير موجود!", Toast.LENGTH_SHORT).show()
            return
        }

        // تحويل أزرار الواجهة وعرض نصوص المحاكاة التفاعلية لشريط التقدم
        runOnUiThread {
            binding.btnInstallLarge.visibility = View.GONE
            binding.layoutDownloadProgress.visibility = View.VISIBLE
            binding.downloadProgress.progress = 20
            binding.txtDownloadPercent.text = "20%"
            binding.txtDownloadSpeed.text = "جاري تحضير ملفات التثبيت..."
            binding.txtDownloadETA.text = "يرجى الانتظار"
        }

        thread {
            try {
                // محاكاة سريعة في الخلفية لإعطاء المستخدم إحساساً باستجابة المتجر والتحضير
                Thread.sleep(600)
                runOnUiThread {
                    binding.downloadProgress.progress = 75
                    binding.txtDownloadPercent.text = "75%"
                    binding.txtDownloadSpeed.text = "جاري التثبيت النهائي في النظام... 🚀"
                }
                Thread.sleep(500)
                runOnUiThread {
                    binding.downloadProgress.progress = 100
                    binding.txtDownloadPercent.text = "100%"
                }
                Thread.sleep(200)

                // تحضير الـ Uri الآمن وإطلاق المثبت الرسمي للأندرويد
                val providerAuthority = "$packageName.provider"
                val uri: Uri = FileProvider.getUriForFile(this@AppDetailsActivity, providerAuthority, file)
                
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this@AppDetailsActivity, "فشل تحليل الحزمة، قد يكون الملف غير مكتمل", Toast.LENGTH_LONG).show()
                    if (file.exists()) file.delete()
                }
            } finally {
                // إرجاع الواجهة لوضعها الطبيعي لكي يتمكن المستخدم من الضغط مجدداً إذا ألغى التثبيت يدوياً
                runOnUiThread {
                    binding.layoutDownloadProgress.visibility = View.GONE
                    binding.btnInstallLarge.visibility = View.VISIBLE
                    binding.btnInstallLarge.text = "تثبيت الآن"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::tracker.isInitialized) tracker.stopTracking()
    }
}
