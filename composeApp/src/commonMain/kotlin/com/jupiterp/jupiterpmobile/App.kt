package com.jupiterp.jupiterpmobile

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jupiterp.jupiterpmobile.data.repository.PreferencesRepository
import com.jupiterp.jupiterpmobile.di.allModules
import com.jupiterp.jupiterpmobile.ui.screens.MainScreen
import com.jupiterp.jupiterpmobile.ui.screens.MainViewModel
import com.jupiterp.jupiterpmobile.ui.screens.generator.GeneratorScreen
import com.jupiterp.jupiterpmobile.ui.screens.generator.GeneratorViewModel
import com.jupiterp.ui.theme.JupiterpTheme
import org.koin.compose.KoinApplication
import org.koin.compose.getKoin
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
                // Resolve through the ViewModelStore so the instances survive
                // configuration changes and onCleared() actually runs.
                val koin = getKoin()
                var showGenerator by remember { mutableStateOf(false) }

                if (showGenerator) {
                    val generatorViewModel: GeneratorViewModel =
                        viewModel { koin.get<GeneratorViewModel>() }
                    GeneratorScreen(
                        viewModel = generatorViewModel,
                        onClose = { showGenerator = false }
                    )
                } else {
                    val mainViewModel: MainViewModel = viewModel { koin.get<MainViewModel>() }
                    MainScreen(
                        viewModel = mainViewModel,
                        isDarkMode = isDarkMode,
                        onToggleDarkMode = {
                            preferencesRepository.setDarkMode(!isDarkMode)
                        },
                        onOpenGenerator = { showGenerator = true }
                    )
                }
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