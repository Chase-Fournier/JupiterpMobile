package com.jupiterp.jupiterpmobile.di

import com.jupiterp.jupiterpmobile.data.api.JupiterpApiClient
import com.jupiterp.jupiterpmobile.data.repository.CourseRepository
import com.jupiterp.jupiterpmobile.data.repository.ScheduleRepository
import com.jupiterp.jupiterpmobile.data.repository.PreferencesRepository
import com.jupiterp.jupiterpmobile.data.storage.LocalStorage
import com.jupiterp.jupiterpmobile.data.storage.createPlatformStorage
import com.jupiterp.jupiterpmobile.ui.screens.MainViewModel
import org.koin.dsl.module

/**
 * Koin dependency injection module
 */
val appModule = module {
    // Storage
    single<LocalStorage> { createPlatformStorage() }

    // API Client
    single { JupiterpApiClient() }

    // Repositories
    single { CourseRepository(get()) }
    single { ScheduleRepository(get()) }
    single { PreferencesRepository(get()) }

    // ViewModels
    factory { MainViewModel(get(), get()) }
}

/**
 * All modules combined
 */
val allModules = listOf(appModule)