package com.schoolproject.screenclose.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import com.schoolproject.screenclose.core.ErrorCode
import com.schoolproject.screenclose.core.ScaException
import com.schoolproject.screenclose.domain.ForegroundPackageProvider
import com.schoolproject.screenclose.domain.GestureDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.lang.ref.WeakReference
import kotlin.coroutines.resume

/**
 * Connects the (system-managed) accessibility service to the rest of the app without leaking it.
 * Provides the foreground package (from TYPE_WINDOW_STATE_CHANGED events) and the single-tap
 * gesture primitive. It has no knowledge of sessions; confirmation is enforced by SessionController.
 */
class AccessibilityBridge(private val ownPackage: String) : GestureDispatcher, ForegroundPackageProvider {
    private var serviceRef: WeakReference<AccessibilityService>? = null
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    @Volatile private var foreground: String? = null

    fun attach(service: AccessibilityService) {
        serviceRef = WeakReference(service)
        _connected.value = true
    }

    fun detach(service: AccessibilityService) {
        if (serviceRef?.get() === service) {
            serviceRef = null
            _connected.value = false
        }
    }

    fun onWindowStateChanged(packageName: CharSequence?, className: CharSequence?) {
        val pkg = packageName?.toString() ?: return
        if (pkg == SYSTEM_UI) return // notification shade / status bar is never the target app
        if (pkg == ownPackage) {
            // Our overlay windows must not change the target, but our own Activity in front does:
            // a screenshot of this app is then attributed to this app (and rejected by the allowlist).
            if (className?.toString()?.endsWith(".ui.MainActivity") == true) foreground = pkg
            return
        }
        foreground = pkg
    }

    override fun currentPackage(): String? = foreground

    override suspend fun tap(x: Float, y: Float): Boolean {
        val service = serviceRef?.get()
            ?: throw ScaException(ErrorCode.GESTURE_SERVICE_UNAVAILABLE, "accessibility service is not enabled")
        require(x >= 0f && y >= 0f) { "tap coordinates must be non-negative" }
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MS))
            .build()
        return suspendCancellableCoroutine { cont ->
            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            }
            val dispatched = service.dispatchGesture(gesture, callback, null)
            if (!dispatched && cont.isActive) cont.resume(false)
        }
    }

    private companion object {
        const val SYSTEM_UI = "com.android.systemui"
        const val TAP_DURATION_MS = 60L
    }
}
