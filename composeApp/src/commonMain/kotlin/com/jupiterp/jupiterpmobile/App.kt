package com.jupiterp

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.jupiterp.jupiterpmobile.data.repository.PreferencesRepository
import com.jupiterp.di.allModules
import com.jupiterp.ui.screens.MainScreen
import com.jupiterp.ui.screens.MainViewModel
import com.jupiterp.ui.theme.JupiterpTheme
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject

/**
 * Main App entry point
 * Sets up Koin DI and the theme
 */
@Composable
fun App() {
    KoinApplication(application = {
        modules(allModules)
    }) {
        val systemDarkTheme = isSystemInDarkTheme()
        val preferencesRepository: PreferencesRepository = koinInject()

        // Collect the dark mode preference
        val storedDarkMode by preferencesRepository.isDarkMode.collectAsState(initial = null)

        // Use stored preference if set, otherwise use system theme
        val isDarkMode = storedDarkMode ?: systemDarkTheme

        JupiterpTheme(darkTheme = isDarkMode) {
            Surface(
                modifier = Modifier.fillMaxSize()
            ) {
                val viewModel: MainViewModel = koinInject()
                MainScreen(
                    viewModel = viewModel,
                    isDarkMode = isDarkMode,
                    onToggleDarkMode = {
                        preferencesRepository.setDarkMode(!isDarkMode)
                    }
                )
            }
        }
    }
}

/**
 * Preview entry point (without Koin)
 */
@Composable
fun AppPreview() {
    JupiterpTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
        ) {
            // Preview content would go here
        }
    }
}