package com.jupiterp.jupiterpmobile.data.storage
import com.jupiterp.domain.model.ScheduleSelection
import com.jupiterp.domain.model.StoredSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Data class for persisted app data
 */
@Serializable
data class AppData(
    val isDarkMode: Boolean? = null, // null = follow system
    val currentSchedule: List<ScheduleSelection> = emptyList(),
    val savedSchedules: List<StoredSchedule> = emptyList(),
    val colorCounter: Int = 0
)

/**
 * Interface for local storage operations
 */
interface LocalStorage {
    suspend fun saveAppData(data: AppData)
    suspend fun loadAppData(): AppData
    fun getAppDataFlow(): Flow<AppData>
}

/**
 * In-memory implementation with JSON serialization hooks
 * Platform-specific implementations should extend this
 */
class InMemoryLocalStorage : LocalStorage {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _appData = MutableStateFlow(AppData())

    override suspend fun saveAppData(data: AppData) {
        _appData.value = data
    }

    override suspend fun loadAppData(): AppData {
        return _appData.value
    }

    override fun getAppDataFlow(): Flow<AppData> = _appData.asStateFlow()

    protected fun serialize(data: AppData): String {
        return json.encodeToString(data)
    }

    protected fun deserialize(jsonString: String): AppData {
        return try {
            json.decodeFromString<AppData>(jsonString)
        } catch (e: Exception) {
            AppData()
        }
    }
}