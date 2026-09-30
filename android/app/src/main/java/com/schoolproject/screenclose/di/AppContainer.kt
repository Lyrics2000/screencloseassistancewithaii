package com.schoolproject.screenclose.di

import android.content.Context
import com.schoolproject.screenclose.accessibility.AccessibilityBridge
import com.schoolproject.screenclose.accessibility.OverlayAwareGestureDispatcher
import com.schoolproject.screenclose.auth.EntraAuthManager
import com.schoolproject.screenclose.capture.CaptureRegistry
import com.schoolproject.screenclose.capture.FrameEncoder
import com.schoolproject.screenclose.capture.ProjectionScreenshotSource
import com.schoolproject.screenclose.core.AndroidLogger
import com.schoolproject.screenclose.core.AppConfig
import com.schoolproject.screenclose.core.InMemoryMetrics
import com.schoolproject.screenclose.core.Logger
import com.schoolproject.screenclose.core.Metrics
import com.schoolproject.screenclose.core.SystemClockMillis
import com.schoolproject.screenclose.core.UuidGenerator
import com.schoolproject.screenclose.domain.ResponseValidator
import com.schoolproject.screenclose.domain.SessionConfig
import com.schoolproject.screenclose.domain.SessionController
import com.schoolproject.screenclose.network.FlowHttpTransport
import com.schoolproject.screenclose.network.HttpClientFactory
import com.schoolproject.screenclose.network.RetryPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * ADR-005: hand-written composition root. One instance per process, created in
 * ScreenCloseApp.onCreate(). Components that the system instantiates (services, activity) read
 * it from the Application.
 */
class AppContainer(appContext: Context) {
    val config: AppConfig = AppConfig.fromBuildConfig()
    val logger: Logger = AndroidLogger()
    val metrics: Metrics = InMemoryMetrics(logger)

    /** Main-thread confined scope for the session state machine and the overlay. */
    val mainScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val auth = EntraAuthManager(appContext, config, logger)
    val captureRegistry = CaptureRegistry()
    val accessibilityBridge = AccessibilityBridge(appContext.packageName)

    private val transport = FlowHttpTransport(
        api = HttpClientFactory.flowApi(HttpClientFactory.okHttp(config.httpTimeoutSeconds)),
        endpointUrl = config.flowEndpointUrl,
        tokenProvider = auth,
        retryPolicy = RetryPolicy(),
        clock = SystemClockMillis,
        logger = logger,
        metrics = metrics,
    )

    private val screenshotSource = ProjectionScreenshotSource(
        registry = captureRegistry,
        encoder = FrameEncoder(config.maxImageLongEdge, config.jpegQuality, config.maxEncodedImageBytes),
        foregroundPackageProvider = accessibilityBridge,
        clock = SystemClockMillis,
        logger = logger,
        metrics = metrics,
    )

    val sessionController = SessionController(
        scope = mainScope,
        screenshotSource = screenshotSource,
        transport = transport,
        gestureDispatcher = OverlayAwareGestureDispatcher(captureRegistry, accessibilityBridge),
        foregroundPackageProvider = accessibilityBridge,
        clock = SystemClockMillis,
        ids = UuidGenerator,
        logger = logger,
        metrics = metrics,
        validator = ResponseValidator(),
        config = SessionConfig(),
    )
}
