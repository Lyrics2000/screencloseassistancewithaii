package com.schoolproject.screenclose.capture

import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.WindowManager

/**
 * Services are not UI contexts. On API 30+ a window context for TYPE_APPLICATION_OVERLAY is the
 * supported way to add overlay windows and to query window metrics from a Service.
 */
object DisplayContexts {
    fun overlayContext(base: Context): Context {
        val dm = base.getSystemService(DisplayManager::class.java)
        val display = dm.getDisplay(Display.DEFAULT_DISPLAY)
        return base.createDisplayContext(display)
            .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
    }

    /** Full physical screen bounds (including system bars) = dispatchGesture coordinate space. */
    fun screenBounds(windowContext: Context): Rect =
        windowContext.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds

    fun densityDpi(windowContext: Context): Int = windowContext.resources.displayMetrics.densityDpi
}
