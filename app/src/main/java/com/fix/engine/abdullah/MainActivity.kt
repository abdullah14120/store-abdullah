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
 * Project: FIX ENGINE - Global Professional Hub
 * Features: ViewPager2, Advanced Search Animations, and Dynamic Badge System
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
        setupSearchAnimation() // اللمسة الاحترافية الجديدة
        
        refreshData()
    }

    /**
     * إعداد نظام التبويبات مع ViewPager2
     */
    private fun setupTabs() {
        val pagerAdapter = MainPagerAdapter(this)
        binding.viewPagerMain.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayoutMain, binding.viewPagerMain) { tab, position ->
            tab.text = if (position == 0) "التطبيقات" else "التحديثات"
        }.attach()
    }

    /**
     * منطق البحث المباشر
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

    /**
     * لمسة حركة البحث العالمية (Scale & Stroke Animation)
     */
    private fun setupSearchAnimation() {
        binding.etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.searchCard.animate()
                    .scaleX(1.01f)
                    .scaleY(1.01f)
                    .setDuration(250)
                    .start()
                binding.searchCard.strokeWidth = 2
                binding.searchCard.strokeColor = Color.parseColor("#22D3EE") // لون السيان الخاص بك
            } else {
                binding.searchCard.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(250)
                    .start()
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

        viewModel.errorMessage.observe(this) { 
            it?.let { Toast.makeText(this, it, Toast.LENGTH_LONG).show() } 
        }
    }

    /**
     * تحديث شارة التنبيه (Badge) بناءً على المقارنة النصية للإصدار
     */
    private fun calculateUpdates(apps: List<com.fix.engine.abdullah.data.model.AppModel>) {
        val pm = packageManager
        var updateCount = 0

        for (app in apps) {
            try {
                val pInfo = pm.getPackageInfo(app.packageName, 0)
                val installedVerName = pInfo.versionName ?: ""
                
                // منطق عبدالله: تجاوز قيود الـ VersionCode بمقارنة الـ VersionName
                if (app.versionName.trim() != installedVerName.trim()) {
                    updateCount++
                }
            } catch (e: PackageManager.NameNotFoundException) { }
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
