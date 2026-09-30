package com.schoolproject.screenclose.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.schoolproject.screenclose.ScreenCloseApp

/**
 * The only component allowed to inject input. It never acts on its own: taps are dispatched
 * exclusively through [AccessibilityBridge.tap], which SessionController calls only after the
 * user pressed "Tap it". Window content is never read (canRetrieveWindowContent=false).
 */
class ConfirmedClickService : AccessibilityService() {

    private val bridge: AccessibilityBridge
        get() = (application as ScreenCloseApp).container.accessibilityBridge

    override fun onServiceConnected() {
        super.onServiceConnected()
        bridge.attach(this)
        (application as ScreenCloseApp).container.logger.info("accessibility_connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            bridge.onWindowStateChanged(event.packageName, event.className)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        bridge.detach(this)
        (application as ScreenCloseApp).container.logger.info("accessibility_disconnected")
        super.onDestroy()
    }
}
