package com.fix.engine.abdullah

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fix.engine.abdullah.core.NativeCoreManager
import com.fix.engine.abdullah.core.NetworkObserver
import com.fix.engine.abdullah.core.NotificationEngine
import com.fix.engine.abdullah.core.PermissionManager
import com.fix.engine.abdullah.core.UpdateValidator
import com.fix.engine.abdullah.databinding.ActivityMainBinding
import com.fix.engine.abdullah.ui.adapter.MainPagerAdapter
import com.fix.engine.abdullah.ui.details.AppDetailsActivity
import com.fix.engine.abdullah.ui.viewmodel.MainViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var networkObserver: NetworkObserver

    private var isDataLoadedSuccessfully = false
    private var isNotificationSent = false

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) Toast.makeText(this, "تم رفض صلاحية الإشعارات", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NativeCoreManager.verifySignatureSafely(applicationContext)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        networkObserver = NetworkObserver(this)

        setupTabs()
        setupNavigationDrawer()
        setupSearchLogic()
        setupSearchAnimation()
        setupObservers()
        observeNetworkState()

        PermissionManager.requestNotificationPermission(this, requestPermissionLauncher)

        lifecycleScope.launch {
            delay(1000)
            if (PermissionManager.needsInstallPermission(this@MainActivity)) {
                showInstallPermissionDialog()
            }
        }

        refreshData()
    }

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

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { binding.progressBar.isVisible = it }

        viewModel.appsUiStateList.observe(this) { uiStates ->
            if (!uiStates.isNullOrEmpty()) {
                isDataLoadedSuccessfully = true
                val apps = uiStates.map { it.app }

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

    private fun observeNetworkState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                networkObserver.networkStatus.collectLatest { status ->
                    when (status) {
                        NetworkObserver.Status.Available -> {
                            if (!isDataLoadedSuccessfully) {
                                Toast.makeText(this@MainActivity, "تم استعادة الاتصال! جاري مزامنة المتجر... 🔄", Toast.LENGTH_SHORT).show()
                                refreshData()
                            } else {
                                Toast.makeText(this@MainActivity, "متصل بالإنترنت ✨", Toast.LENGTH_SHORT).show()
                            }
                        }
                        NetworkObserver.Status.Lost -> {
                            Toast.makeText(this@MainActivity, "عذراً، انقطع الاتصال بالإنترنت!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun refreshData() {
        val secureUrl = NativeCoreManager.getRepoUrlSafely()
        if (secureUrl.isNotEmpty()) {
            viewModel.loadApps(secureUrl.toByteArray(Charsets.UTF_8), 0.toByte())
        } else {
            Toast.makeText(this, "خطأ في معالجة بوابة الأمان", Toast.LENGTH_LONG).show()
            isDataLoadedSuccessfully = false
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
                val intent = Intent(this, AppDetailsActivity::class.java).apply {
                    putExtra("APP_DATA", storeApp)
                }
                startActivity(intent)
                finish()
            }
            .setNegativeButton("خروج") { _, _ -> finishAffinity() }
            .show()
    }
}
