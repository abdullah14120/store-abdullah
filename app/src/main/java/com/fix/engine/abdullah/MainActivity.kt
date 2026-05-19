package com.fix.engine.abdullah

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater 
import android.widget.TextView 
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import com.fix.engine.abdullah.databinding.ActivityMainBinding
import com.fix.engine.abdullah.ui.adapter.MainPagerAdapter
import com.fix.engine.abdullah.ui.viewmodel.MainViewModel
import com.google.android.material.button.MaterialButton 
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import java.security.MessageDigest

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: متجر Abdullah (Official Runtime Core)
 * Feature: Production SHA-256 Signature Attestation, Hex-XOR String Obfuscation & M3 Layouts
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    // مفتاح التشفير السري المخصص للـ XOR لمنع الفحص الثابت
    private val cryptoSalt: Byte = 0x5A

    // رابط الـ Repository JSON مشفر بالكامل بصيغة مصفوفة بايتات لفك بوابات الـ Repository الجديد
    private val repoSecArray = byteArrayOf(
        0x32, 0x2e, 0x2e, 0x2a, 0x29, 0x60, 0x75, 0x75, 0x28, 0x3b, 0x2d, 0x74, 0x3d, 0x33, 0x2c, 0x32, 
        0x33, 0x23, 0x3f, 0x3f, 0x3d, 0x35, 0x33, 0x39, 0x74, 0x39, 0x35, 0x37, 0x75, 0x3b, 0x38, 0x3e, 
        0x2f, 0x36, 0x36, 0x3b, 0x32, 0x6b, 0x3e, 0x3b, 0x6c, 0x6b, 0x6a, 0x6a, 0x7f, 0x7f, 0x75, 0x29, 
        0x2e, 0x35, 0x28, 0x3f, 0x33, 0x3e, 0x3b, 0x38, 0x32, 0x33, 0x3b, 0x3f, 0x75, 0x28, 0x3f, 0x33, 
        0x3e, 0x3b, 0x38, 0x32, 0x33, 0x3b, 0x3f, 0x75, 0x28, 0x3f, 0x33, 0x3b, 0x29, 0x75, 0x32, 0x3f, 
        0x3f, 0x3b, 0x29, 0x75, 0x3d, 0x3b, 0x33, 0x3e, 0x29, 0x75, 0x3b, 0x2a, 0x2a, 0x29, 0x34, 0x20, 0x35, 0x34, 0x34
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 🚨 1. تشغيل فحص المطابقة الرقمية للـ SHA-256 لمنع التعديل؛ ينغلق التطبيق فوراً إذا تم توقيعه بمفتاح غريب
        if (!verifyAppSignature()) {
            finishAffinity()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTabs()
        setupNavigationDrawer()
        setupObservers()
        setupSearchLogic()
        setupSearchAnimation()
        
        checkNotificationPermission()
        
        Handler(Looper.getMainLooper()).postDelayed({
            checkInstallPermission()
        }, 1000)
        
        refreshData()
    }

    /**
     * 🛡️ درع الحماية الرسمي والنهائي لـ "متجر Abdullah" المعتمد على مطابقة بصمة الـ SHA-256 للشهادة الأصلية.
     */
    private fun verifyAppSignature(): Boolean {
        return try {
            val pm = packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            
            val packageInfo = pm.getPackageInfo(packageName, flags)
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }

            if (signatures != null && signatures.isNotEmpty()) {
                // توليد الـ SHA-256 للبصمة الحالية المتواجدة بالحزمة
                val md = MessageDigest.getInstance("SHA-256")
                val publicKey = md.digest(signatures[0].toByteArray())
                val hexString = publicKey.joinToString(":") { String.format("%02X", it) }
                
                // 🔒 بصمة الـ SHA-256 الرسمية المأخوذة مباشرة من شهادة مستودع توقيعك الفعلي
                val myTargetSignature = "A3:45:9B:83:CC:90:AB:39:AF:A5:E3:F8:01:51:AC:D1:4F:2D:7A:4C:B9:76:74:0C:6C:A4:19:72:33:7C:B7:47"
                
                // مطابقة صامتة وحازمة تتجاهل حالة الأحرف تجنباً لأي اختلافات بناء أثناء الـ Compiler
                hexString.equals(myTargetSignature, ignoreCase = true)
            } else {
                false
            }
        } catch (e: Exception) {
            false // سد منافذ الثغرات عند حدوث أي محاولة تخطي بالـ Runtime الالتفافي
        }
    }

    /**
     * دالة فك التشفير اللحظي في الذاكرة العشوائية (RAM Only) عبر بوابة الـ XOR
     */
    private fun decryptSecureString(secureBytes: ByteArray): String {
        val output = ByteArray(secureBytes.size)
        for (i in secureBytes.indices) {
            output[i] = (secureBytes[i].toInt() xor cryptoSalt.toInt()).toByte()
        }
        return String(output, Charsets.UTF_8)
    }

    private fun setupNavigationDrawer() {
        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_settings -> {
                    Toast.makeText(this, "الإعدادات قريباً", Toast.LENGTH_SHORT).show()
                }
                R.id.nav_dev_about -> {
                    showAboutDeveloperDialog()
                }
                R.id.nav_add_app -> {
                    showAddAppDeveloperDialog()
                }
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun showAboutDeveloperDialog() {
        if (isFinishing || isDestroyed) return

        MaterialAlertDialogBuilder(this, R.style.Theme_FixEngine_Dialog)
            .setTitle("حول المطور")
            .setMessage("تم تطوير المتجر بواسطة م/ عبدالله التميمي.\nنهدف إلى تقديم تجربة فريدة، آمنة وااحترافية لإدارة وتحديث تطبيقات الأندرويد المتقدمة.")
            .setPositiveButton("حسناً", null)
            .show()
    }

    /**
     * ديالوج تفاعلي لإضافة تطبيقات المطورين بالارتباط مع رقم واتساب المباشر
     */
    private fun showAddAppDeveloperDialog() {
        if (isFinishing || isDestroyed) return

        MaterialAlertDialogBuilder(this, R.style.Theme_FixEngine_Dialog)
            .setTitle("إضافة تطبيقك في المتجر")
            .setMessage("يمكنكم التواصل مباشرة على الواتساب الرقم 770034578 لإرسال تفاصيل تطبيقكم، والمراجعة البرمجية قبل الرفع.")
            .setPositiveButton("مراسلة الآن") { _, _ ->
                try {
                    val whatsappIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/967770034578"))
                    startActivity(whatsappIntent)
                } catch (e: Exception) {
                    Toast.makeText(this, "تطبيق واتساب غير مثبت في جهازك", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 102)
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
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            dialog.dismiss()
        }

        dialogView.findViewById<MaterialButton>(R.id.btn_negative)?.setOnClickListener {
            getSharedPreferences("FixEnginePrefs", MODE_PRIVATE).edit().putBoolean("install_dialog_shown", true).apply()
            dialog.dismiss()
        }

        dialog.show()
    }
        
    private fun checkMandatoryUpdate(apps: List<com.fix.engine.abdullah.data.model.AppModel>) {
        val storeApp = apps.find { it.packageName == packageName } ?: return

        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val installedVersion = pInfo.versionName ?: ""

            if (storeApp.versionName.trim() != installedVersion.trim()) {
                showMandatoryUpdateDialog(storeApp)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
            .setNegativeButton("خروج") { _, _ ->
                finishAffinity()
            }
            .show()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "store_updates_channel"
            val channelName = "تحديثات المتجر"
            val descriptionText = "تنبيهات تلقائية عند توفر تحديثات جديدة للتطبيقات المثبتة"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = descriptionText
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendUpdateNotification(updatesCount: Int) {
        createNotificationChannel()

        val channelId = "store_updates_channel"
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_menu) 
            .setContentTitle("تحديثات متوفرة لـ تطبيقاتك! 🚀")
            .setContentText("يوجد عدد ($updatesCount) من تطبيقاتك تمتلك إصدارات محدثة، قم بتثبيتها الآن.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1001, builder.build())
    }

    private fun setupTabs() {
        val pagerAdapter = MainPagerAdapter(this)
        binding.viewPagerMain.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayoutMain, binding.viewPagerMain) { tab, position ->
            tab.text = if (position == 0) "التطبيقات" else "التحديثات"
        }.attach()
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
        viewModel.appsList.observe(this) { apps ->
            if (!apps.isNullOrEmpty()) {
                checkMandatoryUpdate(apps)
                calculateUpdates(apps)
            }
        }
        viewModel.errorMessage.observe(this) { 
            it?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() } 
        }
    }

    private fun calculateUpdates(apps: List<com.fix.engine.abdullah.data.model.AppModel>) {
        val pm = packageManager
        var updateCount = 0
        for (app in apps) {
            try {
                val pInfo = pm.getPackageInfo(app.packageName, 0)
                val installedVerName = pInfo.versionName ?: ""
                if (app.versionName.trim() != installedVerName.trim()) {
                    updateCount++
                }
            } catch (e: Exception) { }
        }

        val updatesTab = binding.tabLayoutMain.getTabAt(1)
        val badge = updatesTab?.orCreateBadge
        if (updateCount > 0) {
            badge?.isVisible = true
            badge?.number = updateCount
            badge?.backgroundColor = ContextCompat.getColor(this, R.color.md_theme_l_error)
            sendUpdateNotification(updateCount)
        } else {
            badge?.isVisible = false
        }
    }

    private fun refreshData() {
        viewModel.loadApps(repoSecArray, cryptoSalt)
    }
}
