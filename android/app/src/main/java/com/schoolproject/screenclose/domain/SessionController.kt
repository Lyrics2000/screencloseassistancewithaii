package com.schoolproject.screenclose.domain

import com.schoolproject.screenclose.core.Clock
import com.schoolproject.screenclose.core.ErrorCode
import com.schoolproject.screenclose.core.IdGenerator
import com.schoolproject.screenclose.core.Logger
import com.schoolproject.screenclose.core.Metrics
import com.schoolproject.screenclose.core.ScaException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Session state machine: one user-initiated session = at most [SessionConfig.maxAttempts]
 * capture/analyse cycles, at most one tap per screenshot, a user confirmation before every tap,
 * a cooldown after every tap, and an immediate stop on WAIT / NO_ACTION / UNSAFE / ERROR.
 *
 * ADR (concurrency): all public methods and all state mutations are confined to the
 * single-threaded dispatcher of [scope] (Dispatchers.Main.immediate in the app, a
 * StandardTestDispatcher in tests). This removes data races on [session] without locks.
 * [requestGate] additionally guarantees a single outstanding analysis request even if a future
 * caller used a multi-threaded dispatcher.
 */
class SessionController(
    private val scope: CoroutineScope,
    private val screenshotSource: ScreenshotSource,
    private val transport: AnalysisTransport,
    private val gestureDispatcher: GestureDispatcher,
    private val foregroundPackageProvider: ForegroundPackageProvider,
    private val clock: Clock,
    private val ids: IdGenerator,
    private val logger: Logger,
    private val metrics: Metrics,
    private val validator: ResponseValidator = ResponseValidator(),
    private val config: SessionConfig = SessionConfig(),
) {
    private class SessionData(val id: String) {
        var attempts = 0
        val analyzedHashes = mutableListOf<Long>()
        val clickedHashes = mutableSetOf<Long>()
    }

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val requestGate = Mutex()
    private var session: SessionData? = null
    private var workJob: Job? = null
    private var expiryJob: Job? = null

    /** Starts a new user-initiated session. Returns false if one is already running. */
    fun start(): Boolean {
        if (_state.value.isBusy) {
            logger.warn("session_start_rejected", mapOf("state" to _state.value::class.simpleName))
            return false
        }
        val data = SessionData(ids.newId())
        session = data
        metrics.increment("session_started")
        logger.info("session_started", mapOf("sessionId" to data.id))
        workJob = scope.launch { runAttempt(data) }
        return true
    }

    /** User pressed "Tap it". Only valid while awaiting confirmation. */
    fun confirm(): Boolean {
        val awaiting = _state.value as? SessionState.AwaitingConfirmation ?: return false
        val data = session?.takeIf { it.id == awaiting.sessionId } ?: return false
        expiryJob?.cancel()
        workJob = scope.launch { performConfirmedTap(data, awaiting) }
        return true
    }

    /** User pressed Cancel / Stop, or the capture service is shutting down. */
    fun cancel(detail: String = "Cancelled by user.") {
        if (!_state.value.isBusy) return
        workJob?.cancel()
        expiryJob?.cancel()
        finish(StopReason.USER_CANCELLED, detail, null)
    }

    private suspend fun runAttempt(data: SessionData) {
        if (data.attempts >= config.maxAttempts) {
            finish(StopReason.MAX_ATTEMPTS, "Reached ${config.maxAttempts} attempts.", null)
            return
        }
        data.attempts += 1
        val attempt = data.attempts
        _state.value = SessionState.Capturing(data.id, attempt)

        val frame = try {
            screenshotSource.capture()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ScaException) {
            logger.error("capture_failed", mapOf("sessionId" to data.id, "code" to e.errorCode.code), e)
            finish(StopReason.CAPTURE_FAILED, e.message ?: e.errorCode.name, null)
            return
        } catch (e: Exception) {
            logger.error("capture_failed", mapOf("sessionId" to data.id, "code" to ErrorCode.CAPTURE_FAILED.code), e)
            finish(StopReason.CAPTURE_FAILED, ErrorCode.CAPTURE_FAILED.name, null)
            return
        }

        val duplicate = data.analyzedHashes.any { java.lang.Long.bitCount(it xor frame.hash) <= config.duplicateHammingThreshold }
        if (duplicate) {
            metrics.increment("session_screen_unchanged")
            finish(StopReason.SCREEN_UNCHANGED, "The screen did not change since the last analysis.", null)
            return
        }
        data.analyzedHashes += frame.hash

        val requestId = ids.newId()
        val request = AnalysisRequest(
            requestId = requestId,
            sessionId = data.id,
            screenshotBase64 = frame.base64,
            mimeType = frame.mimeType,
            screenshotWidth = frame.width,
            screenshotHeight = frame.height,
            screenshotHash = PerceptualHashHex.toHex(frame.hash),
            foregroundPackage = frame.foregroundPackage,
        )
        _state.value = SessionState.Analyzing(data.id, attempt, requestId)

        if (!requestGate.tryLock()) {
            finish(StopReason.ERROR, ErrorCode.REQUEST_ALREADY_OUTSTANDING.name, null)
            return
        }
        val started = clock.nowMillis()
        val raw = try {
            transport.analyze(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("transport_contract_violation", mapOf("requestId" to requestId), e)
            AnalysisResponse.error(requestId, request.screenshotHash, ErrorCode.NETWORK_FAILED.name, "Transport failed unexpectedly.")
        } finally {
            requestGate.unlock()
        }
        metrics.recordLatency("analysis_roundtrip", clock.nowMillis() - started)

        val response = validator.validate(
            SentFrame(requestId, request.screenshotHash, frame.width, frame.height),
            raw,
        )
        metrics.increment("analysis_result", mapOf("action" to response.action.name))
        logger.info(
            "analysis_result",
            mapOf(
                "sessionId" to data.id,
                "requestId" to requestId,
                "attempt" to attempt,
                "action" to response.action,
                "confidence" to response.confidence,
                "warnings" to response.warnings.joinToString("|"),
            ),
        )

        when (response.action) {
            AnalysisAction.CLOSE -> awaitConfirmation(data, attempt, frame, response)
            AnalysisAction.WAIT -> finish(StopReason.WAIT, "Close control not available yet (${response.waitSeconds}s). ${response.reason}", response)
            AnalysisAction.NO_ACTION -> finish(StopReason.NO_ACTION, response.reason, response)
            AnalysisAction.UNSAFE -> finish(StopReason.UNSAFE, response.reason, response)
            AnalysisAction.ERROR -> finish(StopReason.ERROR, "${response.warnings.firstOrNull() ?: "ERROR"}: ${response.reason}", response)
        }
    }

    private fun awaitConfirmation(data: SessionData, attempt: Int, frame: EncodedFrame, response: AnalysisResponse) {
        val target = requireNotNull(response.target) { "validator guarantees a target for CLOSE" }
        val screenTarget = CoordinateMapper.toScreen(target, frame.width, frame.height, frame.screenWidth, frame.screenHeight)
        val expiresAt = clock.nowMillis() + config.confirmationTimeoutMillis
        val awaiting = SessionState.AwaitingConfirmation(
            sessionId = data.id,
            attempt = attempt,
            response = response,
            target = screenTarget,
            foregroundPackage = frame.foregroundPackage,
            frameHash = frame.hash,
            expiresAtMillis = expiresAt,
        )
        _state.value = awaiting
        expiryJob = scope.launch {
            delay(config.confirmationTimeoutMillis)
            if (_state.value === awaiting) {
                metrics.increment("confirmation_expired")
                finish(StopReason.CONFIRMATION_EXPIRED, "No confirmation within ${config.confirmationTimeoutMillis / 1000}s.", response)
            }
        }
    }

    private suspend fun performConfirmedTap(data: SessionData, awaiting: SessionState.AwaitingConfirmation) {
        if (clock.nowMillis() > awaiting.expiresAtMillis) {
            finish(StopReason.CONFIRMATION_EXPIRED, "Confirmation arrived too late.", awaiting.response)
            return
        }
        val currentPackage = foregroundPackageProvider.currentPackage()
        if (currentPackage != awaiting.foregroundPackage) {
            metrics.increment("stale_screen")
            finish(StopReason.STALE_SCREEN, "Foreground app changed (${currentPackage ?: "unknown"}).", awaiting.response)
            return
        }
        if (!data.clickedHashes.add(awaiting.frameHash)) {
            finish(StopReason.ERROR, ErrorCode.ALREADY_CLICKED.name, awaiting.response)
            return
        }
        _state.value = SessionState.Clicking(data.id, awaiting.attempt)
        val tapped = try {
            gestureDispatcher.tap(awaiting.target.x, awaiting.target.y)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ScaException) {
            logger.error("tap_failed", mapOf("sessionId" to data.id, "code" to e.errorCode.code), e)
            false
        } catch (e: Exception) {
            logger.error("tap_failed", mapOf("sessionId" to data.id, "code" to ErrorCode.GESTURE_CANCELLED.code), e)
            false
        }
        if (!tapped) {
            metrics.increment("tap_failed")
            finish(StopReason.CLICK_FAILED, "The system did not complete the tap.", awaiting.response)
            return
        }
        metrics.increment("tap_completed")
        logger.info("tap_completed", mapOf("sessionId" to data.id, "attempt" to awaiting.attempt))
        _state.value = SessionState.CoolingDown(data.id, awaiting.attempt, clock.nowMillis() + config.cooldownMillis)
        delay(config.cooldownMillis)
        runAttempt(data)
    }

    private fun finish(reason: StopReason, detail: String, last: AnalysisResponse?) {
        val id = session?.id
        expiryJob?.cancel()
        _state.value = SessionState.Stopped(id, reason, detail, last)
        metrics.increment("session_stopped", mapOf("reason" to reason.name))
        logger.info("session_stopped", mapOf("sessionId" to id, "reason" to reason, "detail" to detail))
    }
}
