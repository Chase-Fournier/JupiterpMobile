package com.jupiterp.jupiterpmobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jupiterp.jupiterpmobile.data.storage.AndroidContextHolder
import com.jupiterp.jupiterpmobile.deeplink.DeepLinkHandler

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

        // Only on a fresh launch: on recreation (e.g. rotation) the original
        // intent is re-delivered, and re-posting it would import a shared
        // schedule twice.
        if (savedInstanceState == null) {
            handleDeepLink(intent)
        }

        setContent {
            App()
        }
    }

    // Deliveries while the app is already running (launchMode="singleTask")
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.dataString?.let { DeepLinkHandler.onDeepLink(it) }
        }
    }
}