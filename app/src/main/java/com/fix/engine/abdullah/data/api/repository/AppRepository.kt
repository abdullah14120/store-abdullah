package com.fix.engine.abdullah.data.repository

import com.fix.engine.abdullah.data.api.RetrofitClient
import com.fix.engine.abdullah.data.model.AppModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

class AppRepository {

    private val apiService = RetrofitClient.instance
    // 🌐 إنشاء مرجع لجدول التنزيلات في الفايربيس
    private val downloadsRef = FirebaseDatabase.getInstance().getReference("AppDownloads")

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
     * 📡 مراقبة العدادات من الفايربيس بشكل حي ومستمر
     */
    fun observeDownloads(): Flow<Map<Int, Long>> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val map = mutableMapOf<Int, Long>()
                for (child in snapshot.children) {
                    val appId = child.key?.toIntOrNull()
                    val count = child.getValue(Long::class.java) ?: 0L
                    if (appId != null) map[appId] = count
                }
                trySend(map) // إرسال التحديثات للـ ViewModel
            }
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        downloadsRef.addValueEventListener(listener)
        awaitClose { downloadsRef.removeEventListener(listener) }
    }

    /**
     * 🚀 زيادة العداد باحترافية عبر نظام (Transaction) 
     * يضمن عدم تخطي أي رقم حتى لو تم التحميل من آلاف الأجهزة بالثانية
     */
    fun incrementDownloadCount(appId: Int) {
        downloadsRef.child(appId.toString()).runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val currentCount = currentData.getValue(Long::class.java) ?: 0L
                currentData.value = currentCount + 1
                return Transaction.success(currentData)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {}
        })
    }
}
