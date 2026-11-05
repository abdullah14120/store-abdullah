package com.fix.engine.abdullah.ui.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
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

/**
 * Developed by: Abdullah Al-Tamimi
 * Architecture: 3-Tab Hybrid State Management (All Apps, Updates & Installed Apps) with Smart Version & Tag Check
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository()
    private val packageManager: PackageManager = application.packageManager

    private var fullAppsList: List<AppModel> = emptyList()
    private var fullUpdatesList: List<AppModel> = emptyList()
    private var fullInstalledList: List<AppModel> = emptyList()
    private var downloadsMap: Map<Int, Long> = emptyMap()
    private var currentSearchQuery: String = ""
    private var isObservingDownloads: Boolean = false

    private val _appsUiStateList = MutableLiveData<List<AppItemUiState>>()
    val appsUiStateList: LiveData<List<AppItemUiState>> get() = _appsUiStateList

    private val _updatesUiStateList = MutableLiveData<List<AppItemUiState>>()
    val updatesUiStateList: LiveData<List<AppItemUiState>> get() = _updatesUiStateList

    private val _installedUiStateList = MutableLiveData<List<AppItemUiState>>()
    val installedUiStateList: LiveData<List<AppItemUiState>> get() = _installedUiStateList

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> get() = _errorMessage

    fun loadApps(encryptedUrl: ByteArray, cryptoSalt: Byte) {
        if (fullAppsList.isNotEmpty()) {
            refreshAppStates()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.postValue(true)
            _errorMessage.postValue(null)

            val result = repository.fetchApps(encryptedUrl, cryptoSalt)

            result.onSuccess { list ->
                fullAppsList = list

                // تشغيل مراقب الفايربيس مرة واحدة فقط لمنع تكرار المراقبين عند التحديث اليدوي
                if (!isObservingDownloads) {
                    isObservingDownloads = true
                    viewModelScope.launch(Dispatchers.IO) {
                        repository.observeDownloads().collect { newMap ->
                            downloadsMap = newMap
                            recalculateAndPublishStates()
                        }
                    }
                }

                recalculateAndPublishStates()
                _isLoading.postValue(false)

            }.onFailure { exception ->
                _errorMessage.postValue("فشل في جلب البيانات: ${exception.localizedMessage ?: "خطأ شبكي مجهول"}")
                _isLoading.postValue(false)
            }
        }
    }

    fun filterApps(query: String) {
        currentSearchQuery = query.trim()
        viewModelScope.launch(Dispatchers.IO) {
            recalculateAndPublishStates()
        }
    }

    fun refreshAppStates() {
        if (fullAppsList.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            recalculateAndPublishStates()
        }
    }

    /**
     * 🧠 إعادة فحص الحزم المثبتة والتحديثات المتاحة بشكل حي ودقيق لجميع التبويبات الثلاثة
     */
    private fun recalculateAndPublishStates() {
        if (fullAppsList.isEmpty()) return

        val updates = mutableListOf<AppModel>()
        val installed = mutableListOf<AppModel>()

        for (app in fullAppsList) {
            try {
                val flags = PackageManager.GET_META_DATA
                val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(app.packageName, flags)
                }

                // التحقق من تطابق وسم المتجر (ABDULLAH_STORE_TAG) إن وجد للتمييز بين النسخ التي تحمل نفس الحزمة
                val serverTag = app.manifestTag
                var isTagMatched = true
                if (!serverTag.isNullOrBlank()) {
                    val bundle = pInfo.applicationInfo?.metaData
                    val installedTag = bundle?.getString("ABDULLAH_STORE_TAG")
                    if (installedTag == null || installedTag.trim() != serverTag.trim()) {
                        isTagMatched = false
                    }
                }

                if (isTagMatched) {
                    installed.add(app)
                    val installedVerName = pInfo.versionName ?: ""
                    if (compareVersionNames(app.versionName, installedVerName) > 0) {
                        updates.add(app)
                    }
                }
            } catch (_: PackageManager.NameNotFoundException) {
                // التطبيق غير مثبت على الجهاز
            } catch (_: Exception) {
            }
        }

        fullUpdatesList = updates
        fullInstalledList = installed

        val cleanQuery = currentSearchQuery
        val matchesQuery: (AppModel) -> Boolean = { app ->
            if (cleanQuery.isBlank()) {
                true
            } else {
                val nameMatch = app.name?.contains(cleanQuery, ignoreCase = true) ?: false
                val devMatch = app.developer?.contains(cleanQuery, ignoreCase = true) ?: false
                val pkgMatch = app.packageName.contains(cleanQuery, ignoreCase = true)
                nameMatch || devMatch || pkgMatch
            }
        }

        val filteredAll = fullAppsList.filter(matchesQuery)
        val filteredUpdates = fullUpdatesList.filter(matchesQuery)
        val filteredInstalled = fullInstalledList.filter(matchesQuery)

        _appsUiStateList.postValue(mapToUiState(filteredAll))
        _updatesUiStateList.postValue(mapToUiState(filteredUpdates))
        _installedUiStateList.postValue(mapToUiState(filteredInstalled))
    }

    fun getUpdatesCount(): Int {
        return fullUpdatesList.size
    }

    fun onAppDownloadStarted(appId: Int) {
        repository.incrementDownloadCount(appId)
    }

    private fun mapToUiState(apps: List<AppModel>): List<AppItemUiState> {
        val appContext = getApplication<Application>()
        val pm = packageManager
        val downloadsDir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)

        return apps.map { app ->
            val fileName = app.getUniqueFileName()
            val localFile = File(downloadsDir, fileName)

            val isDownloaded = AbdullahStoreApp.isApkCompleteAndValid(appContext, localFile)

            var installedVerName = ""
            var isInstalled = false

            try {
                val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(app.packageName, 0)
                }
                installedVerName = pInfo.versionName ?: ""
                isInstalled = true
            } catch (_: PackageManager.NameNotFoundException) {
                // غير مثبت
            }

            val comparisonResult = if (isInstalled) compareVersionNames(app.versionName, installedVerName) else 0

            val status = when {
                isDownloaded -> AppInstallStatus.DOWNLOADED
                isInstalled && comparisonResult > 0 -> AppInstallStatus.UPDATE_AVAILABLE
                isInstalled -> AppInstallStatus.INSTALLED
                else -> AppInstallStatus.NOT_INSTALLED
            }

            val currentDownloads = downloadsMap[app.id] ?: 0L
            AppItemUiState(app, status, installedVerName, currentDownloads)
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
            return if (serverVersion != installedVersion) 1 else 0
        }
        return 0
    }

    fun forceReloadApps(encryptedUrl: ByteArray, cryptoSalt: Byte) {
        fullAppsList = emptyList()
        loadApps(encryptedUrl, cryptoSalt)
    }

    fun filterInstalledOnly() {
        viewModelScope.launch(Dispatchers.IO) {
            recalculateAndPublishStates()
        }
    }
}
