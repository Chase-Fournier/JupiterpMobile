package com.jupiterp.jupiterpmobile.data.storage

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
/**
 * Android implementation using SharedPreferences
 */
class AndroidLocalStorage(private val context: Context) : LocalStorage {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("jupiterp_prefs", Context.MODE_PRIVATE)
    }

    private val _appData: MutableStateFlow<AppData> by lazy {
        MutableStateFlow(loadAppDataSync())
    }

    override suspend fun saveAppData(data: AppData) {
        try {
            val jsonString = json.encodeToString(data)
            prefs.edit().putString(KEY_APP_DATA, jsonString).apply()
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
            val jsonString = prefs.getString(KEY_APP_DATA, null) ?: return AppData()
            json.decodeFromString<AppData>(jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            AppData()
        }
    }

    companion object {
        private const val KEY_APP_DATA = "app_data"
    }
}

// Singleton context holder - initialized in MainActivity
object AndroidContextHolder {
    var appContext: Context? = null
}

actual fun createPlatformStorage(): LocalStorage {
    val context = AndroidContextHolder.appContext
        ?: throw IllegalStateException("AndroidContextHolder.appContext must be set in MainActivity before using storage")
    return AndroidLocalStorage(context)
}