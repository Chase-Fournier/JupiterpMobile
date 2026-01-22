package com.jupiterp.jupiterpmobile.data.repository

import com.jupiterp.jupiterpmobile.data.storage.LocalStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Repository for managing user preferences
 */
class PreferencesRepository(
    private val storage: LocalStorage
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isDarkMode = MutableStateFlow<Boolean?>(null)
    val isDarkMode: Flow<Boolean?> = _isDarkMode.asStateFlow()

    init {
        // Load saved preferences on init
        scope.launch {
            try {
                val appData = storage.loadAppData()
                _isDarkMode.value = appData.isDarkMode
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Get the current dark mode preference (synchronously)
     */
    fun getDarkModeSync(): Boolean? = _isDarkMode.value

    /**
     * Set dark mode preference
     */
    fun setDarkMode(isDark: Boolean) {
        _isDarkMode.value = isDark
        scope.launch {
            try {
                val currentData = storage.loadAppData()
                storage.saveAppData(currentData.copy(isDarkMode = isDark))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Reset to system theme
     */
    fun useSystemTheme() {
        _isDarkMode.value = null
        scope.launch {
            try {
                val currentData = storage.loadAppData()
                storage.saveAppData(currentData.copy(isDarkMode = null))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}