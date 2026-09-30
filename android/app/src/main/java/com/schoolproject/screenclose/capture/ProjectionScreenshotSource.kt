package com.schoolproject.screenclose.capture

import com.schoolproject.screenclose.core.Clock
import com.schoolproject.screenclose.core.ErrorCode
import com.schoolproject.screenclose.core.Logger
import com.schoolproject.screenclose.core.Metrics
import com.schoolproject.screenclose.core.ScaException
import com.schoolproject.screenclose.domain.EncodedFrame
import com.schoolproject.screenclose.domain.ForegroundPackageProvider
import com.schoolproject.screenclose.domain.ScreenshotSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hides the overlay, grabs a fresh frame, restores the overlay, then encodes off the main thread.
 * The bitmap is recycled immediately after encoding; only the base64 string (in memory) survives
 * until the request completes.
 */
class ProjectionScreenshotSource(
    private val registry: CaptureRegistry,
    private val encoder: FrameEncoder,
    private val foregroundPackageProvider: ForegroundPackageProvider,
    private val clock: Clock,
    private val logger: Logger,
    private val metrics: Metrics,
) : ScreenshotSource {

    override suspend fun capture(): EncodedFrame {
        val manager = registry.manager ?: throw ScaException(ErrorCode.CAPTURE_NOT_RUNNING, "start screen capture first")
        val overlay = registry.overlay
        val foreground = foregroundPackageProvider.currentPackage()
            ?: throw ScaException(ErrorCode.FOREGROUND_UNKNOWN, "enable the accessibility service so the foreground app is known")
        val started = clock.nowMillis()

        val captured = try {
            // Sequence is read BEFORE hiding: the first frame composed after the overlay disappears
            // is newer than `seq`, so the overlay can never appear in the screenshot.
            val seq = manager.currentSequence()
            overlay?.hideAll()
            manager.captureFreshBitmap(seq)
        } finally {
            overlay?.restore()
        }

        val encoded = withContext(Dispatchers.Default) {
            try {
                encoder.encode(captured.bitmap)
            } finally {
                captured.bitmap.recycle()
            }
        }
        metrics.recordLatency("capture_encode", clock.nowMillis() - started)
        logger.info(
            "frame_captured",
            mapOf("width" to encoded.width, "height" to encoded.height, "base64Chars" to encoded.base64.length, "package" to foreground),
        )
        return EncodedFrame(
            base64 = encoded.base64,
            mimeType = encoded.mimeType,
            width = encoded.width,
            height = encoded.height,
            screenWidth = captured.screenWidth,
            screenHeight = captured.screenHeight,
            hash = encoded.hash,
            foregroundPackage = foreground,
            capturedAtMillis = started,
        )
    }
}
