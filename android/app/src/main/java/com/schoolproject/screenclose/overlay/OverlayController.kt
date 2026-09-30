package com.schoolproject.screenclose.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.schoolproject.screenclose.core.Logger
import com.schoolproject.screenclose.domain.SessionController
import com.schoolproject.screenclose.domain.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Two overlay windows:
 *  - panel: small, draggable, focus-less window with the controls and the confirmation buttons;
 *  - marker: full-screen, NOT_TOUCHABLE window that draws the target box while awaiting
 *    confirmation. It never intercepts input.
 * While hidden (during capture and during the confirmed tap) both windows are invisible AND
 * untouchable, so they are neither photographed nor able to swallow the injected gesture.
 */
class OverlayController(
    private val windowContext: Context,
    private val sessionController: SessionController,
    private val mainScope: CoroutineScope,
    private val logger: Logger,
) : OverlayVisibility {

    private val windowManager = windowContext.getSystemService(WindowManager::class.java)
    private val lifecycleOwner = OverlayLifecycleOwner()
    private var panelView: ComposeView? = null
    private var markerView: ComposeView? = null
    private var stateJob: Job? = null
    private var forcedHidden = false
    private val markerOrigin = mutableStateOf(IntOffset.Zero)

    private val panelParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 32
        y = 360
        title = "SCA panel"
    }

    private val markerParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        setFitInsetsTypes(0)
        title = "SCA marker"
    }

    fun show() {
        if (panelView != null) return
        lifecycleOwner.create()

        val marker = ComposeView(windowContext).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { MarkerLayer(sessionController.state, markerOrigin) }
            addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                val loc = IntArray(2)
                v.getLocationOnScreen(loc)
                markerOrigin.value = IntOffset(loc[0], loc[1])
            }
            visibility = View.INVISIBLE
        }
        val panel = ComposeView(windowContext).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                OverlayPanel(
                    stateFlow = sessionController.state,
                    onStart = { sessionController.start() },
                    onConfirm = { sessionController.confirm() },
                    onCancel = { sessionController.cancel() },
                    onDrag = ::dragPanel,
                )
            }
        }
        windowManager.addView(marker, markerParams)
        windowManager.addView(panel, panelParams)
        markerView = marker
        panelView = panel
        lifecycleOwner.resume()
        stateJob = mainScope.launch { sessionController.state.collect { applyVisibility(it) } }
        logger.info("overlay_shown")
    }

    fun dismiss() {
        stateJob?.cancel()
        stateJob = null
        panelView?.let { runCatching { windowManager.removeViewImmediate(it) } }
        markerView?.let { runCatching { windowManager.removeViewImmediate(it) } }
        panelView = null
        markerView = null
        lifecycleOwner.destroy()
        logger.info("overlay_dismissed")
    }

    override suspend fun hideAll() {
        forcedHidden = true
        applyVisibility(sessionController.state.value)
        // Two frames at 60 Hz so SurfaceFlinger composes a frame without the overlay.
        delay(HIDE_SETTLE_MS)
    }

    override fun restore() {
        forcedHidden = false
        applyVisibility(sessionController.state.value)
    }

    private fun applyVisibility(state: SessionState) {
        val panel = panelView ?: return
        val marker = markerView ?: return
        val hidePanel = forcedHidden || state is SessionState.Clicking
        panel.visibility = if (hidePanel) View.INVISIBLE else View.VISIBLE
        val touchFlag = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val newFlags = if (hidePanel) panelParams.flags or touchFlag else panelParams.flags and touchFlag.inv()
        if (newFlags != panelParams.flags) {
            panelParams.flags = newFlags
            runCatching { windowManager.updateViewLayout(panel, panelParams) }
        }
        marker.visibility = if (!forcedHidden && state is SessionState.AwaitingConfirmation) View.VISIBLE else View.INVISIBLE
    }

    private fun dragPanel(dx: Float, dy: Float) {
        val panel = panelView ?: return
        val bounds = windowManager.maximumWindowMetrics.bounds
        panelParams.x = (panelParams.x + dx.roundToInt()).coerceIn(0, (bounds.width() - panel.width).coerceAtLeast(0))
        panelParams.y = (panelParams.y + dy.roundToInt()).coerceIn(0, (bounds.height() - panel.height).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(panel, panelParams) }
    }

    private companion object {
        const val HIDE_SETTLE_MS = 48L
    }
}
