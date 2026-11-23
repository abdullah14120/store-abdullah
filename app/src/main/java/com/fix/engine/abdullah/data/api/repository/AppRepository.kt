package com.fix.engine.abdullah.data.repository

import com.fix.engine.abdullah.data.api.RetrofitClient
import com.fix.engine.abdullah.data.model.AppModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

class AppRepository {

    private val apiService = RetrofitClient.instance

    // 🌐 الاتصال المباشر بقاعدة البيانات الأوروبية ونفس جدول شاشة التفاصيل (download_stats)
    private val downloadsRef by lazy {
        FirebaseDatabase.getInstance("https://abdullah-store-a95ed-default-rtdb.europe-west1.firebasedatabase.app")
            .getReference("download_stats")
    }

    suspend fun fetchApps(encryptedUrl: ByteArray, cryptoSalt: Byte): Result<List<AppModel>> {
        return withContext(Dispatchers.IO) {
            try {
                val rawUrl = if (cryptoSalt == 0.toByte()) {
                    String(encryptedUrl, Charsets.UTF_8)
                } else {
                    decryptUrlStream(encryptedUrl, cryptoSalt)
                }
                
                val response = apiService.getAppsList(rawUrl)
                if (response.isSuccessful) {
                    val apps = response.body()
                    if (!apps.isNullOrEmpty()) Result.success(apps) else Result.failure(Exception("قائمة التطبيقات فارغة"))
                } else {
                    Result.failure(Exception("فشل المزامنة: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun decryptUrlStream(secureBytes: ByteArray, salt: Byte): String {
        val output = ByteArray(secureBytes.size)
        for (i in secureBytes.indices) output[i] = (secureBytes[i].toInt() xor salt.toInt()).toByte()
        return String(output, Charsets.UTF_8)
    }

    /**
     * 📡 مراقبة العدادات من الفايربيس (يدعم مفاتيح الحزم مثل com_whatsapp والأرقام معاً)
     */
    fun observeDownloads(): Flow<Map<Int, Long>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val map = mutableMapOf<Int, Long>()
                for (child in snapshot.children) {
                    val count = child.getValue(Long::class.java) ?: 0L
                    val key = child.key ?: continue
                    val idKey = key.toIntOrNull()
                    if (idKey != null) {
                        map[idKey] = count
                    } else {
                        // حفظ القيمة باستخدام hashCode الخاص باسم الحزمة أيضاً في حال الحاجة
                        map[key.hashCode()] = count
                    }
                }
                trySend(map)
            }
            override fun onCancelled(error: DatabaseError) {
                // عدم إغلاق الـ Flow بانهيار في حال كانت قواعد الفايربيس مقفلة مؤقتاً
                trySend(emptyMap())
            }
        }
        downloadsRef.addValueEventListener(listener)
        awaitClose { downloadsRef.removeEventListener(listener) }
    }

    /**
     * 🚀 زيادة العداد بنفس صيغة المفتاح المعتمدة في شاشة التفاصيل
     */
    fun incrementDownloadCount(appId: Int) {
        downloadsRef.child(appId.toString()).setValue(ServerValue.increment(1))
    }
}
