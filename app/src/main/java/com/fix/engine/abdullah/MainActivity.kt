package com.fix.engine.abdullah

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.fix.engine.abdullah.data.model.AppInstallStatus
import com.fix.engine.abdullah.data.model.AppItemUiState
import com.fix.engine.abdullah.data.model.AppModel
import com.fix.engine.abdullah.databinding.ActivityMainBinding
import com.fix.engine.abdullah.ui.adapter.MainPagerAdapter
import com.fix.engine.abdullah.ui.details.AppDetailsActivity
import com.fix.engine.abdullah.ui.viewmodel.MainViewModel
import com.fix.engine.abdullah.service.UpdateWorker
import com.fix.engine.abdullah.ui.widget.FloatingNavBarController
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: متجر Abdullah (Official Runtime Core)
 * Feature: JNI C++ Security, Synced Day/Night Theme Switcher, Hero Spotlight, Smart Sort, Floating Pill Nav & Storage Manager
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private var floatingNavController: FloatingNavBarController? = null

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback
    private var isNetworkCallbackRegistered = false
    private var isDataLoadedSuccessfully = false

    private var isNotificationSent = false
    private var isBackgroundUpdateScheduled = false
    private var isUpdatingThemeProgrammatically = false

    private var spotlightApps: List<AppItemUiState> = emptyList()
    private var currentSpotlightIndex = 0
    private var spotlightAutoScrollJob: Job? = null
    private var isHeroSwipeConfigured = false

    private val sortLabels = arrayOf(
        "الأحدث والإصدارات المميزة ✨",
        "الأكثر تحميلاً وشعبية 🔥",
        "الترتيب الأبجدي (أ - ي) 🔤",
        "الحجم (من الأصغر للأكبر) 📦",
        "الحجم (من الأكبر للأصغر) 💎"
    )

    private val sortShortLabels = arrayOf(
        "الأحدث ✨",
        "الأكثر تحميلاً 🔥",
        "أبجدياً 🔤",
        "الأصغر حجماً 📦",
        "الأكبر حجماً 💎"
    )

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "تم رفض صلاحية الإشعارات", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        init {
            System.loadLibrary("native-lib")
        }
    }

    private external fun verifySignatureNative(context: Context): Boolean
    private external fun getSecureRepoUrl(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!verifySignatureNative(applicationContext)) {
            finishAffinity()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ⚡ إعطاء الأولوية القصوى لجلب بيانات المستودع فور فتح المتجر
        refreshData()

        setupTabs()
        setupNavigationDrawer()
        setupObservers()
        setupSearchLogic()
        setupSearchAnimation()

        checkNotificationPermission()

        lifecycleScope.launch {
            delay(500)
            observeStoreAnnouncement()
            delay(500)
            checkInstallPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        if (spotlightApps.size > 1) {
            startSpotlightAutoScroll()
        }
    }

    override fun onPause() {
        stopSpotlightAutoScroll()
        super.onPause()
    }

    override fun onDestroy() {
        stopSpotlightAutoScroll()
        floatingNavController?.detach()
        floatingNavController = null
        super.onDestroy()
    }

    private fun registerNetworkMonitoring() {
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        if (!isDataLoadedSuccessfully) {
                            Toast.makeText(this@MainActivity, "تم استعادة الاتصال! جاري مزامنة المتجر... 🔄", Toast.LENGTH_SHORT).show()
                            refreshData()
                        } else {
                            Toast.makeText(this@MainActivity, "متصل بالإنترنت ✨", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        Toast.makeText(this@MainActivity, "عذراً، انقطع الاتصال بالإنترنت!", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            isNetworkCallbackRegistered = true
        } catch (e: Exception) {
            isNetworkCallbackRegistered = false
        }
    }

    override fun onStart() {
        super.onStart()
        if (!isNetworkCallbackRegistered) {
            registerNetworkMonitoring()
        }
    }

    override fun onStop() {
        super.onStop()
        if (isNetworkCallbackRegistered) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isNetworkCallbackRegistered = false
        }
    }

    private fun updateSpotlightList(allStates: List<AppItemUiState>) {
        val candidates = allStates.filter { it.app.packageName != packageName }
        val prioritized = candidates.sortedByDescending {
            when (it.status) {
                AppInstallStatus.UPDATE_AVAILABLE -> 3
                AppInstallStatus.DOWNLOADED -> 2
                else -> 1
            }
        }.take(4)

        spotlightApps = prioritized
        val shouldShowHero = spotlightApps.isNotEmpty() && binding.etSearch.text.isNullOrBlank()
        binding.cardHeroSpotlight.isVisible = shouldShowHero

        if (shouldShowHero) {
            currentSpotlightIndex = currentSpotlightIndex.coerceIn(0, spotlightApps.lastIndex)
            setupHeroSwipeGesture()
            bindSpotlightCard(spotlightApps[currentSpotlightIndex])
            renderSpotlightDots()
            if (spotlightApps.size > 1) {
                startSpotlightAutoScroll()
            } else {
                stopSpotlightAutoScroll()
            }
        } else {
            stopSpotlightAutoScroll()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupHeroSwipeGesture() {
        if (isHeroSwipeConfigured) return
        isHeroSwipeConfigured = true

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        val swipeThresholdPx = (44f * resources.displayMetrics.density)
        var downX = 0f
        var downY = 0f

        binding.cardHeroSpotlight.setOnTouchListener { view, event ->
            if (spotlightApps.size <= 1) return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.2f) {
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    false
                }
                MotionEvent.ACTION_UP -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (abs(dx) > swipeThresholdPx && abs(dx) > abs(dy)) {
                        val isRtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
                        val goNext = if (isRtl) dx > 0 else dx < 0
                        val targetIndex = if (goNext) {
                            (currentSpotlightIndex + 1) % spotlightApps.size
                        } else {
                            (currentSpotlightIndex - 1 + spotlightApps.size) % spotlightApps.size
                        }
                        showSpotlightAt(targetIndex, slideFromEnd = goNext, animate = true)
                        startSpotlightAutoScroll()
                        view.isPressed = false
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    false
                }
                else -> false
            }
        }
    }

    private fun startSpotlightAutoScroll() {
        spotlightAutoScrollJob?.cancel()
        if (spotlightApps.size <= 1) return
        spotlightAutoScrollJob = lifecycleScope.launch {
            while (isActive) {
                delay(5500L)
                if (!isFinishing && !isDestroyed && binding.cardHeroSpotlight.isVisible && spotlightApps.size > 1) {
                    val nextIndex = (currentSpotlightIndex + 1) % spotlightApps.size
                    showSpotlightAt(nextIndex, slideFromEnd = true, animate = true)
                }
            }
        }
    }

    private fun stopSpotlightAutoScroll() {
        spotlightAutoScrollJob?.cancel()
        spotlightAutoScrollJob = null
    }

    private fun showSpotlightAt(targetIndex: Int, slideFromEnd: Boolean, animate: Boolean) {
        if (spotlightApps.isEmpty()) return
        val safeIndex = targetIndex.coerceIn(0, spotlightApps.lastIndex)
        currentSpotlightIndex = safeIndex
        val uiState = spotlightApps[safeIndex]

        if (!animate) {
            bindSpotlightCard(uiState)
            renderSpotlightDots()
            return
        }

        val density = resources.displayMetrics.density
        val isRtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val shiftPx = 16f * density * (if (slideFromEnd) 1f else -1f) * (if (isRtl) -1f else 1f)

        binding.layoutHeroContent.animate().cancel()
        binding.layoutHeroContent.animate()
            .alpha(0f)
            .translationX(-shiftPx * 0.5f)
            .setDuration(130)
            .withEndAction {
                if (isFinishing || isDestroyed) return@withEndAction
                bindSpotlightCard(uiState)
                renderSpotlightDots()
                binding.layoutHeroContent.translationX = shiftPx
                binding.layoutHeroContent.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(220)
                    .start()
            }
            .start()
    }

    private fun renderSpotlightDots() {
        val container = binding.layoutHeroDots
        container.removeAllViews()
        if (spotlightApps.size <= 1) {
            container.isVisible = false
            return
        }
        container.isVisible = true

        val density = resources.displayMetrics.density
        val colorPrimary = MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary)
        val colorOnPrimaryContainer = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnPrimaryContainer)
        val inactiveColor = ColorUtils.setAlphaComponent(colorOnPrimaryContainer, 72)

        val dotHeight = (6 * density).toInt()
        val activeWidth = (20 * density).toInt()
        val inactiveWidth = (6 * density).toInt()
        val horizontalMargin = (3 * density).toInt()

        spotlightApps.indices.forEach { index ->
            val isSelected = index == currentSpotlightIndex
            val dotView = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    if (isSelected) activeWidth else inactiveWidth,
                    dotHeight
                ).apply {
                    setMargins(horizontalMargin, 0, horizontalMargin, 0)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dotHeight / 2f
                    setColor(if (isSelected) colorPrimary else inactiveColor)
                }
                setOnClickListener {
                    if (index != currentSpotlightIndex) {
                        showSpotlightAt(index, slideFromEnd = index > currentSpotlightIndex, animate = true)
                        startSpotlightAutoScroll()
                    }
                }
            }
            container.addView(dotView)
        }
    }

    private fun bindSpotlightCard(uiState: AppItemUiState) {
        val app = uiState.app

        binding.txtHeroIndicator.text = "${currentSpotlightIndex + 1} / ${spotlightApps.size}"
        binding.txtHeroTitle.text = app.name
        val sizeText = app.getFormattedSize().ifBlank { "نسخة رسمية" }
        binding.txtHeroSub.text = "${app.developer}  •  v${app.versionName}  •  $sizeText"

        when (uiState.status) {
            AppInstallStatus.UPDATE_AVAILABLE -> {
                binding.txtHeroBadge.text = "🚀 تحديث هام متوفر الآن"
                binding.btnHeroAction.text = "تحديث"
            }
            AppInstallStatus.DOWNLOADED -> {
                binding.txtHeroBadge.text = "✨ جاهز للتثبيت الفوري"
                binding.btnHeroAction.text = "تثبيت"
            }
            AppInstallStatus.INSTALLED -> {
                binding.txtHeroBadge.text = "👑 مثبت ومحدث على جهازك"
                binding.btnHeroAction.text = "فتح"
            }
            AppInstallStatus.NOT_INSTALLED -> {
                binding.txtHeroBadge.text = "👑 مختارات المتجر • أحدث إصدار"
                binding.btnHeroAction.text = "تنزيل"
            }
        }

        Glide.with(this)
            .load(app.iconUrl)
            .placeholder(R.mipmap.ic_launcher)
            .error(R.mipmap.ic_launcher)
            .dontAnimate()
            .into(binding.imgHeroIcon)

        Glide.with(this)
            .load(app.iconUrl)
            .placeholder(R.mipmap.ic_launcher)
            .error(R.mipmap.ic_launcher)
            .dontAnimate()
            .into(binding.imgHeroWatermark)

        binding.txtHeroIndicator.setOnClickListener {
            if (spotlightApps.size > 1) {
                val nextIndex = (currentSpotlightIndex + 1) % spotlightApps.size
                showSpotlightAt(nextIndex, slideFromEnd = true, animate = true)
                startSpotlightAutoScroll()
            }
        }

        binding.cardHeroSpotlight.setOnClickListener {
            startActivity(Intent(this, AppDetailsActivity::class.java).apply {
                putExtra("APP_DATA", app)
            })
        }

        binding.btnHeroAction.setOnClickListener {
            startActivity(Intent(this, AppDetailsActivity::class.java).apply {
                putExtra("APP_DATA", app)
            })
        }
    }

    private fun applyThemeMode(targetMode: Int, toggleGroup: MaterialButtonToggleGroup?, showToast: Boolean) {
        val prefs = getSharedPreferences("FixEnginePrefs", MODE_PRIVATE)
        prefs.edit().putInt("theme_mode", targetMode).apply()

        isUpdatingThemeProgrammatically = true
        val targetButtonId = when (targetMode) {
            AppCompatDelegate.MODE_NIGHT_NO -> R.id.btnThemeLight
            AppCompatDelegate.MODE_NIGHT_YES -> R.id.btnThemeDark
            else -> R.id.btnThemeAuto
        }
        if (toggleGroup != null && toggleGroup.checkedButtonId != targetButtonId) {
            toggleGroup.check(targetButtonId)
        }
        isUpdatingThemeProgrammatically = false

        if (showToast) {
            val modeText = when (targetMode) {
                AppCompatDelegate.MODE_NIGHT_NO -> "تم تفعيل الوضع النهاري ☀️"
                AppCompatDelegate.MODE_NIGHT_YES -> "تم تفعيل الوضع الليلي 🌙"
                else -> "تم تفعيل الوضع التلقائي 🔄"
            }
            Toast.makeText(this, modeText, Toast.LENGTH_SHORT).show()
        }

        AppCompatDelegate.setDefaultNightMode(targetMode)
    }

    private fun applyRoundedDrawerSurface() {
        val cornerRadiusPx = 28f * resources.displayMetrics.density
        val isRtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val shapeBuilder = ShapeAppearanceModel.builder()

        if (isRtl) {
            shapeBuilder
                .setTopLeftCorner(CornerFamily.ROUNDED, cornerRadiusPx)
                .setBottomLeftCorner(CornerFamily.ROUNDED, cornerRadiusPx)
        } else {
            shapeBuilder
                .setTopRightCorner(CornerFamily.ROUNDED, cornerRadiusPx)
                .setBottomRightCorner(CornerFamily.ROUNDED, cornerRadiusPx)
        }

        val surfaceColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurface)
        val drawerBg = MaterialShapeDrawable(shapeBuilder.build()).apply {
            fillColor = ColorStateList.valueOf(surfaceColor)
        }
        binding.navView.background = drawerBg
        binding.navView.clipToOutline = true
    }

    private fun setupNavigationDrawer() {
        val prefs = getSharedPreferences("FixEnginePrefs", MODE_PRIVATE)

        applyRoundedDrawerSurface()

        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        val headerView = if (binding.navView.headerCount > 0) binding.navView.getHeaderView(0) else null
        val txtHeaderVersion = headerView?.findViewById<TextView>(R.id.txtHeaderVersion)
        val toggleGroupTheme = headerView?.findViewById<MaterialButtonToggleGroup>(R.id.toggleGroupTheme)

        toggleGroupTheme?.isSaveEnabled = false

        val isNightActive = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        binding.btnThemeQuickToggle.setImageResource(
            if (isNightActive) R.drawable.ic_header_sun else R.drawable.ic_header_moon
        )

        binding.btnThemeQuickToggle.setOnClickListener {
            val savedMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            val currentlyDark = when (savedMode) {
                AppCompatDelegate.MODE_NIGHT_YES -> true
                AppCompatDelegate.MODE_NIGHT_NO -> false
                else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }

            val nextMode = if (currentlyDark) {
                AppCompatDelegate.MODE_NIGHT_NO
            } else {
                AppCompatDelegate.MODE_NIGHT_YES
            }

            applyThemeMode(nextMode, toggleGroupTheme, showToast = true)
        }

        if (headerView != null) {
            try {
                val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(packageName, 0)
                }
                txtHeaderVersion?.text = "الإصدار ${pInfo.versionName} الرسمي • 🛡️"
            } catch (_: Exception) {}

            val currentNightMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            val initialCheckedId = when (currentNightMode) {
                AppCompatDelegate.MODE_NIGHT_NO -> R.id.btnThemeLight
                AppCompatDelegate.MODE_NIGHT_YES -> R.id.btnThemeDark
                else -> R.id.btnThemeAuto
            }

            isUpdatingThemeProgrammatically = true
            toggleGroupTheme?.check(initialCheckedId)
            isUpdatingThemeProgrammatically = false

            toggleGroupTheme?.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (!isChecked || isUpdatingThemeProgrammatically) return@addOnButtonCheckedListener

                val selectedMode = when (checkedId) {
                    R.id.btnThemeLight -> AppCompatDelegate.MODE_NIGHT_NO
                    R.id.btnThemeDark -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }

                if (selectedMode != prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)) {
                    applyThemeMode(selectedMode, toggleGroupTheme, showToast = true)
                }
            }
        }

        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_settings -> showSettingsAndStorageDialog()
                R.id.nav_dev_about -> showAboutDeveloperDialog()
                R.id.nav_add_app -> showAddAppDeveloperDialog()
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun showRepoStatusDialog() {
        if (isFinishing || isDestroyed) return
        MaterialAlertDialogBuilder(this, R.style.Theme_FixEngine_Dialog)
            .setTitle("تنبيه مزامنة المتجر 🌐")
            .setMessage(
                "تعذر الوصول إلى قائمة التطبيقات في الوقت الحالي، وقد يعود ذلك إلى أحد الأسباب التالية:\n\n" +
                        "• عدم استقرار أو انقطاع الاتصال بالإنترنت في جهازك.\n" +
                        "• أو جاري حالياً رفع تطبيقات حديثة وتحديث ملف المستودع (JSON).\n\n" +
                        "يرجى التحقق من الشبكة أو الانتظار لحظات ثم الضغط على إعادة المحاولة."
            )
            .setPositiveButton("إعادة المحاولة 🔄") { dialog, _ ->
                dialog.dismiss()
                refreshData()
            }
            .setNegativeButton("حسناً", null)
            .show()
    }

    private fun showAboutDeveloperDialog() {
        if (isFinishing || isDestroyed) return
        MaterialAlertDialogBuilder(this, R.style.Theme_FixEngine_Dialog)
            .setTitle("حول المطور")
            .setMessage("تم تطوير المتجر بواسطة م/ عبدالله التميمي.\nنهدف إلى تقديم تجربة فريدة، آمنة واحترافية لإدارة وتحديث تطبيقات الأندرويد المتقدمة.")
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

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
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
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:$packageName")
                })
            } catch (e: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES))
                } catch (_: Exception) {}
            }
            dialog.dismiss()
        }

        dialogView.findViewById<MaterialButton>(R.id.btn_negative)?.setOnClickListener {
            getSharedPreferences("FixEnginePrefs", MODE_PRIVATE).edit().putBoolean("install_dialog_shown", true).apply()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun checkMandatoryUpdate(apps: List<AppModel>) {
        val storeApp = apps.find { it.packageName == packageName } ?: return

        try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }

            val installedVersion = pInfo.versionName ?: ""

            if (compareVersionNames(storeApp.versionName, installedVersion) > 0) {
                showMandatoryUpdateDialog(storeApp)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun compareVersionNames(serverVersion: String, installedVersion: String): Int {
        try {
            val sParts = serverVersion.trim().split(".")
            val iParts = installedVersion.trim().split(".")
            val maxLen = maxOf(sParts.size, iParts.size)

            for (i in 0 until maxLen) {
                val sVal = sParts.getOrNull(i)?.toIntOrNull() ?: 0
                val iVal = iParts.getOrNull(i)?.toIntOrNull() ?: 0

                if (sVal > iVal) return 1
                if (sVal < iVal) return -1
            }
        } catch (e: Exception) {
            return if (serverVersion.trim() != installedVersion.trim()) 1 else 0
        }
        return 0
    }

    private fun showMandatoryUpdateDialog(storeApp: AppModel) {
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "store_updates_channel",
                "تحديثات المتجر",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "تنبيهات تلقائية عند توفر تحديثات جديدة للتطبيقات المثبتة" }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun sendUpdateNotification(updatesCount: Int) {
        try {
            createNotificationChannel()
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )

            val builder = NotificationCompat.Builder(this, "store_updates_channel")
                .setSmallIcon(R.drawable.ic_notification_transparent)
                .setContentTitle("تحديثات متوفرة لـ تطبيقاتك! 🚀")
                .setContentText("يوجد عدد ($updatesCount) من تطبيقاتك تمتلك إصدارات محدثة، قم بتثبيتها الآن.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(1001, builder.build())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupTabs() {
        binding.viewPagerMain.adapter = MainPagerAdapter(this)

        floatingNavController = FloatingNavBarController(
            binding = binding.floatingNav,
            viewPager = binding.viewPagerMain,
            onTabReselected = {
                binding.appBar.setExpanded(true, true)
            }
        )
    }

    private fun setupSearchLogic() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                binding.btnClearSearch.isVisible = query.isNotEmpty()
                binding.cardHeroSpotlight.isVisible = query.isBlank() && spotlightApps.isNotEmpty()
                viewModel.filterApps(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.setText("")
            binding.etSearch.clearFocus()
        }

        binding.btnSortApps.setOnClickListener {
            showSortOptionsDialog()
        }
        binding.txtActiveSortLabel.setOnClickListener {
            showSortOptionsDialog()
        }

        binding.btnRefreshMain.setOnClickListener { view ->
            view.animate().rotationBy(360f).setDuration(600).start()
            Toast.makeText(this, "جاري تحديث قائمة التطبيقات... 🔄", Toast.LENGTH_SHORT).show()
            try {
                val secureUrl = getSecureRepoUrl()
                viewModel.forceReloadApps(secureUrl.toByteArray(Charsets.UTF_8), 0.toByte())
            } catch (e: Exception) {
                refreshData()
            }
        }

        binding.chipGroupCategories.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: R.id.chipAll
            when (checkedId) {
                R.id.chipAll -> {
                    binding.cardHeroSpotlight.isVisible = binding.etSearch.text.isNullOrBlank() && spotlightApps.isNotEmpty()
                    viewModel.filterApps(binding.etSearch.text.toString())
                }
                R.id.chipAbMods -> {
                    binding.cardHeroSpotlight.isVisible = false
                    viewModel.filterApps("ABWhatsApp")
                }
                R.id.chipObMods -> {
                    binding.cardHeroSpotlight.isVisible = false
                    viewModel.filterApps("عمر")
                }
                R.id.chipShieldMods -> {
                    binding.cardHeroSpotlight.isVisible = false
                    viewModel.filterApps("درع الحماية")
                }
            }
        }
    }

    private fun showSortOptionsDialog() {
        if (isFinishing || isDestroyed) return
        val currentIndex = viewModel.getCurrentSortMode().coerceIn(0, sortLabels.lastIndex)

        MaterialAlertDialogBuilder(this, R.style.Theme_FixEngine_Dialog)
            .setTitle("ترتيب التطبيقات حسب ⇅")
            .setSingleChoiceItems(sortLabels, currentIndex) { dialog, which ->
                viewModel.setSortMode(which)
                dialog.dismiss()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun setupSearchAnimation() {
        val colorPrimary = MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary)
        val colorOutlineVariant = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOutlineVariant)
        val density = resources.displayMetrics.density
        val focusedStrokePx = (1.75f * density).toInt().coerceAtLeast(2)
        val normalStrokePx = (1f * density).toInt().coerceAtLeast(1)

        binding.etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.searchCard.animate().scaleX(1.01f).scaleY(1.01f).setDuration(220).start()
                binding.searchCard.strokeWidth = focusedStrokePx
                binding.searchCard.strokeColor = colorPrimary
            } else {
                binding.searchCard.animate().scaleX(1f).scaleY(1f).setDuration(220).start()
                binding.searchCard.strokeWidth = normalStrokePx
                binding.searchCard.strokeColor = colorOutlineVariant
            }
        }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.isVisible = isLoading
            if (isLoading && !isDataLoadedSuccessfully) {
                binding.txtCatalogSummary.text = "جاري مزامنة أحدث التطبيقات..."
            }
        }

        viewModel.activeSortMode.observe(this) { mode ->
            val safeIdx = (mode ?: 0).coerceIn(0, sortShortLabels.lastIndex)
            binding.txtActiveSortLabel.text = "${sortShortLabels[safeIdx]} ▾"
        }

        viewModel.appsUiStateList.observe(this) { uiStates ->
            if (uiStates != null) {
                binding.txtCatalogSummary.text = "متوفر ${uiStates.size} تطبيق في القائمة"
                if (uiStates.isNotEmpty()) {
                    isDataLoadedSuccessfully = true
                    val apps = uiStates.map { it.app }
                    checkMandatoryUpdate(apps)

                    if (spotlightApps.isEmpty()) {
                        updateSpotlightList(uiStates)
                    }

                    if (!isBackgroundUpdateScheduled) {
                        val hasNotifPerm = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                            ContextCompat.checkSelfPermission(
                                this,
                                android.Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                        if (hasNotifPerm) {
                            isBackgroundUpdateScheduled = true
                            runCatching {
                                UpdateWorker.schedulePeriodicCheck(applicationContext, getSecureRepoUrl())
                            }
                        }
                    }
                }
            }
        }

        viewModel.updatesUiStateList.observe(this) { updateStates ->
            if (updateStates != null) {
                val updateCount = updateStates.size
                floatingNavController?.setUpdatesBadge(updateCount)

                if (updateCount > 0 && binding.etSearch.text.isNullOrBlank() && !isNotificationSent) {
                    sendUpdateNotification(updateCount)
                    isNotificationSent = true
                }
            }
        }

        viewModel.installedUiStateList.observe(this) { installedStates ->
            if (installedStates != null) {
                floatingNavController?.setInstalledBadge(installedStates.size)
            }
        }

        viewModel.errorMessage.observe(this) {
            it?.let {
                isDataLoadedSuccessfully = false
                showRepoStatusDialog()
            }
        }
    }

    private fun refreshData() {
        try {
            val secureUrl = getSecureRepoUrl()
            val urlBytes = secureUrl.toByteArray(Charsets.UTF_8)
            viewModel.loadApps(urlBytes, 0.toByte())
        } catch (e: Exception) {
            Toast.makeText(this, "خطأ في معالجة بوابة الأمان", Toast.LENGTH_LONG).show()
            isDataLoadedSuccessfully = false
        }
    }

    private fun showSettingsAndStorageDialog() {
        if (isFinishing || isDestroyed) return

        val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val apkFiles = downloadsDir?.listFiles { file ->
            val name = file.name.lowercase()
            file.isFile && (name.endsWith(".apk") || name.endsWith(".apkm") || name.endsWith(".xapk") || name.endsWith(".apks") || name.endsWith(".tmp") || name.endsWith(".part") || name.endsWith(".part.meta") || name.endsWith(".parts"))
        } ?: emptyArray()

        val totalSizeBytes = apkFiles.sumOf { it.length() }
        val totalSizeMb = String.format(Locale.US, "%.1f MB", totalSizeBytes / (1024.0 * 1024.0))

        val message = "📦 عدد ملفات التثبيت المحفوظة: ${apkFiles.size} ملف\n" +
                "💾 المساحة المستهلكة: $totalSizeMb\n\n" +
                "يمكنك مسح ملفات التثبيت المؤقتة بعد تثبيت تطبيقاتك لتوفير مساحة تخزين إضافية في هاتفك."

        MaterialAlertDialogBuilder(this, R.style.Theme_FixEngine_Dialog)
            .setTitle("إعدادات المتجر والتخزين ⚙️")
            .setMessage(message)
            .setPositiveButton("تنظيف المساحة ($totalSizeMb)") { dialog, _ ->
                var deletedCount = 0
                apkFiles.forEach { if (it.delete()) deletedCount++ }
                viewModel.refreshAppStates()
                Toast.makeText(this, "تم تنظيف $deletedCount ملف وتفريغ المساحة بنجاح! ✨", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun dismissAnnouncementSmoothly() {
        val card = binding.cardAnnouncement
        if (!card.isVisible) return
        card.animate().cancel()
        card.animate()
            .alpha(0f)
            .scaleY(0.92f)
            .translationY(-10f * resources.displayMetrics.density)
            .setDuration(220)
            .withEndAction {
                card.isVisible = false
                card.alpha = 1f
                card.scaleY = 1f
                card.translationY = 0f
            }
            .start()
    }

    private fun observeStoreAnnouncement() {
        try {
            val dbUrl = "https://abdullah-store-a95ed-default-rtdb.europe-west1.firebasedatabase.app"
            FirebaseDatabase.getInstance(dbUrl)
                .getReference("store_announcement")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val isActive = snapshot.child("active").getValue(Boolean::class.java) ?: false
                        val title = snapshot.child("title").getValue(String::class.java) ?: "📢 إشعار هام"
                        val body = snapshot.child("body").getValue(String::class.java) ?: ""

                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            if (isActive && body.isNotBlank()) {
                                binding.txtAnnouncementTitle.text = title
                                binding.txtAnnouncementBody.text = body
                                if (!binding.cardAnnouncement.isVisible) {
                                    binding.cardAnnouncement.alpha = 0f
                                    binding.cardAnnouncement.translationY = -12f * resources.displayMetrics.density
                                    binding.cardAnnouncement.isVisible = true
                                    binding.cardAnnouncement.animate()
                                        .alpha(1f)
                                        .translationY(0f)
                                        .setDuration(260)
                                        .start()
                                }
                                binding.btnDismissAnnouncement.setOnClickListener {
                                    dismissAnnouncementSmoothly()
                                }
                            } else {
                                binding.cardAnnouncement.isVisible = false
                            }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {}
                })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
