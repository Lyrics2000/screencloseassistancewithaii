package com.schoolproject.screenclose.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import com.schoolproject.screenclose.core.ErrorCode
import com.schoolproject.screenclose.core.Logger
import com.schoolproject.screenclose.core.ScaException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns the VirtualDisplay + ImageReader for one MediaProjection grant.
 *
 * ADR (frame freshness): a VirtualDisplay only produces a frame when content changes, so
 * acquireLatestImage() may return null for a static screen. The reader listener therefore keeps
 * the newest Image open and bumps [frameSeq]. [captureFreshBitmap] waits for a frame newer than
 * the moment the overlay was hidden (so the overlay is never in the screenshot); if the screen is
 * static it falls back to the newest retained frame after [FRESH_FRAME_TIMEOUT_MS].
 *
 * Android 14+: registerCallback() must precede createVirtualDisplay(), and one projection may
 * create only one VirtualDisplay; rotation is handled with VirtualDisplay.resize() + setSurface().
 */
class ScreenCaptureManager(
    private val windowContext: Context,
    private val projection: MediaProjection,
    private val logger: Logger,
    private val onProjectionStopped: () -> Unit,
) {
    private val thread = HandlerThread("sca-capture").apply { start() }
    private val handler = Handler(thread.looper)
    private val lock = Any()
    private val frameSeq = MutableStateFlow(0L)

    private var reader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var latest: Image? = null
    private var width = 0
    private var height = 0
    @Volatile private var released = false

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            logger.info("projection_stopped_by_system")
            release()
            onProjectionStopped()
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            handler.post { resizeIfNeeded(width, height) }
        }
    }

    fun start() {
        val bounds = DisplayContexts.screenBounds(windowContext)
        width = bounds.width()
        height = bounds.height()
        projection.registerCallback(projectionCallback, handler)
        val r = newReader(width, height)
        reader = r
        virtualDisplay = projection.createVirtualDisplay(
            "sca-capture",
            width,
            height,
            DisplayContexts.densityDpi(windowContext),
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface,
            null,
            handler,
        ) ?: throw ScaException(ErrorCode.CAPTURE_FAILED, "createVirtualDisplay returned null")
        logger.info("capture_started", mapOf("width" to width, "height" to height))
    }

    /** Current frame sequence number; take this right after hiding the overlay. */
    fun currentSequence(): Long = frameSeq.value

    /**
     * Returns a copy of a frame produced after [afterSequence], or the newest frame if the screen
     * stayed static. The caller owns (and must recycle) the returned bitmap.
     */
    suspend fun captureFreshBitmap(afterSequence: Long): CapturedBitmap {
        if (released) throw ScaException(ErrorCode.CAPTURE_NOT_RUNNING, "capture was released")
        val bounds = DisplayContexts.screenBounds(windowContext)
        if (bounds.width() != width || bounds.height() != height) {
            handler.post { resizeIfNeeded(bounds.width(), bounds.height()) }
        }
        withTimeoutOrNull(FRESH_FRAME_TIMEOUT_MS) { frameSeq.first { it > afterSequence } }
        synchronized(lock) {
            val image = latest ?: throw ScaException(ErrorCode.CAPTURE_NO_FRAME, "no frame received from VirtualDisplay")
            return try {
                CapturedBitmap(imageToBitmap(image), width, height)
            } catch (e: IllegalStateException) {
                throw ScaException(ErrorCode.CAPTURE_FAILED, "image was already closed", e)
            }
        }
    }

    fun release() {
        if (released) return
        released = true
        runCatching { projection.unregisterCallback(projectionCallback) }
        synchronized(lock) {
            latest?.close()
            latest = null
        }
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { reader?.close() }
        reader = null
        runCatching { projection.stop() }
        thread.quitSafely()
        logger.info("capture_released")
    }

    private fun newReader(w: Int, h: Int): ImageReader =
        ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, MAX_IMAGES).apply {
            setOnImageAvailableListener({ onImageAvailable(it) }, handler)
        }

    private fun onImageAvailable(r: ImageReader) {
        synchronized(lock) {
            if (released || r !== reader) return
            val next = try {
                r.acquireLatestImage()
            } catch (e: IllegalStateException) {
                logger.warn("acquire_latest_image_failed", error = e)
                null
            } ?: return
            latest?.close()
            latest = next
        }
        frameSeq.value = frameSeq.value + 1
    }

    private fun resizeIfNeeded(w: Int, h: Int) {
        if (released || (w == width && h == height) || w <= 0 || h <= 0) return
        val old = reader
        val fresh = newReader(w, h)
        synchronized(lock) {
            latest?.close()
            latest = null
            reader = fresh
            width = w
            height = h
        }
        virtualDisplay?.resize(w, h, DisplayContexts.densityDpi(windowContext))
        virtualDisplay?.surface = fresh.surface
        old?.close()
        logger.info("capture_resized", mapOf("width" to w, "height" to h))
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer.duplicate().apply { rewind() }
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val paddedWidth = rowStride / pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(buffer)
        if (paddedWidth == image.width) return padded
        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        padded.recycle()
        return cropped
    }

    class CapturedBitmap(val bitmap: Bitmap, val screenWidth: Int, val screenHeight: Int)

    private companion object {
        const val MAX_IMAGES = 3
        const val FRESH_FRAME_TIMEOUT_MS = 600L
    }
}
