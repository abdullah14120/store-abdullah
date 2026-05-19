package com.fix.engine.abdullah.ui.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fix.engine.abdullah.data.model.AppModel
import com.fix.engine.abdullah.data.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Developed by: Abdullah Al-Tamimi
 * Project: FIX ENGINE - Pro View Model (Material 3 Secure Edition)
 * Logic: Handles Independent Multi-Fragment Search & Secure Encrypted Data Distribution
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository()
    private val packageManager: PackageManager = application.packageManager
    
    // حفظ النسخ الأصلية المستقرة القادمة من السيرفر لمنع تداخل الواجهات
    private var fullAppsList: List<AppModel> = emptyList()
    private var fullUpdatesList: List<AppModel> = emptyList()

    // 🟢 1. مسار مراقبة صفحة "كل التطبيقات"
    private val _appsList = MutableLiveData<List<AppModel>>()
    val appsList: LiveData<List<AppModel>> get() = _appsList

    // 🔵 2. مسار مراقبة صفحة "التحديثات"
    private val _updatesList = MutableLiveData<List<AppModel>>()
    val updatesList: LiveData<List<AppModel>> get() = _updatesList

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> get() = _errorMessage

    /**
     * 🔐 جلب التطبيقات وفرزها في الخلفية بتوزيع متوازٍ
     * تم تحديث البارامترات لتستقبل الحزمة المشفرة بدلاً من روابط النصوص الصافية
     */
    fun loadApps(encryptedUrl: ByteArray, cryptoSalt: Byte) {
        viewModelScope.launch(Dispatchers.IO) { // العمليات الثقيلة وفك التشفير تتم داخل الـ Thread المعزول لحماية الذاكرة
            _isLoading.postValue(true)
            _errorMessage.postValue(null)
            
            // تمرير مصفوفة البايتات والمفتاح السري مباشرة للـ Repository لفكها لحظياً هناك
            val result = repository.fetchApps(encryptedUrl, cryptoSalt)
            
            result.onSuccess { list ->
                fullAppsList = list
                
                // حساب وتصفية التطبيقات التي تمتلك تحديثات برمجية فوراً وتخزينها كنسخة مستقلة
                fullUpdatesList = list.filter { app ->
                    try {
                        val pInfo = packageManager.getPackageInfo(app.packageName, 0)
                        val installedVer = pInfo.versionName ?: ""
                        app.versionName.trim() != installedVer.trim()
                    } catch (e: PackageManager.NameNotFoundException) {
                        false
                    }
                }

                // ضخ البيانات لكل واجهة بشكل آمن وفي نفس اللحظة عبر postValue الآمنة للخلفية
                _appsList.postValue(fullAppsList)
                _updatesList.postValue(fullUpdatesList)
                _isLoading.postValue(false)
                
            }.onFailure { exception ->
                _errorMessage.postValue("فشل في جلب البيانات: ${exception.localizedMessage}")
                _isLoading.postValue(false)
            }
        }
    }

    /**
     * دالة البحث المباشر الذكية والمزدوجة: تقوم بتصفية تبويب التطبيقات 
     * وتبويب التحديثات معاً دون أن يختفي محتوى أي واجهة منهما!
     */
    fun filterApps(query: String) {
        if (query.isBlank()) {
            _appsList.value = fullAppsList
            _updatesList.value = fullUpdatesList
        } else {
            // تصفية ذكية لصفحة التطبيقات بناءً على الاسم أو المطور
            val filteredApps = fullAppsList.filter { 
                it.name.contains(query, ignoreCase = true) || 
                it.developer.contains(query, ignoreCase = true)
            }
            
            // تصفية موازية ومستقلة لشاشة التحديثات المتاحة لتظل النتائج متناسقة
            val filteredUpdates = fullUpdatesList.filter { 
                it.name.contains(query, ignoreCase = true) || 
                it.developer.contains(query, ignoreCase = true)
            }

            _appsList.value = filteredApps
            _updatesList.value = filteredUpdates
        }
    }

    /**
     * حساب دقيق وسريع لعدد التحديثات المتاحة دون استهلاك موارد المعالج
     */
    fun getUpdatesCount(): Int {
        return fullUpdatesList.size
    }
}
