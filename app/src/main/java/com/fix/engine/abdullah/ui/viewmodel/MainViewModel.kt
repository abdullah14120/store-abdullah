Package com.fix.engine.abdullah.ui.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
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
 * Project: متجر Abdullah - Pro View Model (Material 3 Secure Edition)
 * Logic: Handles Independent Multi-Fragment Search, Secure Encrypted Data Distribution & Lifecycle Caching
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
     */
    fun loadApps(encryptedUrl: ByteArray, cryptoSalt: Byte) {
        // 🟢 التعديل الأول: التحقق من الكاش الداخلي لمنع إعادة التحميل عند تدوير الشاشة (Screen Rotation)
        if (fullAppsList.isNotEmpty()) {
            _appsList.value = fullAppsList
            _updatesList.value = fullUpdatesList
            return
        }

        // العمليات الثقيلة وفك التشفير تتم داخل الـ Thread المعزول لحماية الذاكرة والواجهة
        viewModelScope.launch(Dispatchers.IO) { 
            _isLoading.postValue(true)
            _errorMessage.postValue(null)
            
            // تمرير مصفوفة البايتات والمفتاح السري مباشرة للـ Repository لفكها لحظياً هناك
            val result = repository.fetchApps(encryptedUrl, cryptoSalt)
            
            result.onSuccess { list ->
                fullAppsList = list
                
                // حساب وتصفية التطبيقات التي تمتلك تحديثات برمجية فوراً وتخزينها كنسخة مستقلة
                fullUpdatesList = list.filter { app ->
                    try {
                        // 🟢 التعديل الثاني: التوافق التام مع أندرويد 13+ (API 33) للـ PackageManager
                        val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            packageManager.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(0))
                        } else {
                            @Suppress("DEPRECATION")
                            packageManager.getPackageInfo(app.packageName, 0)
                        }
                        
                        val installedVer = pInfo.versionName ?: ""
                        // عمل trim لتفادي الفراغات المخفية في النصوص القادمة من السيرفر
                        app.versionName.trim() != installedVer.trim()
                    } catch (e: PackageManager.NameNotFoundException) {
                        false // التطبيق غير مثبت، إذن لا يوجد له تحديث في التبويب الثاني
                    } catch (e: Exception) {
                        false
                    }
                }

                // ضخ البيانات لكل واجهة بشكل آمن وفي نفس اللحظة عبر postValue الآمنة للخلفية
                _appsList.postValue(fullAppsList)
                _updatesList.postValue(fullUpdatesList)
                _isLoading.postValue(false)
                
            }.onFailure { exception ->
                // استرجاع تفاصيل الخطأ الشبكي الحقيقي وعرضه في الواجهة مباشرة
                _errorMessage.postValue("فشل في جلب البيانات: ${exception.localizedMessage ?: "خطأ شبكي مجهول"}")
                _isLoading.postValue(false)
            }
        }
    }

    /**
     * دالة البحث المباشر الذكية والمزدوجة
     * 🛠️ تم تعديلها لتطابق الخصائص الحقيقية للسيرفر (name و developer) مع الحماية ضد الـ Null
     */
    fun filterApps(query: String) {
        // استخدام trim يمنع البحث الخاطئ إذا قام المستخدم بكتابة مسافة فارغة بالخطأ
        val cleanQuery = query.trim()
        
        if (cleanQuery.isBlank()) {
            _appsList.value = fullAppsList
            _updatesList.value = fullUpdatesList
        } else {
            // تصفية ذكية لصفحة التطبيقات بناءً على الاسم أو المطور الحالي
            val filteredApps = fullAppsList.filter { app ->
                val nameMatch = app.name?.contains(cleanQuery, ignoreCase = true) ?: false
                val devMatch = app.developer?.contains(cleanQuery, ignoreCase = true) ?: false
                nameMatch || devMatch
            }
            
            // تصفية موازية ومستقلة لشاشة التحديثات المتاحة لتظل النتائج متناسقة
            val filteredUpdates = fullUpdatesList.filter { app ->
                val nameMatch = app.name?.contains(cleanQuery, ignoreCase = true) ?: false
                val devMatch = app.developer?.contains(cleanQuery, ignoreCase = true) ?: false
                nameMatch || devMatch
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
