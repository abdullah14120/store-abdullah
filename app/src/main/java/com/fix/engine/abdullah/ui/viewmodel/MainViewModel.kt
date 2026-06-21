package com.fix.engine.abdullah.ui.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fix.engine.abdullah.data.model.AppInstallStatus
import com.fix.engine.abdullah.data.model.AppItemUiState
import com.fix.engine.abdullah.data.model.AppModel
import com.fix.engine.abdullah.data.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Developed by: Abdullah Al-Tamimi
 * Architecture: Hybrid State Management (JSON + Firebase Realtime DB)
 * Refactored: Resolved Type Inference Compiler Bug & Integrated Dynamic Download Counters.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository()
    private val packageManager: PackageManager = application.packageManager
    
    // حفظ النسخ الأصلية الخام لمنع إعادة التحميل من السيرفر عند كل بحث
    private var fullAppsList: List<AppModel> = emptyList()
    private var fullUpdatesList: List<AppModel> = emptyList()

    // 📊 خريطة لحفظ التنزيلات الحية: (مفتاح التطبيق -> عدد التنزيلات) من الفايربيس
    private var downloadsMap: Map<Int, Long> = emptyMap()

    // الاحتفاظ بنص البحث النشط لضمان عدم ضياع الفلترة عند تحديث الحالة
    private var currentSearchQuery: String = ""

    // 🟢 1. مسار مراقبة صفحة "كل التطبيقات" (يعتمد على واجهة الحالة الجاهزة)
    private val _appsUiStateList = MutableLiveData<List<AppItemUiState>>()
    val appsUiStateList: LiveData<List<AppItemUiState>> get() = _appsUiStateList

    // 🔵 2. مسار مراقبة صفحة "التحديثات" (يعتمد على واجهة الحالة الجاهزة)
    private val _updatesUiStateList = MutableLiveData<List<AppItemUiState>>()
    val updatesUiStateList: LiveData<List<AppItemUiState>> get() = _updatesUiStateList

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> get() = _errorMessage

    /**
     * 🔐 جلب التطبيقات من السيرفر، فرز التحديثات، وتوليد الحالات الأولية
     */
    fun loadApps(encryptedUrl: ByteArray, cryptoSalt: Byte) {
        if (fullAppsList.isNotEmpty()) {
            refreshAppStates() // تحديث الحالات من الذاكرة المحلية إذا كانت محملة مسبقاً
            return
        }

        viewModelScope.launch(Dispatchers.IO) { 
            _isLoading.postValue(true)
            _errorMessage.postValue(null)
            
            val result = repository.fetchApps(encryptedUrl, cryptoSalt)
            
            result.onSuccess { list ->
                fullAppsList = list
                
                // 🚀 تشغيل مراقب الفايربيس الحي للعدادات
                viewModelScope.launch(Dispatchers.IO) {
                    repository.observeDownloads().collect { newMap ->
                        downloadsMap = newMap
                        refreshAppStates() // تحديث الشاشة فوراً عند تغير أرقام التنزيلات
                    }
                }
                
                // 🔄 التصفية الذكية المعتمدة على النصوص المخفية (Meta-Data Matching)
                fullUpdatesList = list.filter { app ->
                    try {
                        val flags = PackageManager.GET_META_DATA
                        
                        val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            packageManager.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
                        } else {
                            @Suppress("DEPRECATION")
                            packageManager.getPackageInfo(app.packageName, flags)
                        }
                        
                        val installedVer = pInfo.versionName ?: ""
                        val hasNewVersion = app.versionName.trim() != installedVer.trim()
                        
                        if (hasNewVersion) {
                            // 🛡️ المطابقة الذكية للنص (Manifest Tag Matching) لضمان مصدر التحديث
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

                // بعد الفرز، نقوم بتوليد الحالات المرئية وإرسالها للواجهة
                _appsUiStateList.postValue(mapToUiState(fullAppsList))
                _updatesUiStateList.postValue(mapToUiState(fullUpdatesList))
                _isLoading.postValue(false)
                
            }.onFailure { exception ->
                _errorMessage.postValue("فشل في جلب البيانات: ${exception.localizedMessage ?: "خطأ شبكي مجهول"}")
                _isLoading.postValue(false)
            }
        }
    }

    /**
     * دالة البحث المباشر الذكية والمزدوجة 
     */
    fun filterApps(query: String) {
        val cleanQuery = query.trim()
        currentSearchQuery = cleanQuery // حفظ النص الحالي
        
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

            // تحويل النتائج المفلترة إلى حالات UI
            _appsUiStateList.postValue(mapToUiState(filteredApps))
            _updatesUiStateList.postValue(mapToUiState(filteredUpdates))
        }
    }

    /**
     * 🚀 المحرك الأساسي: تحديث حالات الأزرار (تنزيل، تحديث، فتح) في الواجهة بسلاسة
     * تُستدعى تلقائياً من الـ Fragments في onResume
     */
    fun refreshAppStates() {
        if (fullAppsList.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            if (currentSearchQuery.isNotBlank()) {
                // إذا كان المستخدم يبحث حالياً، نحدث فقط العناصر الظاهرة في البحث
                val filteredApps = fullAppsList.filter { it.name?.contains(currentSearchQuery, true) == true || it.developer?.contains(currentSearchQuery, true) == true }
                val filteredUpdates = fullUpdatesList.filter { it.name?.contains(currentSearchQuery, true) == true || it.developer?.contains(currentSearchQuery, true) == true }
                
                _appsUiStateList.postValue(mapToUiState(filteredApps))
                _updatesUiStateList.postValue(mapToUiState(filteredUpdates))
            } else {
                // تحديث القوائم الكاملة
                _appsUiStateList.postValue(mapToUiState(fullAppsList))
                _updatesUiStateList.postValue(mapToUiState(fullUpdatesList))
            }
        }
    }

    /**
     * حساب دقيق وسريع لعدد التحديثات المتاحة
     */
    fun getUpdatesCount(): Int {
        return fullUpdatesList.size
    }

    /**
     * 🚀 استدعاء هذه الدالة عند ضغط المستخدم على زر التنزيل لزيادة العداد في الفايربيس
     */
    fun onAppDownloadStarted(appId: Int) {
        repository.incrementDownloadCount(appId)
    }

    /**
     * 🧠 معالج الحالات المرئية (State Mapper): 
     * يقوم بتحويل الموديل الخام إلى نموذج UI جاهز للعرض مباشرة في الـ Adapter
     */
    private fun mapToUiState(apps: List<AppModel>): List<AppItemUiState> {
        val pm = packageManager
        val downloadsDir = getApplication<Application>().getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)

        return apps.map { app ->
            val fileName = app.getUniqueFileName()
            val localFile = File(downloadsDir, fileName)
            val isDownloaded = localFile.exists()

            var installedVerName = ""
            var isInstalled = false

            // 🛡️ إصلاح التباس استنتاج النوع (Type Inference Fix)
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
                // التطبيق غير مثبت، تترك القيم الافتراضية
            }

            // تحديد الحالة النهائية التي ستبنى عليها ألوان ونصوص الواجهة
            val status = when {
                isDownloaded -> AppInstallStatus.DOWNLOADED
                isInstalled && app.versionName.trim() != installedVerName.trim() -> AppInstallStatus.UPDATE_AVAILABLE
                isInstalled -> AppInstallStatus.INSTALLED
                else -> AppInstallStatus.NOT_INSTALLED
            }

            // 📊 جلب رقم العداد الخاص بهذا التطبيق من الذاكرة الحية (أو 0 إذا لم يوجد)
            val currentDownloads = downloadsMap[app.id] ?: 0L

            AppItemUiState(app, status, installedVerName, currentDownloads)
        }
    }
}
