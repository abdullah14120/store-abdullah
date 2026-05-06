package com.fix.engine.abdullah

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.fix.engine.abdullah.databinding.ActivityMainBinding
import com.fix.engine.abdullah.ui.viewmodel.MainViewModel

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Android Application Store
 * Architecture: MVVM + ViewBinding + Coroutines
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    
    // ربط الـ ViewModel بطريقة كوتلن الحديثة (Activity KTX)
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupObservers()
        
        // جلب البيانات عند بدء التطبيق من الرابط المخصص لمستودعك
        val repoUrl = "https://raw.githubusercontent.com/your-username/your-repo/main/apps.json"
        viewModel.loadApps(repoUrl)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "FIX ENGINE"
            subtitle = "By Abdullah Al-Tamimi"
        }
    }

    /**
     * مراقبة البيانات والحالات من الـ ViewModel
     */
    private fun setupObservers() {
        // مراقبة حالة التحميل لإظهار أو إخفاء الـ ProgressBar
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.isVisible = isLoading
        }

        // مراقبة قائمة التطبيقات (هنا سنقوم لاحقاً بربطها بالـ RecyclerView)
        viewModel.appsList.observe(this) { apps ->
            if (apps.isNotEmpty()) {
                // تحديث القائمة في الواجهة
                Toast.makeText(this, "تم جلب ${apps.size} تطبيق بنجاح", Toast.LENGTH_SHORT).show()
            }
        }

        // مراقبة الأخطاء
        viewModel.errorMessage.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                val repoUrl = "https://raw.githubusercontent.com/your-username/your-repo/main/apps.json"
                viewModel.loadApps(repoUrl)
                true
            }
            R.id.action_settings -> {
                // سيتم العمل على الإعدادات لاحقاً
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
