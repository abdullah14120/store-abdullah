package com.fix.engine.abdullah

import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.fix.engine.abdullah.databinding.ActivityMainBinding
import com.fix.engine.abdullah.ui.adapter.MainPagerAdapter
import com.fix.engine.abdullah.ui.viewmodel.MainViewModel
import com.google.android.material.tabs.TabLayoutMediator

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Professional Tabbed UI
 * Features: ViewPager2, Smart Badge Updates, and Name-Based Comparison
 */
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
        
        refreshData()
    }

    /**
     * إعداد نظام التبويبات (ViewPager2 + TabLayout)
     */
    private fun setupTabs() {
        val pagerAdapter = MainPagerAdapter(this)
        binding.viewPagerMain.adapter = pagerAdapter

        // ربط التبويبات بالعناوين: التطبيقات (يمين) والتحديثات (يسار)
        TabLayoutMediator(binding.tabLayoutMain, binding.viewPagerMain) { tab, position ->
            tab.text = if (position == 0) "التطبيقات" else "التحديثات"
        }.attach()
    }

    /**
     * منطق البحث المباشر (سيقوم بتصفية القائمة في الـ ViewModel لكي تظهر النتائج في الـ Fragment)
     */
    private fun setupSearchLogic() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.filterApps(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupObservers() {
        // مراقبة حالة التحميل
        viewModel.isLoading.observe(this) { binding.progressBar.isVisible = it }

        // مراقبة عدد التحديثات المتاحة لإظهار النقطة الحمراء (Badge)
        viewModel.appsList.observe(this) { apps ->
            if (!apps.isNullOrEmpty()) {
                calculateUpdates(apps)
            }
        }

        viewModel.errorMessage.observe(this) { it?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() } }
    }

    /**
     * حساب عدد التحديثات بناءً على الـ versionName وإظهار الـ Badge
     */
    private fun calculateUpdates(apps: List<com.fix.engine.abdullah.data.model.AppModel>) {
        val pm = packageManager
        var updateCount = 0

        for (app in apps) {
            try {
                val pInfo = pm.getPackageInfo(app.packageName, 0)
                val installedVerName = pInfo.versionName ?: ""
                
                // المقارنة النصية بناءً على طلبك يا عبدالله لتجاوز سقف الـ VersionCode
                if (app.versionName.trim() != installedVerName.trim()) {
                    updateCount++
                }
            } catch (e: PackageManager.NameNotFoundException) {
                // التطبيق غير مثبت
            }
        }

        // تحديث شارة الإشعار (Badge) فوق تبويب التحديثات
        val updatesTab = binding.tabLayoutMain.getTabAt(1)
        val badge = updatesTab?.orCreateBadge
        
        if (updateCount > 0) {
            badge?.isVisible = true
            badge?.number = updateCount
            badge?.backgroundColor = Color.RED
            badge?.badgeTextColor = Color.WHITE
        } else {
            badge?.isVisible = false
        }
    }

    private fun refreshData() {
        // الرابط الخاص بمستودعك على GitHub
        val repoUrl = "https://raw.githubusercontent.com/abdullah14120/store-abdullah/refs/heads/main/apps.json"
        viewModel.loadApps(repoUrl)
    }
}
