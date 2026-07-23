package com.fix.engine.abdullah

import android.Manifest
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
import com.fix.engine.abdullah.databinding.ActivityMainBinding
import com.fix.engine.abdullah.ui.adapter.MainPagerAdapter
import com.fix.engine.abdullah.ui.viewmodel.MainViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Developed by: Abdullah Al-Tamimi
 * Feature: Strict UI/Core Separation & Clean Architecture Observers
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var networkMonitor: NetworkObserver

    private var isDataLoadedSuccessfully = false
    private var isNotificationSent = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) Toast.makeText(this, "تم رفض صلاحية الإشعارات", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 🛡️ التفويض لطبقة الأمان (Core)
        NativeCoreManager.verifySignature(applicationContext)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initNetworkMonitor()
        setupTabs()
        setupNavigationDrawer()
        setupSearchLogic()
        setupSearchAnimation()
        setupObservers()

        PermissionManager.requestNotificationPermission(this, requestPermissionLauncher)

        lifecycleScope.launch {
            delay(1000)
            if (PermissionManager.needsInstallPermission(this@MainActivity)) {
                showInstallPermissionDialog()
            }
        }

        refreshData()
    }

    override fun onStart() {
        super.onStart()
        networkMonitor.register()
    }

    override fun onStop() {
        super.onStop()
        networkMonitor.unregister()
    }

    // ==========================================
    // 🎨 UI SETUP & EVENT LISTENERS
    // ==========================================

    private fun setupTabs() {
        binding.viewPagerMain.adapter = MainPagerAdapter(this)
        TabLayoutMediator(binding.tabLayoutMain, binding.viewPagerMain) { tab, position ->
            tab.text = if (position == 0) "التطبيقات" else "التحديثات"
        }.attach()
    }

    private fun setupNavigationDrawer() {
        binding.btnMenu.setOnClickListener { binding.drawerLayout.openDrawer(GravityCompat.START) }
        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_settings -> Toast.makeText(this, "الإعدادات قريباً", Toast.LENGTH_SHORT).show()
                R.id.nav_dev_about -> showAboutDeveloperDialog()
                R.id.nav_add_app -> showAddAppDeveloperDialog()
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun setupSearchLogic() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.filterApps(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupSearchAnimation() {
        val colorPrimary = ContextCompat.getColor(this, R.color.md_theme_d_primary)
        val colorOutlineVariant = ContextCompat.getColor(this, R.color.md_theme_d_outlineVariant)

        binding.etSearch.setOnFocusChangeListener { _, hasFocus ->
            val scale = if (hasFocus) 1.01f else 1f
            binding.searchCard.animate().scaleX(scale).scaleY(scale).setDuration(250).start()
            binding.searchCard.strokeWidth = if (hasFocus) 2 else 1
            binding.searchCard.strokeColor = if (hasFocus) colorPrimary else colorOutlineVariant
        }
    }

    // ==========================================
    // 👀 STATE OBSERVERS (MVVM)
    // ==========================================

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { binding.progressBar.isVisible = it }

        viewModel.appsUiStateList.observe(this) { uiStates ->
            if (!uiStates.isNullOrEmpty()) {
                isDataLoadedSuccessfully = true
                val apps = uiStates.map { it.app }
                
                // التفويض لطبقة المنطق لفحص الإصدارات
                UpdateValidator.getMandatoryUpdate(this, apps)?.let { storeApp ->
                    showMandatoryUpdateDialog(storeApp)
                }
            }
        }

        viewModel.updatesUiStateList.observe(this) { updateStates ->
            updateStates?.let {
                val updateCount = it.size
                val badge = binding.tabLayoutMain.getTabAt(1)?.orCreateBadge
                
                if (updateCount > 0) {
                    badge?.apply {
                        isVisible = true
                        number = updateCount
                        backgroundColor = ContextCompat.getColor(this@MainActivity, R.color.md_theme_d_error)
                    }

                    if (binding.etSearch.text.isNullOrBlank() && !isNotificationSent) {
                        NotificationEngine.sendUpdateNotification(this, updateCount)
                        isNotificationSent = true
                    }
                } else {
                    badge?.isVisible = false
                }
            }
        }

        viewModel.errorMessage.observe(this) { errorMsg ->
            errorMsg?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                isDataLoadedSuccessfully = false
            }
        }
    }

    // ==========================================
    // ⚙️ UI ACTIONS & DIALOGS
    // ==========================================

    private fun refreshData() {
        try {
            val secureUrl = NativeCoreManager.getRepoUrl()
            viewModel.loadApps(secureUrl.toByteArray(Charsets.UTF_8), 0.toByte())
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ في معالجة بوابة الأمان", Toast.LENGTH_LONG).show()
            isDataLoadedSuccessfully = false
        }
    }

    private fun initNetworkMonitor() {
        networkMonitor = NetworkObserver(this, 
            onAvailable = {
                if (!isDataLoadedSuccessfully) {
                    Toast.makeText(this, "تم استعادة الاتصال! جاري مزامنة المتجر... 🔄", Toast.LENGTH_SHORT).show()
                    refreshData()
                } else {
                    Toast.makeText(this, "متصل بالإنترنت ✨", Toast.LENGTH_SHORT).show()
                }
            },
            onLost = {
                Toast.makeText(this, "عذراً، انقطع الاتصال بالإنترنت!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun showAboutDeveloperDialog() {
        if (isFinishing || isDestroyed) return
        MaterialAlertDialogBuilder(this, R.style.Theme_FixEngine_Dialog)
            .setTitle("حول المطور")
            .setMessage("تم تطوير المتجر بواسطة م/ عبدالله التميمي.\nنهدف إلى تقديم تجربة فريدة، آمنة وااحترافية لإدارة وتحديث تطبيقات الأندرويد المتقدمة.")
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

    private fun showInstallPermissionDialog() {
        if (isFinishing || isDestroyed) return
        val dialogView = LayoutInflater.from(this).inflate(R.layout.mtrl_alert_dialog, null)
        val dialog = MaterialAlertDialogBuilder(this, R.style.Theme_FixEngine_Dialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<MaterialButton>(R.id.btn_positive)?.setOnClickListener {
            PermissionManager.markInstallDialogShown(this)
            PermissionManager.launchInstallSettings(this)
            dialog.dismiss()
        }

        dialogView.findViewById<MaterialButton>(R.id.btn_negative)?.setOnClickListener {
            PermissionManager.markInstallDialogShown(this)
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showMandatoryUpdateDialog(storeApp: com.fix.engine.abdullah.data.model.AppModel) {
        if (isFinishing || isDestroyed) return
        MaterialAlertDialogBuilder(this, R.style.Theme_FixEngine_Dialog)
            .setTitle("تحديث إجباري!")
            .setMessage("يوجد إصدار جديد من متجر Abdullah يحل بعض المشاكل التقنية. يرجى التحديث للمتابعة.")
            .setCancelable(false)
            .setPositiveButton("تحديث الآن") { _, _ ->
                val intent = Intent(this, com.fix.engine.abdullah.ui.details.AppDetailsActivity::class.java).apply {
                    putExtra("APP_DATA", storeApp)
                }
                startActivity(intent)
                finish()
            }
            .setNegativeButton("خروج") { _, _ -> finishAffinity() }
            .show()
    }
}

// ==============================================================================
// ⚙️ CORE LOGIC LAYER (يفضل نقل هذه الكائنات إلى ملفات مستقلة في حزمة core)
// ==============================================================================

object NativeCoreManager {
    init { System.loadLibrary("native-lib") }
    
    @JvmStatic external fun verifySignatureNative(context: Context)
    @JvmStatic private external fun getSecureRepoUrl(): String

    fun verifySignature(context: Context) = verifySignatureNative(context)
    fun getRepoUrl(): String = getSecureRepoUrl()
}

class NetworkObserver(
    private val context: Context,
    private val onAvailable: () -> Unit,
    private val onLost: () -> Unit
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var isRegistered = false

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            // ضمان التنفيذ على الـ Main Thread
            (context as? AppCompatActivity)?.runOnUiThread { onAvailable() }
        }
        override fun onLost(network: Network) {
            (context as? AppCompatActivity)?.runOnUiThread { onLost() }
        }
    }

    fun register() {
        if (isRegistered) return
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        connectivityManager.registerNetworkCallback(request, callback)
        isRegistered = true
    }

    fun unregister() {
        if (!isRegistered) return
        connectivityManager.unregisterNetworkCallback(callback)
        isRegistered = false
    }
}

object PermissionManager {
    private const val PREFS_NAME = "FixEnginePrefs"
    private const val KEY_INSTALL_DIALOG = "install_dialog_shown"

    fun requestNotificationPermission(activity: AppCompatActivity, launcher: androidx.activity.result.ActivityResultLauncher<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun needsInstallPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isDialogShown = prefs.getBoolean(KEY_INSTALL_DIALOG, false)
        return !context.packageManager.canRequestPackageInstalls() && !isDialogShown
    }

    fun markInstallDialogShown(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_INSTALL_DIALOG, true).apply()
    }

    fun launchInstallSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply { 
                data = Uri.parse("package:${context.packageName}") 
            }
            context.startActivity(intent)
        }
    }
}

object UpdateValidator {
    fun getMandatoryUpdate(context: Context, apps: List<com.fix.engine.abdullah.data.model.AppModel>): com.fix.engine.abdullah.data.model.AppModel? {
        val storeApp = apps.find { it.packageName == context.packageName } ?: return null
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (storeApp.versionName.trim() != pInfo.versionName?.trim()) storeApp else null
        } catch (e: Exception) {
            null
        }
    }
}

object NotificationEngine {
    private const val CHANNEL_ID = "store_updates_channel"

    fun sendUpdateNotification(context: Context, updatesCount: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "تحديثات المتجر", NotificationManager.IMPORTANCE_DEFAULT).apply { 
                description = "تنبيهات تلقائية عند توفر تحديثات جديدة" 
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply { 
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK 
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_transparent)
            .setContentTitle("تحديثات متوفرة لـ تطبيقاتك! 🚀")
            .setContentText("يوجد عدد ($updatesCount) من تطبيقاتك تمتلك إصدارات محدثة، قم بتثبيتها الآن.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(1001, notification)
    }
}
