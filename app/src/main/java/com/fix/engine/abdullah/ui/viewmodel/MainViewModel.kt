package com.fix.engine.abdullah.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fix.engine.abdullah.data.model.AppModel
import com.fix.engine.abdullah.data.repository.AppRepository
import kotlinx.coroutines.launch

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Pro View Model
 * Logic: Handles Live Search and Multi-Fragment Data Distribution
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository()
    
    // القائمة الأصلية القادمة من السيرفر
    private var fullList: List<AppModel> = emptyList()

    // القائمة التي تراقبها الواجهات (Fragments)
    private val _appsList = MutableLiveData<List<AppModel>>()
    val appsList: LiveData<List<AppModel>> get() = _appsList

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> get() = _errorMessage

    /**
     * جلب التطبيقات وتحديث القائمة الرئيسية
     */
    fun loadApps(repoUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            val result = repository.fetchApps(repoUrl)
            
            result.onSuccess { list ->
                fullList = list // حفظ النسخة الأصلية للبحث
                _appsList.value = list
                _isLoading.value = false
            }.onFailure { exception ->
                _errorMessage.value = "فشل في جلب البيانات: ${exception.localizedMessage}"
                _isLoading.value = false
            }
        }
    }

    /**
     * دالة البحث المباشر: تقوم بتصفية القائمة بناءً على الاسم أو المطور
     * ويتم تحديث الـ LiveData تلقائياً لتراها الـ Fragments
     */
    fun filterApps(query: String) {
        if (query.isEmpty()) {
            _appsList.value = fullList
        } else {
            val filtered = fullList.filter { 
                it.name.contains(query, ignoreCase = true) || 
                it.developer.contains(query, ignoreCase = true)
            }
            _appsList.value = filtered
        }
    }

    /**
     * حساب عدد التحديثات المتاحة (يستخدم للـ Badge في MainActivity)
     */
    fun getUpdatesCount(packageManager: android.content.pm.PackageManager): Int {
        return fullList.count { app ->
            try {
                val installedVer = packageManager.getPackageInfo(app.packageName, 0).versionName
                // المقارنة النصية بناءً على طلبك لتجاوز الـ VersionCode
                app.versionName.trim() != installedVer?.trim()
            } catch (e: Exception) {
                false
            }
        }
    }
}
