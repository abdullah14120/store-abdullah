package com.fix.engine.abdullah.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.fix.engine.abdullah.data.model.AppModel
import com.fix.engine.abdullah.data.repository.AppRepository
import kotlinx.coroutines.launch

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: Abdullah Store - FIX ENGINE Edition
 * Logic: Handles App Data flow and Background Work Status
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository()
    private val workManager = WorkManager.getInstance(application)

    private val _appsList = MutableLiveData<List<AppModel>>()
    val appsList: LiveData<List<AppModel>> get() = _appsList

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> get() = _errorMessage

    /**
     * جلب التطبيقات وتحديث الواجهة
     */
    fun loadApps(repoUrl: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            
            val result = repository.fetchApps(repoUrl)
            
            result.onSuccess { list ->
                _appsList.value = list
                _isLoading.value = false
            }.onFailure { exception ->
                _errorMessage.value = "فشل في جلب البيانات: ${exception.localizedMessage}"
                _isLoading.value = false
            }
        }
    }

    /**
     * دالة لمراقبة حالة تحميل تطبيق معين عبر الـ Package Name
     * تستخدم لاسترجاع الحالة عند إغلاق التطبيق والعودة إليه
     */
    fun getDownloadStatus(packageName: String): LiveData<List<WorkInfo>> {
        return workManager.getWorkInfosByTagLiveData(packageName)
    }
}
