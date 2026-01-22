package com.jupiterp.jupiterpmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import com.jupiterp.App
import com.jupiterp.jupiterpmobile.data.storage.AndroidContextHolder

/**
 * Main Activity for Android
 * Sets up edge-to-edge display and hosts the Compose content
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set context for storage - MUST be before setContent
        AndroidContextHolder.appContext = applicationContext
        // Enable edge-to-edge display
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            App()
        }
    }
}