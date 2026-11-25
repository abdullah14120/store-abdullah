package com.fix.engine.abdullah.data.repository

import com.fix.engine.abdullah.AbdullahStoreApp
import com.fix.engine.abdullah.data.api.RetrofitClient
import com.fix.engine.abdullah.data.model.AppModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File

class AppRepository {

    private val apiService = RetrofitClient.instance
    private val gson = Gson()

    private val downloadsRef by lazy {
        FirebaseDatabase.getInstance("https://abdullah-store-a95ed-default-rtdb.europe-west1.firebasedatabase.app")
            .getReference("download_stats")
    }

    suspend fun fetchApps(encryptedUrl: ByteArray, cryptoSalt: Byte): Result<List<AppModel>> {
        val rawUrl = if (cryptoSalt == 0.toByte()) {
            String(encryptedUrl, Charsets.UTF_8)
        } else {
            decryptUrlStream(encryptedUrl, cryptoSalt)
        }
        return fetchAppsDirect(rawUrl)
    }

    suspend fun fetchAppsDirect(rawUrl: String): Result<List<AppModel>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.getAppsList(rawUrl)
                if (response.isSuccessful) {
                    val apps = response.body()
                    if (!apps.isNullOrEmpty()) {
                        saveCatalogToDiskCache(apps)
                        return@withContext Result.success(apps)
                    }
                }
                val offlineCached = loadCatalogFromDiskCache()
                if (!offlineCached.isNullOrEmpty()) {
                    Result.success(offlineCached)
                } else {
                    Result.failure(Exception("فشل المزامنة: ${response.code()}"))
                }
            } catch (e: Exception) {
                val offlineCached = loadCatalogFromDiskCache()
                if (!offlineCached.isNullOrEmpty()) {
                    Result.success(offlineCached)
                } else {
                    Result.failure(e)
                }
            }
        }
    }

    private fun catalogCacheFile(): File? {
        val ctx = AbdullahStoreApp.appInstance ?: return null
        return File(ctx.cacheDir, "apps_catalog_cache.json")
    }

    private fun saveCatalogToDiskCache(apps: List<AppModel>) {
        runCatching {
            val target = catalogCacheFile() ?: return
            val tmp = File(target.parentFile, "apps_catalog_cache.json.tmp")
            tmp.writeText(gson.toJson(apps), Charsets.UTF_8)
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }
    }

    private fun loadCatalogFromDiskCache(): List<AppModel>? {
        return runCatching {
            val target = catalogCacheFile() ?: return null
            if (!target.exists() || target.length() <= 2L) return null
            val listType = object : TypeToken<List<AppModel>>() {}.type
            gson.fromJson<List<AppModel>>(target.readText(Charsets.UTF_8), listType)
        }.getOrNull()
    }

    private fun decryptUrlStream(secureBytes: ByteArray, salt: Byte): String {
        val output = ByteArray(secureBytes.size)
        for (i in secureBytes.indices) output[i] = (secureBytes[i].toInt() xor salt.toInt()).toByte()
        return String(output, Charsets.UTF_8)
    }

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
                        map[key.hashCode()] = count
                    }
                }
                trySend(map)
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(emptyMap())
            }
        }
        downloadsRef.addValueEventListener(listener)
        awaitClose { downloadsRef.removeEventListener(listener) }
    }

    fun incrementDownloadCount(appId: Int) {
        downloadsRef.child(appId.toString()).setValue(ServerValue.increment(1))
    }
}
