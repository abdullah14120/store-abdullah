package com.fix.engine.abdullah

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
import com.fix.engine.abdullah.ui.viewmodel.MainViewModel

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Android Application Store
 * Architecture: MVVM + ViewBinding + Coroutines + ListAdapter
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
        
        // تحميل البيانات الأولية عند فتح التطبيق
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
     * إعداد القائمة (RecyclerView) بلمسة هادئة واحترافية
     */
    private fun setupRecyclerView() {
        appAdapter = AppAdapter { app ->
            // منطق النقر على التطبيق: فتح صفحة التفاصيل مثلاً
            Toast.makeText(this, "استعراض: ${app.name}", Toast.LENGTH_SHORT).show()
        }

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = appAdapter
            // منع الرمش عند التحديث لضمان هدوء الواجهة
            setHasFixedSize(true)
        }
    }

    /**
     * مراقبة البيانات والحالات من الـ ViewModel بذكاء
     */
    private fun setupObservers() {
        // مراقبة حالة التحميل (Loading State)
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.isVisible = isLoading
            // إخفاء القائمة مؤقتاً أثناء التحميل الأول لزيادة الأناقة البصرية
            if (isLoading && appAdapter.itemCount == 0) {
                binding.recyclerView.alpha = 0.5f
            } else {
                binding.recyclerView.animate().alpha(1.0f).setDuration(300).start()
            }
        }

        // مراقبة قائمة التطبيقات وتمريرها للـ Adapter
        viewModel.appsList.observe(this) { apps ->
            appAdapter.submitList(apps)
        }

        // مراقبة رسائل الخطأ وعرضها بشكل غير مزعج
        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshData() {
        // رابط المستودع الخاص بك (يفضل نقله لملف Constants لاحقاً)
        val repoUrl = "https://raw.githubusercontent.com/your-username/your-repo/main/apps.json"
        viewModel.loadApps(repoUrl)
    }

    // --- إدارة القائمة العلوية (Menu) ---

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
