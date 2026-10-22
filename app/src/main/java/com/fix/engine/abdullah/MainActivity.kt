package com.fix.engine.abdullah

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.fix.engine.abdullah.data.model.AppModel
import com.fix.engine.abdullah.databinding.ActivityMainBinding
import com.fix.engine.abdullah.ui.adapter.MainPagerAdapter
import com.fix.engine.abdullah.ui.details.AppDetailsActivity
import com.fix.engine.abdullah.ui.viewmodel.MainViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: متجر Abdullah (Official Runtime Core)
 * Feature: JNI C++ Security, Smart Category Chips, Storage Manager & Live Firebase Announcements
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    // 🌐 أدوات مراقبة حالة الشبكة الفورية
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback
    private var isNetworkCallbackRegistered = false
    private var isDataLoadedSuccessfully = false

    // 🔔 متغير لمنع تكرار الإشعار المزعج عند تدوير الشاشة أو البحث
    private var isNotificationSent = false

    // 🛡️ مسجل طلب الصلاحيات الحديث (Activity Result API)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "تم رفض صلاحية الإشعارات", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        init {
            System.loadLibrary("native-lib")
        }
    }

    private external fun verifySignatureNative(context: Context)
    private external fun getSecureRepoUrl(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🚨 درع الحماية النيتف
        verifySignatureNative(applicationContext)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTabs()
        setupNavigationDrawer()
        setupObservers()
        setupSearchLogic()
        setupSearchAnimation()
        observeStoreAnnouncement()

        checkNotificationPermission()

        lifecycleScope.launch {
            delay(1000)
            checkInstallPermission()
        }

        refreshData()
    }

    private fun registerNetworkMonitoring() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                runOnUiThread {
                    if (!isDataLoadedSuccessfully) {
                        Toast.makeText(this@MainActivity, "تم استعادة الاتصال! جاري مزامنة المتجر... 🔄", Toast.LENGTH_SHORT).show()
                        refreshData()
                    } else {
                        Toast.makeText(this@MainActivity, "متصل بالإنترنت ✨", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "عذراً، انقطع الاتصال بالإنترنت!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        isNetworkCallbackRegistered = true
    }

    override fun onStart() {
        super.onStart()
        registerNetworkMonitoring()
    }

    override fun onStop() {
        super.onStop()
        if (isNetworkCallbackRegistered) {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            isNetworkCallbackRegistered = false
        }
    }

    private fun setupNavigationDrawer() {
        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_settings -> showSettingsAndStorageDialog()
                R.id.nav_dev_about -> showAboutDeveloperDialog()
                R.id.nav_add_app -> showAddAppDeveloperDialog()
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun showAboutDeveloperDialog() {
        if (isFinishing || isDestroyed) return
        MaterialAlertDialogBuilder(this, R.style.Theme_FixEngine_Dialog)
            .setTitle("حول المطور")
            .setMessage("تم تطوير المتجر بواسطة م/ عبدالله التميمي.\nنهدف إلى تقديم تجربة فريدة، آمنة واحترافية لإدارة وتحديث تطبيقات الأندرويد المتقدمة.")
            .setPositiveButton("حسناً", null)
            .show()
    }

    private fun showAddAppDeveloperDialog() {
        if (isFinishing || isDestroyed) return
        MaterialAlertDialogBuilder(this, R.style.Theme_FixEngine_Dialog)
            .setTitle("إضافة تطبيقك في المتجر")
            .setMessage("يمكنكم التواصل مباشرة على الواتساب الرقم 770034578 لإرسال تفاصيل تطبيقكم، والمراجعة البرمجية قبل الرفع.")
            .setPositiveButton("مراسلة الآن") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/967770034578")))
                } catch (e: Exception) {
                    Toast.makeText(this, "تطبيق واتساب غير مثبت في جهازك", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val prefs = getSharedPreferences("FixEnginePrefs", MODE_PRIVATE)
            val isDialogShown = prefs.getBoolean("install_dialog_shown", false)

            if (!packageManager.canRequestPackageInstalls() && !isDialogShown) {
                showInstallPermissionDialog()
            }
        }
    }

    private fun showInstallPermissionDialog() {
        if (isFinishing || isDestroyed) return
        val dialogView = LayoutInflater.from(this).inflate(R.layout.mtrl_alert_dialog, null)
        val dialog = MaterialAlertDialogBuilder(this, R.style.Theme_FixEngine_Dialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<MaterialButton>(R.id.btn_positive)?.setOnClickListener {
            getSharedPreferences("FixEnginePrefs", MODE_PRIVATE).edit().putBoolean("install_dialog_shown", true).apply()
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply { data = Uri.parse("package:\$packageName") })
            dialog.dismiss()
        }

        dialogView.findViewById<MaterialButton>(R.id.btn_negative)?.setOnClickListener {
            getSharedPreferences("FixEnginePrefs", MODE_PRIVATE).edit().putBoolean("install_dialog_shown", true).apply()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun checkMandatoryUpdate(apps: List<AppModel>) {
        val storeApp = apps.find { it.packageName == packageName } ?: return

        try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }

            val installedVersion = pInfo.versionName ?: ""

            // 🧠 مقارنة ذكية: لا يظهر التحديث الإجباري إلا إذا كان إصدار السيرفر أحدث فعلياً من المثبت
            if (compareVersionNames(storeApp.versionName, installedVersion) > 0) {
                showMandatoryUpdateDialog(storeApp)
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
            return if (serverVersion.trim() != installedVersion.trim()) 1 else 0
        }
        return 0
    }

    private fun showMandatoryUpdateDialog(storeApp: AppModel) {
        if (isFinishing || isDestroyed) return
        MaterialAlertDialogBuilder(this, R.style.Theme_FixEngine_Dialog)
            .setTitle("تحديث إجباري!")
            .setMessage("يوجد إصدار جديد من متجر Abdullah يحل بعض المشاكل التقنية. يرجى التحديث للمتابعة.")
            .setCancelable(false)
            .setPositiveButton("تحديث الآن") { _, _ ->
                val intent = Intent(this, AppDetailsActivity::class.java).apply {
                    putExtra("APP_DATA", storeApp)
                }
                startActivity(intent)
                finish()
            }
            .setNegativeButton("خروج") { _, _ -> finishAffinity() }
            .show()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "store_updates_channel",
                "تحديثات المتجر",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "تنبيهات تلقائية عند توفر تحديثات جديدة للتطبيقات المثبتة" }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun sendUpdateNotification(updatesCount: Int) {
        createNotificationChannel()
        val intent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)

        val builder = NotificationCompat.Builder(this, "store_updates_channel")
            .setSmallIcon(R.drawable.ic_notification_transparent)
            .setContentTitle("تحديثات متوفرة لـ تطبيقاتك! 🚀")
            .setContentText("يوجد عدد (\$updatesCount) من تطبيقاتك تمتلك إصدارات محدثة، قم بتثبيتها الآن.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(1001, builder.build())
    }

    private fun setupTabs() {
        binding.viewPagerMain.adapter = MainPagerAdapter(this)
        TabLayoutMediator(binding.tabLayoutMain, binding.viewPagerMain) { tab, position ->
            tab.text = if (position == 0) "التطبيقات" else "التحديثات"
        }.attach()
    }

    private fun setupSearchLogic() {
        // 1. مراقبة البحث وإظهار/إخفاء زر المسح (X)
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                binding.btnClearSearch.isVisible = query.isNotEmpty()
                viewModel.filterApps(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 2. زر مسح النص بضغطة واحدة
        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.setText("")
            binding.etSearch.clearFocus()
        }

        // 3. زر التحديث الفوري من السيرفر مع حركة دوران
        binding.btnRefreshMain.setOnClickListener { view ->
            view.animate().rotationBy(360f).setDuration(600).start()
            Toast.makeText(this, "جاري تحديث قائمة التطبيقات... 🔄", Toast.LENGTH_SHORT).show()
            try {
                val secureUrl = getSecureRepoUrl()
                viewModel.forceReloadApps(secureUrl.toByteArray(Charsets.UTF_8), 0.toByte())
            } catch (e: Exception) {
                refreshData()
            }
        }

        // 4. تفعيل شريط التصنيفات السريعة (Chips)
        binding.chipGroupCategories.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: R.id.chipAll
            when (checkedId) {
                R.id.chipAll -> viewModel.filterApps(binding.etSearch.text.toString())
                R.id.chipAbMods -> viewModel.filterApps("ABWhatsApp")
                R.id.chipObMods -> viewModel.filterApps("عمر")
                R.id.chipShieldMods -> viewModel.filterApps("درع الحماية")
                R.id.chipInstalledOnly -> viewModel.filterInstalledOnly()
            }
        }
    }

    private fun setupSearchAnimation() {
        val colorPrimary = ContextCompat.getColor(this, R.color.md_theme_d_primary)
        val colorOutlineVariant = ContextCompat.getColor(this, R.color.md_theme_d_outlineVariant)

        binding.etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.searchCard.animate().scaleX(1.01f).scaleY(1.01f).setDuration(250).start()
                binding.searchCard.strokeWidth = 2
                binding.searchCard.strokeColor = colorPrimary
            } else {
                binding.searchCard.animate().scaleX(1f).scaleY(1f).setDuration(250).start()
                binding.searchCard.strokeWidth = 1
                binding.searchCard.strokeColor = colorOutlineVariant
            }
        }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { binding.progressBar.isVisible = it }

        viewModel.appsUiStateList.observe(this) { uiStates ->
            if (!uiStates.isNullOrEmpty()) {
                isDataLoadedSuccessfully = true
                val apps = uiStates.map { it.app }
                checkMandatoryUpdate(apps)
            }
        }

        viewModel.updatesUiStateList.observe(this) { updateStates ->
            if (updateStates != null) {
                val updateCount = updateStates.size
                val updatesTab = binding.tabLayoutMain.getTabAt(1)
                val badge = updatesTab?.orCreateBadge

                if (updateCount > 0) {
                    badge?.isVisible = true
                    badge?.number = updateCount
                    badge?.backgroundColor = ContextCompat.getColor(this@MainActivity, R.color.md_theme_d_error)

                    if (binding.etSearch.text.isNullOrBlank() && !isNotificationSent) {
                        sendUpdateNotification(updateCount)
                        isNotificationSent = true
                    }
                } else {
                    badge?.isVisible = false
                }
            }
        }

        viewModel.errorMessage.observe(this) {
            it?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                isDataLoadedSuccessfully = false
            }
        }
    }

    private fun refreshData() {
        try {
            val secureUrl = getSecureRepoUrl()
            val urlBytes = secureUrl.toByteArray(Charsets.UTF_8)
            viewModel.loadApps(urlBytes, 0.toByte())
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ في معالجة بوابة الأمان", Toast.LENGTH_LONG).show()
            isDataLoadedSuccessfully = false
        }
    }

    private fun showSettingsAndStorageDialog() {
        if (isFinishing || isDestroyed) return

        val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val apkFiles = downloadsDir?.listFiles { file ->
            file.isFile && (file.name.endsWith(".apk") || file.name.endsWith(".tmp"))
        } ?: emptyArray()

        val totalSizeBytes = apkFiles.sumOf { it.length() }
        val totalSizeMb = String.format(Locale.US, "%.1f MB", totalSizeBytes / (1024.0 * 1024.0))

        val message = "📦 عدد ملفات التثبيت المحفوظة: \${apkFiles.size} ملف\n" +
                "💾 المساحة المستهلكة: \$totalSizeMb\n\n" +
                "يمكنك مسح ملفات الـ APK المؤقتة بعد تثبيت تطبيقاتك لتوفير مساحة تخزين إضافية في هاتفك."

        MaterialAlertDialogBuilder(this, R.style.Theme_FixEngine_Dialog)
            .setTitle("إعدادات المتجر والتخزين ⚙️")
            .setMessage(message)
            .setPositiveButton("تنظيف المساحة (\$totalSizeMb)") { dialog, _ ->
                var deletedCount = 0
                apkFiles.forEach { if (it.delete()) deletedCount++ }
                viewModel.refreshAppStates()
                Toast.makeText(this, "تم تنظيف \$deletedCount ملف وتفريغ المساحة بنجاح! ✨", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun observeStoreAnnouncement() {
        try {
            val dbUrl = "https://abdullah-store-a95ed-default-rtdb.europe-west1.firebasedatabase.app"
            FirebaseDatabase.getInstance(dbUrl)
                .getReference("store_announcement")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val isActive = snapshot.child("active").getValue(Boolean::class.java) ?: false
                        val title = snapshot.child("title").getValue(String::class.java) ?: "📢 إشعار هام"
                        val body = snapshot.child("body").getValue(String::class.java) ?: ""

                        runOnUiThread {
                            if (isActive && body.isNotBlank()) {
                                binding.txtAnnouncementTitle.text = title
                                binding.txtAnnouncementBody.text = body
                                binding.cardAnnouncement.isVisible = true
                                binding.btnDismissAnnouncement.setOnClickListener {
                                    binding.cardAnnouncement.isVisible = false
                                }
                            } else {
                                binding.cardAnnouncement.isVisible = false
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {}
                })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
