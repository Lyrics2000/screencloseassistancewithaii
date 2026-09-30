package com.schoolproject.screenclose.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import androidx.core.content.IntentCompat
import com.schoolproject.screenclose.R
import com.schoolproject.screenclose.ScreenCloseApp
import com.schoolproject.screenclose.core.ErrorCode
import com.schoolproject.screenclose.core.ScaException
import com.schoolproject.screenclose.overlay.OverlayController
import kotlinx.coroutines.launch

/**
 * Foreground service of type mediaProjection. It owns the MediaProjection, the capture
 * pipeline and the floating overlay for as long as the user keeps capture enabled.
 *
 * Order matters on Android 14+: startForeground(type=MEDIA_PROJECTION) must be called BEFORE
 * MediaProjectionManager.getMediaProjection(), and each consent Intent can be used only once.
 */
class ScreenCaptureService : Service() {

    private var manager: ScreenCaptureManager? = null
    private var overlay: OverlayController? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(startIntent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as ScreenCloseApp).container
        val intent = startIntent ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_STOP -> {
                container.logger.info("capture_service_stop_requested")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> Unit
            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        if (manager != null) return START_NOT_STICKY

        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val data = IntentCompat.getParcelableExtra(intent, EXTRA_RESULT_DATA, Intent::class.java)
        try {
            if (data == null) throw ScaException(ErrorCode.CAPTURE_FAILED, "missing projection consent data")
            val mpm = getSystemService(MediaProjectionManager::class.java)
            val projection = mpm.getMediaProjection(resultCode, data)
                ?: throw ScaException(ErrorCode.CAPTURE_FAILED, "user consent was not granted")
            val windowContext = DisplayContexts.overlayContext(this)
            val captureManager = ScreenCaptureManager(windowContext, projection, container.logger) {
                // System revoked the projection (e.g. user tapped "Stop sharing").
                container.mainScope.launch { stopSelf() }
            }
            captureManager.start()
            val overlayController = OverlayController(windowContext, container.sessionController, container.mainScope, container.logger)
            overlayController.show()
            manager = captureManager
            overlay = overlayController
            container.captureRegistry.attach(captureManager, overlayController)
            container.metrics.increment("capture_service_started")
        } catch (e: Exception) {
            container.logger.error("capture_service_start_failed", error = e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        val container = (application as ScreenCloseApp).container
        container.sessionController.cancel("Screen capture stopped.")
        container.captureRegistry.detach()
        overlay?.dismiss()
        overlay = null
        manager?.release()
        manager = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        container.logger.info("capture_service_destroyed")
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(null, getString(R.string.notification_stop), stopIntent).build())
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "sca_capture"
        private const val NOTIFICATION_ID = 4101
        const val ACTION_START = "com.schoolproject.screenclose.action.START_CAPTURE"
        const val ACTION_STOP = "com.schoolproject.screenclose.action.STOP_CAPTURE"
        private const val EXTRA_RESULT_CODE = "resultCode"
        private const val EXTRA_RESULT_DATA = "resultData"

        fun startIntent(context: Context, resultCode: Int, data: Intent): Intent =
            Intent(context, ScreenCaptureService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)

        fun stopIntent(context: Context): Intent =
            Intent(context, ScreenCaptureService::class.java).setAction(ACTION_STOP)
    }
}
