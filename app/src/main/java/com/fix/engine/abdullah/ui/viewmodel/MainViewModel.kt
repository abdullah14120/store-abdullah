package com.fix.engine.abdullah.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fix.engine.abdullah.data.model.AppModel
import com.fix.engine.abdullah.data.repository.AppRepository
import kotlinx.coroutines.launch

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE
 * Logic: Handles App Data flow between Repository and UI
 */
class MainViewModel : ViewModel() {

    private val repository = AppRepository()

    // متغير داخلي قابل للتعديل لتخزين قائمة التطبيقات
    private val _appsList = MutableLiveData<List<AppModel>>()
    // متغير خارجي للقراءة فقط لضمان أمان البيانات (Encapsulation)
    val appsList: LiveData<List<AppModel>> get() = _appsList

    // متغير لمتابعة حالة التحميل (Loading State)
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    // متغير لتخزين رسائل الخطأ
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> get() = _errorMessage

    /**
     * وظيفة جلب التطبيقات من المستودع
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
}
