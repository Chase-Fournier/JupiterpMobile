package com.jupiterp.jupiterpmobile.data.storage
import com.jupiterp.jupiterpmobile.domain.model.ScheduleSelection
import com.jupiterp.jupiterpmobile.domain.model.StoredSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
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

    /**
     * Atomically read-modify-write the stored app data. All writers must go
     * through this; separate load + save calls from concurrent coroutines can
     * interleave and silently drop each other's changes.
     */
    suspend fun updateAppData(transform: (AppData) -> AppData)
}

/**
 * Shared mutex-based implementation of [LocalStorage.updateAppData] for
 * implementations whose load/save are plain reads/writes.
 */
abstract class MutexGuardedStorage : LocalStorage {
    private val writeMutex = Mutex()

    override suspend fun updateAppData(transform: (AppData) -> AppData) {
        writeMutex.withLock {
            saveAppData(transform(loadAppData()))
        }
    }
}

/**
 * In-memory implementation with JSON serialization hooks
 * Platform-specific implementations should extend this
 */
class InMemoryLocalStorage : MutexGuardedStorage() {
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