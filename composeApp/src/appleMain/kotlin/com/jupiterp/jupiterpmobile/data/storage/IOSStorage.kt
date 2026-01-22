package com.jupiterp.jupiterpmobile.data.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

/**
 * iOS implementation using NSUserDefaults
 */
class IOSLocalStorage : LocalStorage {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val userDefaults = NSUserDefaults.standardUserDefaults

    private val _appData: MutableStateFlow<AppData> by lazy {
        MutableStateFlow(loadAppDataSync())
    }

    override suspend fun saveAppData(data: AppData) {
        try {
            val jsonString = json.encodeToString(data)
            userDefaults.setObject(jsonString, forKey = KEY_APP_DATA)
            userDefaults.synchronize()
            _appData.value = data
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun loadAppData(): AppData {
        return _appData.value
    }

    override fun getAppDataFlow(): Flow<AppData> = _appData.asStateFlow()

    private fun loadAppDataSync(): AppData {
        return try {
            val jsonString = userDefaults.stringForKey(KEY_APP_DATA) ?: return AppData()
            json.decodeFromString<AppData>(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            AppData()
        }
    }

    companion object {
        private const val KEY_APP_DATA = "jupiterp_app_data"
    }
}

actual fun createPlatformStorage(): LocalStorage {
    return IOSLocalStorage()
}