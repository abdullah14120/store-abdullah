package com.fix.engine.abdullah

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
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
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import com.fix.engine.abdullah.databinding.ActivityMainBinding
import com.fix.engine.abdullah.ui.adapter.MainPagerAdapter
import com.fix.engine.abdullah.ui.viewmodel.MainViewModel
import com.google.android.material.button.MaterialButton 
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Global Professional Hub
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTabs()
        setupNavigationDrawer()
        setupObservers()
        setupSearchLogic()
        setupSearchAnimation()
        
        // تأخير الاستدعاء قليلاً لضمان استقرار النشاط (Activity)
        Handler(Looper.getMainLooper()).postDelayed({
            checkInstallPermission()
        }, 1000)
        
        refreshData()
    }

    private fun setupNavigationDrawer() {
        // ربط الزر العلوي btnMenu بفتح القائمة الجانبية
        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        // معالجة الضغطات على عناصر القائمة
        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_settings -> {
                    Toast.makeText(this, "الإعدادات قريباً", Toast.LENGTH_SHORT).show()
                }
                R.id.nav_dev_about -> {
                    showAboutDeveloperDialog()
                }
                R.id.nav_add_app -> {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/abdullah_dev"))
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "تطبيق تليجرام غير مثبت", Toast.LENGTH_SHORT).show()
                    }
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
            .setMessage("تم تطوير FIX ENGINE بواسطة م/ عبدالله التميمي.\nنهدف إلى تقديم تجربة فريدة، آمنة واحترافية لإدارة وتحديث تطبيقات الأندرويد المتقدمة.")
            .setPositiveButton("حسناً", null)
            .show()
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
            markDialogAsShown()
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
            dialog.dismiss()
        }

        dialogView.findViewById<MaterialButton>(R.id.btn_negative)?.setOnClickListener {
            markDialogAsShown()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun markDialogAsShown() {
        getSharedPreferences("FixEnginePrefs", MODE_PRIVATE)
            .edit()
            .putBoolean("install_dialog_shown", true)
            .apply()
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

    override fun onResume() {
        super.onResume()
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
        binding.etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.searchCard.animate().scaleX(1.01f).scaleY(1.01f).setDuration(250).start()
                binding.searchCard.strokeWidth = 2
                binding.searchCard.strokeColor = Color.parseColor("#22D3EE")
            } else {
                binding.searchCard.animate().scaleX(1f).scaleY(1f).setDuration(250).start()
                binding.searchCard.strokeWidth = 1
                binding.searchCard.strokeColor = Color.parseColor("#334155")
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
            badge?.backgroundColor = Color.RED
        } else {
            badge?.isVisible = false
        }
    }

    private fun refreshData() {
        val repoUrl = "https://raw.githubusercontent.com/abdullah14120/store-abdullah/refs/heads/main/apps.json"
        viewModel.loadApps(repoUrl)
    }
}
