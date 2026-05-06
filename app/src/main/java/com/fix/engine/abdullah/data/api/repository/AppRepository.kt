package com.fix.engine.abdullah.data.repository

import com.fix.engine.abdullah.data.api.RetrofitClient
import com.fix.engine.abdullah.data.model.AppModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository {

    private val apiService = RetrofitClient.instance

    suspend fun fetchApps(repoUrl: String): Result<List<AppModel>> {
        return withContext(Dispatchers.IO) { // العمل في خلفية النظام
            try {
                val response = apiService.getAppsList(repoUrl)
                Result.success(response)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
