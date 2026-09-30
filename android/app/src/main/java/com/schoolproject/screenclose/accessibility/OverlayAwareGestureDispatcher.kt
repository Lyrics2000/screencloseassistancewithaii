package com.schoolproject.screenclose.accessibility

import com.schoolproject.screenclose.capture.CaptureRegistry
import com.schoolproject.screenclose.domain.GestureDispatcher

/** Hides (and makes untouchable) the overlay so the injected tap reaches the app underneath. */
class OverlayAwareGestureDispatcher(
    private val registry: CaptureRegistry,
    private val delegate: GestureDispatcher,
) : GestureDispatcher {
    override suspend fun tap(x: Float, y: Float): Boolean {
        val overlay = registry.overlay
        return try {
            overlay?.hideAll()
            delegate.tap(x, y)
        } finally {
            overlay?.restore()
        }
    }
}
