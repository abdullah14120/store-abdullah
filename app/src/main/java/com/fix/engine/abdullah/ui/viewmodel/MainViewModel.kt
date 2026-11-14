package com.fix.engine.abdullah.ui.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fix.engine.abdullah.AbdullahStoreApp
import com.fix.engine.abdullah.data.model.AppInstallStatus
import com.fix.engine.abdullah.data.model.AppItemUiState
import com.fix.engine.abdullah.data.model.AppModel
import com.fix.engine.abdullah.data.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Developed by: Abdullah Al-Tamimi
 * Architecture: High-Speed Single-Pass State Engine with Memoized APK Check & Instant Memory Cache
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        // ⚡ كاش فوري في الذاكرة لتسريع فتح المتجر وتبديل المظهر النهاري/الليلي في 0.01 ثانية
        @Volatile
        private var cachedAppsList: List<AppModel> = emptyList()

        @Volatile
        private var cachedDownloadsMap: Map<Int, Long> = emptyMap()

        private val apkValidationCache = ConcurrentHashMap<String, Boolean>()
    }

    private val repository = AppRepository()
    private val packageManager: PackageManager = application.packageManager

    private var fullAppsList: List<AppModel> = cachedAppsList
    private var downloadsMap: Map<Int, Long> = cachedDownloadsMap
    private var currentSearchQuery: String = ""
    private var currentSortMode: Int = 0
    private var isObservingDownloads: Boolean = false
    private var lastRefreshTimeMs: Long = 0L

    private val _appsUiStateList = MutableLiveData<List<AppItemUiState>>()
    val appsUiStateList: LiveData<List<AppItemUiState>> get() = _appsUiStateList

    private val _updatesUiStateList = MutableLiveData<List<AppItemUiState>>()
    val updatesUiStateList: LiveData<List<AppItemUiState>> get() = _updatesUiStateList

    private val _installedUiStateList = MutableLiveData<List<AppItemUiState>>()
    val installedUiStateList: LiveData<List<AppItemUiState>> get() = _installedUiStateList

    private val _activeSortMode = MutableLiveData<Int>(0)
    val activeSortMode: LiveData<Int> get() = _activeSortMode

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> get() = _errorMessage

    fun loadApps(encryptedUrl: ByteArray, cryptoSalt: Byte) {
        // إذا كانت البيانات محفوظة في الكاش السريع، نعرضها فوراً بدون انتظار الشبكة
        if (fullAppsList.isNotEmpty()) {
            _isLoading.postValue(false)
            refreshAppStates(force = true)
            startDownloadsObserverIfNeeded()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            _errorMessage.postValue(null)

            val result = repository.fetchApps(encryptedUrl, cryptoSalt)

            result.onSuccess { list ->
                fullAppsList = list
                cachedAppsList = list

                // نشر القوائم الثلاث فوراً في تمريرة واحدة فائقة السرعة
                publishSinglePassStates(fullAppsList)
                _isLoading.postValue(false)

                // تشغيل مراقب عدادات التحميل في الخلفية بعد ظهور القائمة للمستخدم
                startDownloadsObserverIfNeeded()

            }.onFailure { exception ->
                _errorMessage.postValue("فشل في جلب البيانات: ${exception.localizedMessage ?: "خطأ شبكي مجهول"}")
                _isLoading.postValue(false)
            }
        }
    }

    private fun startDownloadsObserverIfNeeded() {
        if (!isObservingDownloads) {
            isObservingDownloads = true
            viewModelScope.launch(Dispatchers.IO) {
                repository.observeDownloads().collect { newMap ->
                    if (newMap != downloadsMap) {
                        downloadsMap = newMap
                        cachedDownloadsMap = newMap
                        updateDownloadsCountInMemory(newMap)
                    }
                }
            }
        }
    }

    /**
     * ⚡ تحديث عدادات التحميل في الذاكرة مباشرة دون إعادة فحص PackageManager أو ملفات الـ APK
     */
    private fun updateDownloadsCountInMemory(newMap: Map<Int, Long>) {
        val currentAll = _appsUiStateList.value
        if (!currentAll.isNullOrEmpty()) {
            _appsUiStateList.postValue(currentAll.map { it.copy(downloadsCount = newMap[it.app.id] ?: 0L) })
        }
        val currentUpdates = _updatesUiStateList.value
        if (!currentUpdates.isNullOrEmpty()) {
            _updatesUiStateList.postValue(currentUpdates.map { it.copy(downloadsCount = newMap[it.app.id] ?: 0L) })
        }
        val currentInstalled = _installedUiStateList.value
        if (!currentInstalled.isNullOrEmpty()) {
            _installedUiStateList.postValue(currentInstalled.map { it.copy(downloadsCount = newMap[it.app.id] ?: 0L) })
        }
    }

    fun filterApps(query: String) {
        val cleanQuery = query.trim()
        if (currentSearchQuery == cleanQuery && _appsUiStateList.value != null) return
        currentSearchQuery = cleanQuery

        if (fullAppsList.isEmpty()) return

        viewModelScope.launch(Dispatchers.Default) {
            publishSinglePassStates(fullAppsList)
        }
    }

    fun setSortMode(mode: Int) {
        if (currentSortMode == mode) return
        currentSortMode = mode
        _activeSortMode.postValue(mode)
        refreshAppStates(force = true)
    }

    fun getCurrentSortMode(): Int = currentSortMode

    fun refreshAppStates(force: Boolean = false) {
        if (fullAppsList.isEmpty()) return

        // منع التكرار المتزامن عند استدعاء onResume من عدة تبويبات في نفس اللحظة
        val now = SystemClock.elapsedRealtime()
        if (!force && (now - lastRefreshTimeMs) < 400L) return
        lastRefreshTimeMs = now

        viewModelScope.launch(Dispatchers.IO) {
            publishSinglePassStates(fullAppsList)
        }
    }

    /**
     * 🚀 محرك التمريرة الواحدة (Single-Pass Engine):
     * يفحص حالة التثبيت ووسم المتجر وملف الـ APK مرة واحدة فقط لكل تطبيق،
     * ويستخرج القوائم الثلاث (الكل، التحديثات، المثبتة) في أقل من جزء من الثانية.
     */
    private fun publishSinglePassStates(sourceApps: List<AppModel>) {
        val appContext = getApplication<Application>()
        val pm = packageManager
        val downloadsDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)

        val allMapped = ArrayList<AppItemUiState>(sourceApps.size)
        val updatesMapped = ArrayList<AppItemUiState>()
        val installedMapped = ArrayList<AppItemUiState>()

        val flags = PackageManager.GET_META_DATA

        for (app in sourceApps) {
            val fileName = app.getUniqueFileName()
            val localFile = File(downloadsDir, fileName)

            // ⚡ فحص سريع جداً للملف مع حفظ النتيجة في الذاكرة لتجنب قراءة الـ ZIP المتكررة
            val isDownloaded = isApkValidCached(appContext, localFile)

            var installedVerName = ""
            var isInstalled = false
            var isTagMatched = true

            try {
                val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(app.packageName, flags)
                }
                installedVerName = pInfo.versionName ?: ""
                isInstalled = true

                val serverTag = app.manifestTag
                if (!serverTag.isNullOrBlank()) {
                    val bundle = pInfo.applicationInfo?.metaData
                    val installedTag = bundle?.getString("ABDULLAH_STORE_TAG")
                    if (installedTag == null || installedTag.trim() != serverTag.trim()) {
                        isTagMatched = false
                    }
                }
            } catch (_: PackageManager.NameNotFoundException) {
                isInstalled = false
            } catch (_: Exception) {
                isInstalled = false
            }

            val hasNewerVersion = isInstalled && isTagMatched && compareVersionNames(app.versionName, installedVerName) > 0

            val status = when {
                isDownloaded -> AppInstallStatus.DOWNLOADED
                hasNewerVersion -> AppInstallStatus.UPDATE_AVAILABLE
                isInstalled && isTagMatched -> AppInstallStatus.INSTALLED
                else -> AppInstallStatus.NOT_INSTALLED
            }

            val currentDownloads = downloadsMap[app.id] ?: 0L
            val uiState = AppItemUiState(app, status, installedVerName, currentDownloads)

            allMapped.add(uiState)
            if (hasNewerVersion) {
                updatesMapped.add(uiState)
            }
            if (isInstalled && isTagMatched) {
                installedMapped.add(uiState)
            }
        }

        val cleanQuery = currentSearchQuery
        val filterPredicate: (AppItemUiState) -> Boolean = { item ->
            if (cleanQuery.isBlank()) {
                true
            } else {
                val nameMatch = item.app.name?.contains(cleanQuery, ignoreCase = true) ?: false
                val devMatch = item.app.developer?.contains(cleanQuery, ignoreCase = true) ?: false
                nameMatch || devMatch
            }
        }

        val finalAll = applySortByMode(allMapped.filter(filterPredicate))
        val finalUpdates = applySortByMode(updatesMapped.filter(filterPredicate))
        val finalInstalled = applySortByMode(installedMapped.filter(filterPredicate))

        _appsUiStateList.postValue(finalAll)
        _updatesUiStateList.postValue(finalUpdates)
        _installedUiStateList.postValue(finalInstalled)
    }

    private fun isApkValidCached(appContext: Application, localFile: File): Boolean {
        if (!localFile.exists() || localFile.length() <= 0L) {
            return false
        }
        val cacheKey = "${localFile.absolutePath}:${localFile.length()}:${localFile.lastModified()}"
        return apkValidationCache.getOrPut(cacheKey) {
            AbdullahStoreApp.isApkCompleteAndValid(appContext, localFile)
        }
    }

    private fun applySortByMode(items: List<AppItemUiState>): List<AppItemUiState> {
        return when (currentSortMode) {
            1 -> items.sortedByDescending { it.app.size.toLongOrNull() ?: 0L }
            2 -> items.sortedBy { it.app.name?.lowercase() ?: "" }
            3 -> items.sortedBy { it.app.size.toLongOrNull() ?: Long.MAX_VALUE }
            4 -> items.sortedByDescending { it.app.size.toLongOrNull() ?: 0L }
            else -> items
        }
    }

    fun getUpdatesCount(): Int {
        return _updatesUiStateList.value?.size ?: 0
    }

    fun onAppDownloadStarted(appId: Int) {
        repository.incrementDownloadCount(appId)
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
            return if (serverVersion != installedVersion) 1 else 0
        }
        return 0
    }

    fun forceReloadApps(encryptedUrl: ByteArray, cryptoSalt: Byte) {
        fullAppsList = emptyList()
        cachedAppsList = emptyList()
        apkValidationCache.clear()
        loadApps(encryptedUrl, cryptoSalt)
    }

    fun filterInstalledOnly() {
        refreshAppStates(force = true)
    }
}
