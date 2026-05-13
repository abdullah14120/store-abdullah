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
import androidx.core.view.isVisible
import com.fix.engine.abdullah.databinding.ActivityMainBinding
import com.fix.engine.abdullah.ui.adapter.MainPagerAdapter
import com.fix.engine.abdullah.ui.viewmodel.MainViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    setupTabs()
    setupObservers()
    setupSearchLogic()
    setupSearchAnimation()
    
    // 1. عطل الـ Handler مؤقتاً وجرب تشغيل التطبيق. 
    // إذا لم ينهار، فالمشكلة كانت في توقيت الديالوج.
    // checkInstallPermission() 

    refreshData()
}

// 2. تعديل الـ Observer ليكون محمياً من البيانات الفارغة (Null Safe)
private fun setupObservers() {
    viewModel.isLoading.observe(this) { binding.progressBar.isVisible = it }
    
    viewModel.appsList.observe(this) { apps ->
        // حماية: إذا كانت القائمة null أو حدث خطأ في البيانات لا تنهار
        if (!apps.isNullOrEmpty()) {
            try {
                calculateUpdates(apps)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    viewModel.errorMessage.observe(this) { error ->
        error?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() }
    }
}

    private fun checkInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                showInstallPermissionDialog()
            }
        }
    }

    private fun showInstallPermissionDialog() {
    if (isFinishing || isDestroyed) return 

    // خطة الطوارئ: بناء ديالوج نظامي خام بدون أي ملفات XML أو Styles مخصصة
    // إذا نجح هذا، فالمشكلة في ملف mtrl_alert_dialog أو Theme_FixEngine_Dialog
    MaterialAlertDialogBuilder(this)
        .setTitle("تفعيل التثبيت الآمن")
        .setMessage("عزيزي المستخدم، لضمان تحديث تطبيقاتك من FIX ENGINE بأمان، نحتاج منك منح المتجر إذن التثبيت.")
        .setCancelable(false)
        .setPositiveButton("منح الإذن") { _, _ ->
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "يرجى منحه يدوياً من الإعدادات", Toast.LENGTH_LONG).show()
            }
        }
        .setNegativeButton("لاحقاً", null)
        .show()
}
    // ... باقي الدوال (setupTabs, setupObservers, إلخ) كما هي دون تغيير ...  
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
                calculateUpdates(apps)
            }
        }
    }

    private fun calculateUpdates(apps: List<com.fix.engine.abdullah.data.model.AppModel>) {
        val pm = packageManager
        var updateCount = 0
        for (app in apps) {
            try {
                val pInfo = pm.getPackageInfo(app.packageName, 0)
                if (app.versionName.trim() != (pInfo.versionName ?: "").trim()) {
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
