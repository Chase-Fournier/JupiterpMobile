package com.jupiterp.jupiterpmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        // Edge-to-edge implicitly disables decor fitting; calling
        // WindowCompat.setDecorFitsSystemWindows(false) here would be redundant
        // and can shadow enableEdgeToEdge's smart system-bar styling.
        enableEdgeToEdge()

        setContent {
            App()
        }
    }
}