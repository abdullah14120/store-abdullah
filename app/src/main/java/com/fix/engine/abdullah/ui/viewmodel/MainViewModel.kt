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
 * Architecture: Hybrid State Management with Smart Version Comparison & Validated APK Check
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository()
    private val packageManager: PackageManager = application.packageManager

    private var fullAppsList: List<AppModel> = emptyList()
    private var fullUpdatesList: List<AppModel> = emptyList()
    private var downloadsMap: Map<Int, Long> = emptyMap()
    private var currentSearchQuery: String = ""

    private val _appsUiStateList = MutableLiveData<List<AppItemUiState>>()
    val appsUiStateList: LiveData<List<AppItemUiState>> get() = _appsUiStateList

    private val _updatesUiStateList = MutableLiveData<List<AppItemUiState>>()
    val updatesUiStateList: LiveData<List<AppItemUiState>> get() = _updatesUiStateList

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

                viewModelScope.launch(Dispatchers.IO) {
                    repository.observeDownloads().collect { newMap ->
                        downloadsMap = newMap
                        refreshAppStates()
                    }
                }

                fullUpdatesList = list.filter { app ->
                    try {
                        val flags = PackageManager.GET_META_DATA

                        val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            packageManager.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
                        } else {
                            @Suppress("DEPRECATION")
                            packageManager.getPackageInfo(app.packageName, flags)
                        }

                        val installedVerName = pInfo.versionName ?: ""
                        val isServerNewer = compareVersionNames(app.versionName, installedVerName) > 0

                        if (isServerNewer) {
                            val serverTag = app.manifestTag
                            if (!serverTag.isNullOrBlank()) {
                                val bundle = pInfo.applicationInfo?.metaData
                                val installedTag = bundle?.getString("ABDULLAH_STORE_TAG")
                                if (installedTag == null || installedTag.trim() != serverTag.trim()) {
                                    return@filter false
                                }
                            }
                            return@filter true
                        } else {
                            return@filter false
                        }
                    } catch (e: PackageManager.NameNotFoundException) {
                        false
                    } catch (e: Exception) {
                        false
                    }
                }

                _appsUiStateList.postValue(mapToUiState(fullAppsList))
                _updatesUiStateList.postValue(mapToUiState(fullUpdatesList))
                _isLoading.postValue(false)

            }.onFailure { exception ->
                _errorMessage.postValue("فشل في جلب البيانات: ${exception.localizedMessage ?: "خطأ شبكي مجهول"}")
                _isLoading.postValue(false)
            }
        }
    }

    fun filterApps(query: String) {
        val cleanQuery = query.trim()
        currentSearchQuery = cleanQuery

        if (cleanQuery.isBlank()) {
            refreshAppStates()
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            val filteredApps = fullAppsList.filter { app ->
                val nameMatch = app.name?.contains(cleanQuery, ignoreCase = true) ?: false
                val devMatch = app.developer?.contains(cleanQuery, ignoreCase = true) ?: false
                nameMatch || devMatch
            }

            val filteredUpdates = fullUpdatesList.filter { app ->
                val nameMatch = app.name?.contains(cleanQuery, ignoreCase = true) ?: false
                val devMatch = app.developer?.contains(cleanQuery, ignoreCase = true) ?: false
                nameMatch || devMatch
            }

            _appsUiStateList.postValue(mapToUiState(filteredApps))
            _updatesUiStateList.postValue(mapToUiState(filteredUpdates))
        }
    }

    fun refreshAppStates() {
        if (fullAppsList.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            if (currentSearchQuery.isNotBlank()) {
                val filteredApps = fullAppsList.filter { it.name?.contains(currentSearchQuery, true) == true || it.developer?.contains(currentSearchQuery, true) == true }
                val filteredUpdates = fullUpdatesList.filter { it.name?.contains(currentSearchQuery, true) == true || it.developer?.contains(currentSearchQuery, true) == true }

                _appsUiStateList.postValue(mapToUiState(filteredApps))
                _updatesUiStateList.postValue(mapToUiState(filteredUpdates))
            } else {
                _appsUiStateList.postValue(mapToUiState(fullAppsList))
                _updatesUiStateList.postValue(mapToUiState(fullUpdatesList))
            }
        }
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
            
            // 🛡️ لا يعتبر الملف جاهزاً للتثبيت إلا إذا اكتمل تحميله 100% وكان حزمة APK صالحة
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
            } catch (e: PackageManager.NameNotFoundException) {
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
            val allStates = mapToUiState(fullAppsList)
            val installedOnly = allStates.filter {
                it.status == AppInstallStatus.INSTALLED || it.status == AppInstallStatus.UPDATE_AVAILABLE
            }
            _appsUiStateList.postValue(installedOnly)
        }
}
