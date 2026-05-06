package com.fix.engine.abdullah

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.fix.engine.abdullah.databinding.ActivityMainBinding
import com.fix.engine.abdullah.ui.adapter.AppAdapter
import com.fix.engine.abdullah.ui.details.AppDetailsActivity
import com.fix.engine.abdullah.ui.viewmodel.MainViewModel

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Android Application Store
 * Architecture: MVVM + ViewBinding + Coroutines + Navigation
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    
    // ربط الـ ViewModel بطريقة كوتلن الحديثة
    private val viewModel: MainViewModel by viewModels()
    
    // تعريف الـ Adapter كمتغير خاص
    private lateinit var appAdapter: AppAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupObservers()
        
        // تحميل البيانات عند بدء التشغيل
        refreshData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "FIX ENGINE"
            subtitle = "By Abdullah Al-Tamimi"
        }
    }

    /**
     * إعداد القائمة بربطها بشاشة التفاصيل
     */
    private fun setupRecyclerView() {
        appAdapter = AppAdapter { app ->
            // الانتقال الاحترافي لشاشة التفاصيل
            val intent = Intent(this, AppDetailsActivity::class.java).apply {
                putExtra("APP_DATA", app) // تأكد أن AppModel ينفذ Serializable أو Parcelable
            }
            startActivity(intent)
            // إضافة حركة انتقال ناعمة (اختياري)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = appAdapter
            setHasFixedSize(true)
        }
    }

    /**
     * مراقبة البيانات والحالات من الـ ViewModel
     */
    private fun setupObservers() {
        // مراقبة حالة التحميل (Loading State)
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.isVisible = isLoading
            
            // تأثير بصري هادئ للقائمة عند التحميل
            if (isLoading) {
                binding.recyclerView.animate().alpha(0.3f).setDuration(200).start()
            } else {
                binding.recyclerView.animate().alpha(1.0f).setDuration(400).start()
            }
        }

        // مراقبة قائمة التطبيقات وتمريرها للـ Adapter
        viewModel.appsList.observe(this) { apps ->
            if (apps.isNullOrEmpty()) {
                // هنا يمكن إظهار رسالة "لا توجد تطبيقات حالياً"
            } else {
                appAdapter.submitList(apps)
            }
        }

        // مراقبة رسائل الخطأ
        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshData() {
        // رابط المستودع المحدث (JSON)
        val repoUrl = "https://raw.githubusercontent.com/your-username/your-repo/main/apps.json"
        viewModel.loadApps(repoUrl)
    }

    // --- إدارة القائمة العلوية ---

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                refreshData()
                true
            }
            R.id.action_settings -> {
                // سيتم توجيه المستخدم للإعدادات لاحقاً
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
