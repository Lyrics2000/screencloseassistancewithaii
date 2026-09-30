package com.schoolproject.screenclose.capture

import com.schoolproject.screenclose.overlay.OverlayVisibility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide registry of the running capture pipeline. Written only by [ScreenCaptureService]
 * (main thread); read by the screenshot source and the gesture dispatcher.
 */
class CaptureRegistry {
    @Volatile var manager: ScreenCaptureManager? = null
        private set
    @Volatile var overlay: OverlayVisibility? = null
        private set

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun attach(manager: ScreenCaptureManager, overlay: OverlayVisibility) {
        this.manager = manager
        this.overlay = overlay
        _running.value = true
    }

    fun detach() {
        manager = null
        overlay = null
        _running.value = false
    }
}
