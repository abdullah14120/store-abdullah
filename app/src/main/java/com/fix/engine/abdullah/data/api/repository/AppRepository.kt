package com.fix.engine.abdullah.data.repository

import com.fix.engine.abdullah.data.api.RetrofitClient
import com.fix.engine.abdullah.data.model.AppModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Developed by: Abdullah Al-Tamimi
 * Architecture: Professional Data Repository (Secure Data Hub)
 * Logic: Bridge between Cryptographic API Streams and ViewModels
 */
class AppRepository {

    private val apiService = RetrofitClient.instance

    /**
     * جلب قائمة التطبيقات من مستودع الـ Server الآمن.
     * يستقبل المصفوفة المفكوكة أو المشفرة ويتعامل معها ديناميكياً في الخلفية.
     */
    suspend fun fetchApps(encryptedUrl: ByteArray, cryptoSalt: Byte): Result<List<AppModel>> {
        return withContext(Dispatchers.IO) {
            try {
                // 🛠️ فحص ذكي: إذا كان المفتاح 0 فهذا يعني أن الرابط جاهز كنص صافٍ
                val rawUrl = if (cryptoSalt == 0.toByte()) {
                    String(encryptedUrl, Charsets.UTF_8)
                } else {
                    // كود الـ XOR الاحتياطي في حال قررت تفعيله مستقبلاً
                    decryptUrlStream(encryptedUrl, cryptoSalt)
                }
                
                // استدعاء الواجهة البرمجية بالرابط النظيف المحمي
                val response = apiService.getAppsList(rawUrl)
                
                if (response.isSuccessful) {
                    val apps = response.body()
                    if (!apps.isNullOrEmpty()) {
                        Result.success(apps)
                    } else {
                        Result.failure(Exception("قائمة التطبيقات فارغة في الخادم"))
                    }
                } else {
                    // التقاط أخطاء السيرفر بحذر لعدم تسريب أي معلومات عن هيكل المستودع
                    Result.failure(Exception("فشل مزامنة المستودع الآمن: ${response.code()}"))
                }
            } catch (e: Exception) {
                // التقاط أخطاء الشبكة العامة أو انقطاع الاتصال
                Result.failure(e)
            }
        }
    }

    /**
     * المحرك الداخلي السري لفك بوابات الـ XOR لضمان تشويه الرابط تماماً
     * في حال محاولة سحب الـ Heap Dump من الذاكرة
     */
    private fun decryptUrlStream(secureBytes: ByteArray, salt: Byte): String {
        val output = ByteArray(secureBytes.size)
        for (i in secureBytes.indices) {
            output[i] = (secureBytes[i].toInt() xor salt.toInt()).toByte()
        }
        return String(output, Charsets.UTF_8)
    }
}
