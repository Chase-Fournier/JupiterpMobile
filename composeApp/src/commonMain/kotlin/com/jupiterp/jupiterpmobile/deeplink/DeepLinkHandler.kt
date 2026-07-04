package com.jupiterp.jupiterpmobile.deeplink

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide hand-off point between platform deep link entry points and
 * shared code.
 *
 * Platform code (Android's `MainActivity`, iOS's `iOSApp.swift`) pushes
 * incoming URLs here via [onDeepLink]; the URL is held until shared code is
 * ready to act on it, which covers cold starts where the link arrives before
 * Compose (and the ViewModel that imports shared schedules) exists. The
 * consumer clears the pending link with [consume] so it is handled exactly
 * once.
 */
object DeepLinkHandler {
    private val _pendingUrl = MutableStateFlow<String?>(null)

    /** The most recent unconsumed deep link URL, or `null` if there is none. */
    val pendingUrl: StateFlow<String?> = _pendingUrl.asStateFlow()

    /** Called from platform entry points with the full URL that opened the app. */
    fun onDeepLink(url: String) {
        _pendingUrl.value = url
    }

    /** Mark the pending link as handled so it isn't re-processed. */
    fun consume() {
        _pendingUrl.value = null
    }
}
