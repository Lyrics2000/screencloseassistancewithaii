package com.schoolproject.screenclose.overlay

/** Lets capture and gesture code hide the overlay so it is neither photographed nor tapped. */
interface OverlayVisibility {
    /** Hides every overlay window, makes it untouchable, and waits until the change is composited. Main thread. */
    suspend fun hideAll()

    /** Re-applies the visibility that matches the current session state. Main thread. */
    fun restore()
}
