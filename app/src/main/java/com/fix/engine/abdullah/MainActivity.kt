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
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityOptionsCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: ظ…طھط¬ط± Abdullah (Official Runtime Core)
 * Feature: Hero Spotlight Banner, Smart Sort Control, 3-Tab Live Badges, Synced Day/Night Theme & Smart Diagnostics
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkCallback: ConnectivityManager.NetworkCallback
    private var isNetworkCallbackRegistered = false
    private var isDataLoadedSuccessfully = false

    private var isNotificationSent = false
    private var isUpdatingThemeProgrammatically = false
    private var activeErrorDialog: AlertDialog? = null

    private var spotlightApps: List<AppItemUiState> = emptyList()
    private var currentSpotlightIndex = 0
    private var spotlightRotationJob: Job? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "طھظ… ط±ظپط¶ طµظ„ط§ط­ظٹط© ط§ظ„ط¥ط´ط¹ط§ط±ط§طھ", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        init {
            System.loadLibrary("native-lib")
        }
    }

    private external fun verifySignatureNative(context: Context)
    private external fun getSecureRepoUrl(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences("FixEnginePrefs", MODE_PRIVATE)
        val savedTheme = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        if (AppCompatDelegate.getDefaultNightMode() != savedTheme) {
            AppCompatDelegate.setDefaultNightMode(savedTheme)
        }

        super.onCreate(savedInstanceState)

        verifySignatureNative(applicationContext)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupTabs()
        setupNavigationDrawer()
        setupObservers()
        setupSearchAndSortLogic()
        setupSearchAnimation()
        observeStoreAnnouncement()

        checkNotificationPermission()

        lifecycleScope.launch {
            delay(1000)
            checkInstallPermission()
        }

        refreshData()
    }

    private fun isInternetAvailable(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    private fun registerNetworkMonitoring() {
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        activeErrorDialog?.dismiss()
                        if (!isDataLoadedSuccessfully) {
                            Toast.makeText(this@MainActivity, "طھظ… ط§ط³طھط¹ط§ط¯ط© ط§ظ„ط§طھطµط§ظ„! ط¬ط§ط±ظٹ ظ…ط²ط§ظ…ظ†ط© ط§ظ„ظ…طھط¬ط±... ًں”„", Toast.LENGTH_SHORT).show()
                            refreshData()
                        }
                    }
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        Toast.makeText(this@MainActivity, "طھظ†ط¨ظٹظ‡: ط§ظ†ظ‚ط·ط¹ ط§ظ„ط§طھطµط§ظ„ ط¨ط§ظ„ط¥ظ†طھط±ظ†طھ ًںŒگ", Toast.LENGTH_SHORT).show()
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
        startSpotlightAutoRotation()
    }

    override fun onStop() {
        super.onStop()
        spotlightRotationJob?.cancel()
        if (isNetworkCallbackRegistered) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            isNetworkCallbackRegistered = false
        }
    }

    // ====================================================================
    // ًں‘‘ ظ…ط­ط±ظƒ ط§ظ„ط¨ط§ظ†ط± ط§ظ„طھظپط§ط¹ظ„ظٹ ظ„ظ„طھط·ط¨ظٹظ‚ط§طھ ط§ظ„ظ…ظ…ظٹط²ط© (Hero Spotlight Carousel)
    // ====================================================================

    private fun updateSpotlightList(allStates: List<AppItemUiState>) {
        // ط§ط®طھظٹط§ط± ط£ط¨ط±ط² ط§ظ„طھط·ط¨ظٹظ‚ط§طھ (ط§ظ„طھظٹ ظٹطھظˆظپط± ظ„ظ‡ط§ طھط­ط¯ظٹط« ط£ظˆظ„ط§ظ‹ ط«ظ… ط£ط­ط¯ط« ط§ظ„طھط·ط¨ظٹظ‚ط§طھ ظپظٹ ط§ظ„ظ…طھط¬ط±طŒ ظ…ط¹ ط§ط³طھط¨ط¹ط§ط¯ ط­ط²ظ…ط© ط§ظ„ظ…طھط¬ط± ظ†ظپط³ظ‡)
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
            bindSpotlightCard(spotlightApps[currentSpotlightIndex], animate = false)
            startSpotlightAutoRotation()
        } else {
            spotlightRotationJob?.cancel()
        }
    }

    private fun startSpotlightAutoRotation() {
        spotlightRotationJob?.cancel()
        if (spotlightApps.size <= 1) return

        spotlightRotationJob = lifecycleScope.launch {
            while (isActive) {
                delay(5500)
                if (!isFinishing && !isDestroyed && binding.cardHeroSpotlight.isVisible && spotlightApps.size > 1) {
                    currentSpotlightIndex = (currentSpotlightIndex + 1) % spotlightApps.size
                    bindSpotlightCard(spotlightApps[currentSpotlightIndex], animate = true)
                }
            }
        }
    }

    private fun bindSpotlightCard(uiState: AppItemUiState, animate: Boolean) {
        val app = uiState.app

        val updateAction = Runnable {
            binding.txtHeroIndicator.text = "${currentSpotlightIndex + 1} / ${spotlightApps.size}"
            binding.txtHeroTitle.text = app.name
            val sizeText = app.getFormattedSize().ifBlank { "ظ†ط³ط®ط© ط±ط³ظ…ظٹط©" }
            binding.txtHeroSub.text = "${app.developer}  â€¢  v${app.versionName}  â€¢  $sizeText"

            when (uiState.status) {
                AppInstallStatus.UPDATE_AVAILABLE -> {
                    binding.txtHeroBadge.text = "ًںڑ€ طھط­ط¯ظٹط« ظ‡ط§ظ… ظ…طھظˆظپط± ط§ظ„ط¢ظ†"
                    binding.btnHeroAction.text = "طھط­ط¯ظٹط«"
                }
                AppInstallStatus.DOWNLOADED -> {
                    binding.txtHeroBadge.text = "âœ¨ ط¬ط§ظ‡ط² ظ„ظ„طھط«ط¨ظٹطھ ط§ظ„ظپظˆط±ظٹ"
                    binding.btnHeroAction.text = "طھط«ط¨ظٹطھ"
                }
                AppInstallStatus.INSTALLED -> {
                    binding.txtHeroBadge.text = "ًں‘‘ ظ…ط«ط¨طھ ظˆظ…ط­ط¯ط« ط¹ظ„ظ‰ ط¬ظ‡ط§ط²ظƒ"
                    binding.btnHeroAction.text = "ظپطھط­"
                }
                AppInstallStatus.NOT_INSTALLED -> {
                    binding.txtHeroBadge.text = "ًں‘‘ ظ…ط®طھط§ط±ط§طھ ط§ظ„ظ…طھط¬ط± â€¢ ط£ط­ط¯ط« ط¥طµط¯ط§ط±"
                    binding.btnHeroAction.text = "طھظ†ط²ظٹظ„"
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

            val openDetails = {
                val intent = Intent(this, AppDetailsActivity::class.java).apply {
                    putExtra("APP_DATA", app)
                }
                val uniqueTransitionName = "transition_app_icon_${app.packageName}"
                ViewCompat.setTransitionName(binding.imgHeroIcon, uniqueTransitionName)
                val options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                    this,
                    binding.imgHeroIcon,
                    uniqueTransitionName
                )
                startActivity(intent, options.toBundle())
            }

            binding.cardHeroSpotlight.setOnClickListener { openDetails() }
            binding.btnHeroAction.setOnClickListener { openDetails() }
        }

        if (animate) {
            binding.cardHeroSpotlight.animate()
                .alpha(0.65f)
                .scaleX(0.98f)
                .scaleY(0.98f)
                .setDuration(140)
                .withEndAction {
                    updateAction.run()
                    binding.cardHeroSpotlight.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .start()
                }
                .start()
        } else {
            updateAction.run()
        }
    }

    // ====================================================================
    // ًںژ¨ ط¥ط¯ط§ط±ط© ط§ظ„ظ…ط¸ظ‡ط± ظˆط§ظ„ظ‚ط§ط¦ظ…ط© ط§ظ„ط¬ط§ظ†ط¨ظٹط©
    // ====================================================================

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
                AppCompatDelegate.MODE_NIGHT_NO -> "طھظ… طھظپط¹ظٹظ„ ط§ظ„ظˆط¶ط¹ ط§ظ„ظ†ظ‡ط§ط±ظٹ âک€ï¸ڈ"
                AppCompatDelegate.MODE_NIGHT_YES -> "طھظ… طھظپط¹ظٹظ„ ط§ظ„ظˆط¶ط¹ ط§ظ„ظ„ظٹظ„ظٹ ًںŒ™"
                else -> "طھظ… طھظپط¹ظٹظ„ ط§ظ„ظˆط¶ط¹ ط§ظ„طھظ„ظ‚ط§ط¦ظٹ ًں”„"
            }
            Toast.makeText(this, modeText, Toast.LENGTH_SHORT).show()
        }

        AppCompatDelegate.setDefaultNightMode(targetMode)
    }

    private fun setupNavigationDrawer() {
        val prefs = getSharedPreferences("FixEnginePrefs", MODE_PRIVATE)

        binding.btnMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        val headerView = if (binding.navView.headerCount > 0) binding.navView.getHeaderView(0) else null
        val txtHeaderVersion = headerView?.findViewById<TextView>(R.id.txtHeaderVersion)
        val toggleGroupTheme = headerView?.findViewById<MaterialButtonToggleGroup>(R.id.toggleGroupTheme)

        toggleGroupTheme?.isSaveEnabled = false

        val isNightActive = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        binding.btnThemeQuickToggle.setImageResource(
            if (isNightActive) android.R.drawable.ic_menu_day else android.R.drawable.ic_menu_recent_history
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
                txtHeaderVersion?.text = "ط§ظ„ط¥طµط¯ط§ط± ${pInfo.versionName} ط§ظ„ط±ط³ظ…ظٹ â€¢ ًں›،ï¸ڈ"
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

    private fun showSmartRepoDiagnosticDialog() {
        if (isFinishing || isDestroyed) return
        activeErrorDialog?.dismiss()

        val hasInternet = isInternetAvailable()

        val builder = MaterialAlertDialogBuilder(this, R.style.Theme_FixEngine_Dialog)
            .setCancelable(true)

        if (!hasInternet) {
            builder.setTitle("ظ„ط§ ظٹظˆط¬ط¯ ط§طھطµط§ظ„ ط¨ط§ظ„ط¥ظ†طھط±ظ†طھ ًںŒگ")
                .setMessage(
                    "ظٹط¨ط¯ظˆ ط£ظ† ط¬ظ‡ط§ط²ظƒ ط؛ظٹط± ظ…طھطµظ„ ط¨ط§ظ„ط¥ظ†طھط±ظ†طھ ط­ط§ظ„ظٹط§ظ‹.\n\n" +
                            "â€¢ ظٹط±ط¬ظ‰ ط§ظ„طھط­ظ‚ظ‚ ظ…ظ† ط§طھطµط§ظ„ ط§ظ„ط´ط¨ظƒط© (Wi-Fi ط£ظˆ ط¨ظٹط§ظ†ط§طھ ط§ظ„ظ‡ط§طھظپ).\n" +
                            "â€¢ ط³ظٹظ‚ظˆظ… ط§ظ„ظ…طھط¬ط± ط¨ط¬ظ„ط¨ ظˆظ…ط²ط§ظ…ظ†ط© ط§ظ„طھط·ط¨ظٹظ‚ط§طھ طھظ„ظ‚ط§ط¦ظٹط§ظ‹ ظپظˆط± ط¹ظˆط¯ط© ط§ظ„ط§طھطµط§ظ„."
                )
                .setPositiveButton("ط¥ط¹ط§ط¯ط© ط§ظ„ظ…ط­ط§ظˆظ„ط© ًں”„") { dialog, _ ->
                    dialog.dismiss()
                    refreshData()
                }
                .setNeutralButton("ط¥ط¹ط¯ط§ط¯ط§طھ ط§ظ„ط´ط¨ظƒط© âڑ™ï¸ڈ") { dialog, _ ->
                    dialog.dismiss()
                    try {
                        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                    } catch (_: Exception) {
                        try {
                            startActivity(Intent(Settings.ACTION_SETTINGS))
                        } catch (_: Exception) {}
                    }
                }
                .setNegativeButton("ط¥ط؛ظ„ط§ظ‚", null)
        } else {
            builder.setTitle("طھط­ط¯ظٹط« ط§ظ„ظ…ط³طھظˆط¯ط¹ ط¬ط§ط±ظٹ ط§ظ„ط¢ظ† ًں› ï¸ڈ")
                .setMessage(
                    "ط§طھطµط§ظ„ظƒ ط¨ط§ظ„ط¥ظ†طھط±ظ†طھ ظٹط¹ظ…ظ„ ط¨ط´ظƒظ„ ط³ظ„ظٹظ…طŒ ظˆظ„ظƒظ† ظٹطھط¹ط°ط± ظ‚ط±ط§ط،ط© ظ‚ط§ط¦ظ…ط© ط§ظ„طھط·ط¨ظٹظ‚ط§طھ ظ…ط¤ظ‚طھط§ظ‹ ظ„ط£ط­ط¯ ط§ظ„ط£ط³ط¨ط§ط¨ ط§ظ„طھط§ظ„ظٹط©:\n\n" +
                            "ًں“¦ ط¬ط§ط±ظٹ ط­ط§ظ„ظٹط§ظ‹ ط±ظپط¹ طھط·ط¨ظٹظ‚ط§طھ ط£ظˆ ط¥طµط¯ط§ط±ط§طھ ط­ط¯ظٹط«ط© ط¥ظ„ظ‰ ط§ظ„ظ…ط³طھظˆط¯ط¹.\n" +
                            "ًں”§ ط£ظˆ ظٹطھظ… ط¥ط¬ط±ط§ط، ط¹ظ…ظ„ظٹط© ط¥طµظ„ط§ط­ ظˆطھط­ط¯ظٹط« ط³ط±ظٹط¹ط© ظ„ظ‚ط§ط¹ط¯ط© ط§ظ„ط¨ظٹط§ظ†ط§طھ (JSON).\n\n" +
                            "ظٹط±ط¬ظ‰ ط§ظ„ط§ظ†طھط¸ط§ط± ظ„ط­ط¸ط§طھ ظ‚ظ„ظٹظ„ط© ط«ظ… ط§ظ„ط¶ط؛ط· ط¹ظ„ظ‰ ط¥ط¹ط§ط¯ط© ط§ظ„ظ…ط­ط§ظˆظ„ط©."
                )
                .setPositiveButton("ط¥ط¹ط§ط¯ط© ط§ظ„ظ…ط­ط§ظˆظ„ط© ط§ظ„ط¢ظ† ًں”„") { dialog, _ ->
                    dialog.dismiss()
                    try {
                        val secureUrl = getSecureRepoUrl()
                        viewModel.forceReloadApps(secureUrl.toByteArray(Charsets.UTF_8), 0.toByte())
                    } catch (e: Exception) {
                        refreshData()
                    }
                }
                .setNegativeButton("ط­ط³ظ†ط§ظ‹طŒ ط³ط£ظ†طھط¸ط±", null)
        }

        activeErrorDialog = builder.create()
        activeErrorDialog?.show()
    }

    private fun showAboutDeveloperDialog() {
        if (isFinishing || isDestroyed) return
        MaterialAlertDialogBuilder(this, R.style.Theme_FixEngine_Dialog)
            .setTitle("ط­ظˆظ„ ط§ظ„ظ…ط·ظˆط±")
            .setMessage("طھظ… طھط·ظˆظٹط± ط§ظ„ظ…طھط¬ط± ط¨ظˆط§ط³ط·ط© ظ…/ ط¹ط¨ط¯ط§ظ„ظ„ظ‡ ط§ظ„طھظ…ظٹظ…ظٹ.\nظ†ظ‡ط¯ظپ ط¥ظ„ظ‰ طھظ‚ط¯ظٹظ… طھط¬ط±ط¨ط© ظپط±ظٹط¯ط©طŒ ط¢ظ…ظ†ط© ظˆط§ط­طھط±ط§ظپظٹط© ظ„ط¥ط¯ط§ط±ط© ظˆطھط­ط¯ظٹط« طھط·ط¨ظٹظ‚ط§طھ ط§ظ„ط£ظ†ط¯ط±ظˆظٹط¯ ط§ظ„ظ…طھظ‚ط¯ظ…ط©.")
            .setPositiveButton("ط­ط³ظ†ط§ظ‹", null)
            .show()
    }

    private fun showAddAppDeveloperDialog() {
        if (isFinishing || isDestroyed) return
        MaterialAlertDialogBuilder(this, R.style.Theme_FixEngine_Dialog)
            .setTitle("ط¥ط¶ط§ظپط© طھط·ط¨ظٹظ‚ظƒ ظپظٹ ط§ظ„ظ…طھط¬ط±")
            .setMessage("ظٹظ…ظƒظ†ظƒظ… ط§ظ„طھظˆط§طµظ„ ظ…ط¨ط§ط´ط±ط© ط¹ظ„ظ‰ ط§ظ„ظˆط§طھط³ط§ط¨ ط§ظ„ط±ظ‚ظ… 770034578 ظ„ط¥ط±ط³ط§ظ„ طھظپط§طµظٹظ„ طھط·ط¨ظٹظ‚ظƒظ…طŒ ظˆط§ظ„ظ…ط±ط§ط¬ط¹ط© ط§ظ„ط¨ط±ظ…ط¬ظٹط© ظ‚ط¨ظ„ ط§ظ„ط±ظپط¹.")
            .setPositiveButton("ظ…ط±ط§ط³ظ„ط© ط§ظ„ط¢ظ†") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/967770034578")))
                } catch (e: Exception) {
                    Toast.makeText(this, "طھط·ط¨ظٹظ‚ ظˆط§طھط³ط§ط¨ ط؛ظٹط± ظ…ط«ط¨طھ ظپظٹ ط¬ظ‡ط§ط²ظƒ", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("ط¥ظ„ط؛ط§ط،", null)
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
            .setTitle("طھط­ط¯ظٹط« ط¥ط¬ط¨ط§ط±ظٹ!")
            .setMessage("ظٹظˆط¬ط¯ ط¥طµط¯ط§ط± ط¬ط¯ظٹط¯ ظ…ظ† ظ…طھط¬ط± Abdullah ظٹط­ظ„ ط¨ط¹ط¶ ط§ظ„ظ…ط´ط§ظƒظ„ ط§ظ„طھظ‚ظ†ظٹط©. ظٹط±ط¬ظ‰ ط§ظ„طھط­ط¯ظٹط« ظ„ظ„ظ…طھط§ط¨ط¹ط©.")
            .setCancelable(false)
            .setPositiveButton("طھط­ط¯ظٹط« ط§ظ„ط¢ظ†") { _, _ ->
                val intent = Intent(this, AppDetailsActivity::class.java).apply {
                    putExtra("APP_DATA", storeApp)
                }
                startActivity(intent)
                finish()
            }
            .setNegativeButton("ط®ط±ظˆط¬") { _, _ -> finishAffinity() }
            .show()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "store_updates_channel",
                "طھط­ط¯ظٹط«ط§طھ ط§ظ„ظ…طھط¬ط±",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "طھظ†ط¨ظٹظ‡ط§طھ طھظ„ظ‚ط§ط¦ظٹط© ط¹ظ†ط¯ طھظˆظپط± طھط­ط¯ظٹط«ط§طھ ط¬ط¯ظٹط¯ط© ظ„ظ„طھط·ط¨ظٹظ‚ط§طھ ط§ظ„ظ…ط«ط¨طھط©" }
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
                .setContentTitle("طھط­ط¯ظٹط«ط§طھ ظ…طھظˆظپط±ط© ظ„ظ€ طھط·ط¨ظٹظ‚ط§طھظƒ! ًںڑ€")
                .setContentText("ظٹظˆط¬ط¯ ط¹ط¯ط¯ ($updatesCount) ظ…ظ† طھط·ط¨ظٹظ‚ط§طھظƒ طھظ…طھظ„ظƒ ط¥طµط¯ط§ط±ط§طھ ظ…ط­ط¯ط«ط©طŒ ظ‚ظ… ط¨طھط«ط¨ظٹطھظ‡ط§ ط§ظ„ط¢ظ†.")
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
        TabLayoutMediator(binding.tabLayoutMain, binding.viewPagerMain) { tab, position ->
            tab.text = when (position) {
                0 -> "ط§ظ„طھط·ط¨ظٹظ‚ط§طھ"
                1 -> "ط§ظ„طھط­ط¯ظٹط«ط§طھ"
                else -> "ط§ظ„ظ…ط«ط¨طھط© ط¨ظ‡ط§طھظپظٹ"
            }
        }.attach()
    }

    private fun setupSearchAndSortLogic() {
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

        // â‡… ط²ط± ط§ظ„ظپط±ط² ط§ظ„ط°ظƒظٹ ط§ظ„ظ…ط³طھظˆط­ظ‰ ظ…ظ† ط§ظ„ظ…طھط§ط¬ط± ط§ظ„ط¹ط§ظ„ظ…ظٹط©
        binding.btnSortApps.setOnClickListener {
            showSortOptionsDialog()
        }
        binding.txtActiveSortLabel.setOnClickListener {
            showSortOptionsDialog()
        }

        binding.btnRefreshMain.setOnClickListener { view ->
            view.animate().rotationBy(360f).setDuration(600).start()
            if (!isInternetAvailable()) {
                showSmartRepoDiagnosticDialog()
                return@setOnClickListener
            }
            Toast.makeText(this, "ط¬ط§ط±ظٹ طھط­ط¯ظٹط« ظ‚ط§ط¦ظ…ط© ط§ظ„طھط·ط¨ظٹظ‚ط§طھ... ًں”„", Toast.LENGTH_SHORT).show()
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
                    viewModel.filterApps("ط¹ظ…ط±")
                }
                R.id.chipShieldMods -> {
                    binding.cardHeroSpotlight.isVisible = false
                    viewModel.filterApps("ط¯ط±ط¹ ط§ظ„ط­ظ…ط§ظٹط©")
                }
            }
        }
    }

    private fun showSortOptionsDialog() {
        if (isFinishing || isDestroyed) return
        val options = MainViewModel.SortOption.values()
        val labels = options.map { it.label }.toTypedArray()
        val currentIndex = options.indexOf(viewModel.getCurrentSortOption()).coerceAtLeast(0)

        MaterialAlertDialogBuilder(this, R.style.Theme_FixEngine_Dialog)
            .setTitle("طھط±طھظٹط¨ ط§ظ„طھط·ط¨ظٹظ‚ط§طھ ط­ط³ط¨ â‡…")
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                val selectedOption = options[which]
                viewModel.setSortOption(selectedOption)
                dialog.dismiss()
            }
            .setNegativeButton("ط¥ظ„ط؛ط§ط،", null)
            .show()
    }

    private fun setupSearchAnimation() {
        val colorPrimary = MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary)
        val colorOutlineVariant = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOutlineVariant)

        binding.etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.searchCard.animate().scaleX(1.01f).scaleY(1.01f).setDuration(250).start()
                binding.searchCard.strokeWidth = 2
                binding.searchCard.strokeColor = colorPrimary
            } else {
                binding.searchCard.animate().scaleX(1f).scaleY(1f).setDuration(250).start()
                binding.searchCard.strokeWidth = 1
                binding.searchCard.strokeColor = colorOutlineVariant
            }
        }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.isVisible = isLoading
            if (isLoading && !isDataLoadedSuccessfully) {
                binding.txtCatalogSummary.text = "ط¬ط§ط±ظٹ ظ…ط²ط§ظ…ظ†ط© ط£ط­ط¯ط« ط§ظ„طھط·ط¨ظٹظ‚ط§طھ..."
            }
        }

        viewModel.activeSortOption.observe(this) { sortOption ->
            if (sortOption != null) {
                binding.txtActiveSortLabel.text = "ط§ظ„طھط±طھظٹط¨: ${sortOption.shortLabel}"
            }
        }

        viewModel.appsUiStateList.observe(this) { uiStates ->
            if (uiStates != null) {
                binding.txtCatalogSummary.text = "ظ…طھظˆظپط± ${uiStates.size} طھط·ط¨ظٹظ‚ ظپظٹ ط§ظ„ظ‚ط§ط¦ظ…ط©"
                if (uiStates.isNotEmpty()) {
                    isDataLoadedSuccessfully = true
                    activeErrorDialog?.dismiss()
                    val apps = uiStates.map { it.app }
                    checkMandatoryUpdate(apps)

                    if (spotlightApps.isEmpty()) {
                        updateSpotlightList(uiStates)
                    }
                }
            }
        }

        viewModel.updatesUiStateList.observe(this) { updateStates ->
            if (updateStates != null) {
                val updateCount = updateStates.size
                val updatesTab = binding.tabLayoutMain.getTabAt(1)
                val badge = updatesTab?.orCreateBadge

                if (updateCount > 0) {
                    badge?.isVisible = true
                    badge?.number = updateCount
                    badge?.backgroundColor = MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorError)

                    if (binding.etSearch.text.isNullOrBlank() && !isNotificationSent) {
                        sendUpdateNotification(updateCount)
                        isNotificationSent = true
                    }
                } else {
                    badge?.isVisible = false
                }
            }
        }

        // ط´ط§ط±ط© ط£ظ†ظٹظ‚ط© ظ„طھط¨ظˆظٹط¨ "ط§ظ„ظ…ط«ط¨طھط© ط¨ظ‡ط§طھظپظٹ" طھط¹ط±ط¶ ط¹ط¯ط¯ ط§ظ„طھط·ط¨ظٹظ‚ط§طھ ط§ظ„ظ…ط«ط¨طھط©
        viewModel.installedUiStateList.observe(this) { installedStates ->
            if (installedStates != null) {
                val installedCount = installedStates.size
                val installedTab = binding.tabLayoutMain.getTabAt(2)
                val badge = installedTab?.orCreateBadge

                if (installedCount > 0) {
                    badge?.isVisible = true
                    badge?.number = installedCount
                    badge?.backgroundColor = MaterialColors.getColor(binding.root, androidx.appcompat.R.attr.colorPrimary)
                } else {
                    badge?.isVisible = false
                }
            }
        }

        viewModel.errorMessage.observe(this) { errorMsg ->
            if (!errorMsg.isNullOrBlank()) {
                isDataLoadedSuccessfully = false
                showSmartRepoDiagnosticDialog()
            }
        }
    }

    private fun refreshData() {
        if (!isInternetAvailable()) {
            isDataLoadedSuccessfully = false
            showSmartRepoDiagnosticDialog()
            return
        }
        try {
            val secureUrl = getSecureRepoUrl()
            val urlBytes = secureUrl.toByteArray(Charsets.UTF_8)
            viewModel.loadApps(urlBytes, 0.toByte())
        } catch (e: Exception) {
            isDataLoadedSuccessfully = false
            showSmartRepoDiagnosticDialog()
        }
    }

    private fun showSettingsAndStorageDialog() {
        if (isFinishing || isDestroyed) return

        val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val apkFiles = downloadsDir?.listFiles { file ->
            val name = file.name.lowercase()
            file.isFile && (name.endsWith(".apk") || name.endsWith(".apkm") || name.endsWith(".xapk") || name.endsWith(".apks") || name.endsWith(".tmp"))
        } ?: emptyArray()

        val totalSizeBytes = apkFiles.sumOf { it.length() }
        val totalSizeMb = String.format(Locale.US, "%.1f MB", totalSizeBytes / (1024.0 * 1024.0))

        val message = "ًں“¦ ط¹ط¯ط¯ ظ…ظ„ظپط§طھ ط§ظ„طھط«ط¨ظٹطھ ط§ظ„ظ…ط­ظپظˆط¸ط©: ${apkFiles.size} ظ…ظ„ظپ\n" +
                "ًں’¾ ط§ظ„ظ…ط³ط§ط­ط© ط§ظ„ظ…ط³طھظ‡ظ„ظƒط©: $totalSizeMb\n\n" +
                "ظٹظ…ظƒظ†ظƒ ظ…ط³ط­ ظ…ظ„ظپط§طھ ط§ظ„طھط«ط¨ظٹطھ ط§ظ„ظ…ط¤ظ‚طھط© ط¨ط¹ط¯ طھط«ط¨ظٹطھ طھط·ط¨ظٹظ‚ط§طھظƒ ظ„طھظˆظپظٹط± ظ…ط³ط§ط­ط© طھط®ط²ظٹظ† ط¥ط¶ط§ظپظٹط© ظپظٹ ظ‡ط§طھظپظƒ."

        MaterialAlertDialogBuilder(this, R.style.Theme_FixEngine_Dialog)
            .setTitle("ط¥ط¹ط¯ط§ط¯ط§طھ ط§ظ„ظ…طھط¬ط± ظˆط§ظ„طھط®ط²ظٹظ† âڑ™ï¸ڈ")
            .setMessage(message)
            .setPositiveButton("طھظ†ط¸ظٹظپ ط§ظ„ظ…ط³ط§ط­ط© ($totalSizeMb)") { dialog, _ ->
                var deletedCount = 0
                apkFiles.forEach { if (it.delete()) deletedCount++ }
                viewModel.refreshAppStates()
                Toast.makeText(this, "طھظ… طھظ†ط¸ظٹظپ $deletedCount ظ…ظ„ظپ ظˆطھظپط±ظٹط؛ ط§ظ„ظ…ط³ط§ط­ط© ط¨ظ†ط¬ط§ط­! âœ¨", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("ط¥ط؛ظ„ط§ظ‚", null)
            .show()
    }

    private fun observeStoreAnnouncement() {
        try {
            val dbUrl = "https://abdullah-store-a95ed-default-rtdb.europe-west1.firebasedatabase.app"
            FirebaseDatabase.getInstance(dbUrl)
                .getReference("store_announcement")
                .addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val isActive = snapshot.child("active").getValue(Boolean::class.java) ?: false
                        val title = snapshot.child("title").getValue(String::class.java) ?: "ًں“¢ ط¥ط´ط¹ط§ط± ظ‡ط§ظ…"
                        val body = snapshot.child("body").getValue(String::class.java) ?: ""

                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            if (isActive && body.isNotBlank()) {
                                binding.txtAnnouncementTitle.text = title
                                binding.txtAnnouncementBody.text = body
                                binding.cardAnnouncement.isVisible = true
                                binding.btnDismissAnnouncement.setOnClickListener {
                                    binding.cardAnnouncement.isVisible = false
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
