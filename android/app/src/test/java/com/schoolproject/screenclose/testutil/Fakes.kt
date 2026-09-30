package com.schoolproject.screenclose.testutil

import com.schoolproject.screenclose.core.Clock
import com.schoolproject.screenclose.core.IdGenerator
import com.schoolproject.screenclose.domain.AnalysisAction
import com.schoolproject.screenclose.domain.AnalysisRequest
import com.schoolproject.screenclose.domain.AnalysisResponse
import com.schoolproject.screenclose.domain.AnalysisTarget
import com.schoolproject.screenclose.domain.AnalysisTransport
import com.schoolproject.screenclose.domain.EncodedFrame
import com.schoolproject.screenclose.domain.ForegroundPackageProvider
import com.schoolproject.screenclose.domain.GestureDispatcher
import com.schoolproject.screenclose.domain.ScreenshotSource
import kotlinx.coroutines.test.TestScope

class TestClock(private val scope: TestScope) : Clock {
    override fun nowMillis(): Long = scope.testScheduler.currentTime
}

class SequentialIds(private val prefix: String = "id") : IdGenerator {
    private var n = 0
    override fun newId(): String = "$prefix-${++n}"
}

class FakeScreenshotSource(private val frames: ArrayDeque<EncodedFrame>) : ScreenshotSource {
    var captures = 0
        private set

    override suspend fun capture(): EncodedFrame {
        captures++
        return frames.removeFirst()
    }
}

class ScriptedTransport(private val reply: (AnalysisRequest) -> AnalysisResponse) : AnalysisTransport {
    val requests = mutableListOf<AnalysisRequest>()
    override suspend fun analyze(request: AnalysisRequest): AnalysisResponse {
        requests += request
        return reply(request)
    }
}

class RecordingGestures(private val result: Boolean = true) : GestureDispatcher {
    val taps = mutableListOf<Pair<Float, Float>>()
    override suspend fun tap(x: Float, y: Float): Boolean {
        taps += x to y
        return result
    }
}

class MutableForeground(var pkg: String? = TEST_PACKAGE) : ForegroundPackageProvider {
    override fun currentPackage(): String? = pkg
}

const val TEST_PACKAGE = "com.schoolproject.adtestbed"

fun frame(hash: Long, width: Int = 720, height: Int = 1568, screenWidth: Int = 1080, screenHeight: Int = 2352) = EncodedFrame(
    base64 = "/9j/AAAA",
    mimeType = "image/jpeg",
    width = width,
    height = height,
    screenWidth = screenWidth,
    screenHeight = screenHeight,
    hash = hash,
    foregroundPackage = TEST_PACKAGE,
    capturedAtMillis = 0,
)

fun closeResponse(req: AnalysisRequest, cx: Int = 680, cy: Int = 60, confidence: Double = 0.97) = AnalysisResponse(
    requestId = req.requestId,
    action = AnalysisAction.CLOSE,
    confidence = confidence,
    target = AnalysisTarget(
        label = "X close icon",
        centerX = cx,
        centerY = cy,
        normalizedX = cx.toDouble() / req.screenshotWidth,
        normalizedY = cy.toDouble() / req.screenshotHeight,
        left = cx - 20,
        top = cy - 20,
        right = cx + 20,
        bottom = cy + 20,
    ),
    waitSeconds = 0,
    reason = "Interstitial ad with a close icon in the top-right corner.",
    warnings = emptyList(),
    screenshotHash = req.screenshotHash,
)

fun simpleResponse(req: AnalysisRequest, action: AnalysisAction, waitSeconds: Int = 0) = AnalysisResponse(
    requestId = req.requestId,
    action = action,
    confidence = 0.95,
    target = null,
    waitSeconds = waitSeconds,
    reason = "Test reason.",
    warnings = emptyList(),
    screenshotHash = req.screenshotHash,
)
