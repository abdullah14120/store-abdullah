package com.fix.engine.abdullah

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.fix.engine.abdullah.databinding.ActivityMainBinding
import com.fix.engine.abdullah.util.Log // سنقوم بإنشاء كلاس Log مخصص لاحقاً
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Android Application Store
 * Description: The main entry point for the store engine.
 */
class MainActivity : AppCompatActivity() {

    // ViewBinding للوصول الآمن لجميع عناصر الواجهة
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // تهيئة الواجهة
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        initializeEngine()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = "FIX ENGINE"
            subtitle = "By Abdullah Al-Tamimi"
        }
    }

    private fun initializeEngine() {
        // استخدام Coroutines لتشغيل عمليات الخلفية دون تجميد الواجهة
        lifecycleScope.launch {
            try {
                showLoading(true)
                
                // هنا سنقوم لاحقاً باستدعاء محرك الشبكة لجلب البيانات
                // محاكاة لعملية جلب البيانات (تأخير ثانية واحدة)
                delay(1000)
                
                updateStatus("Engine Ready")
                showLoading(false)
                
            } catch (e: Exception) {
                showLoading(false)
                updateStatus("Error: ${e.message}")
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        // الوصول للعناصر عبر binding يضمن عدم حدوث NullPointerException
        binding.progressBar.isVisible = isLoading
    }

    private fun updateStatus(message: String) {
        // مثال لاستخدام binding للوصول لـ TextView
        // binding.statusTextView.text = message
    }

    // إدارة القائمة العلوية (Menu) بشكل احترافي
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                initializeEngine()
                true
            }
            R.id.action_settings -> {
                // فتح الإعدادات لاحقاً
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
