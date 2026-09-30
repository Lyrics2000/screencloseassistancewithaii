package com.schoolproject.screenclose.domain

import com.schoolproject.screenclose.core.ErrorCode
import com.schoolproject.screenclose.core.InMemoryMetrics
import com.schoolproject.screenclose.core.NoopLogger
import com.schoolproject.screenclose.core.ScaException
import com.schoolproject.screenclose.testutil.FakeScreenshotSource
import com.schoolproject.screenclose.testutil.MutableForeground
import com.schoolproject.screenclose.testutil.RecordingGestures
import com.schoolproject.screenclose.testutil.ScriptedTransport
import com.schoolproject.screenclose.testutil.SequentialIds
import com.schoolproject.screenclose.testutil.TestClock
import com.schoolproject.screenclose.testutil.closeResponse
import com.schoolproject.screenclose.testutil.frame
import com.schoolproject.screenclose.testutil.simpleResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionControllerTest {

    private val hashA = 0L
    private val hashB = -1L
    private val hashC = 0x00000000FFFFFFFFL
    private val hashD = 0x0F0F0F0F0F0F0F0FL

    private fun TestScope.controller(
        frames: List<EncodedFrame>,
        transport: AnalysisTransport,
        gestures: GestureDispatcher = RecordingGestures(),
        foreground: ForegroundPackageProvider = MutableForeground(),
        source: ScreenshotSource = FakeScreenshotSource(ArrayDeque(frames)),
    ) = SessionController(
        scope = backgroundScope,
        screenshotSource = source,
        transport = transport,
        gestureDispatcher = gestures,
        foregroundPackageProvider = foreground,
        clock = TestClock(this),
        ids = SequentialIds(),
        logger = NoopLogger,
        metrics = InMemoryMetrics(NoopLogger),
    )

    @Test
    fun `CLOSE waits for confirmation and never taps on its own`() = runTest {
        val gestures = RecordingGestures()
        val c = controller(listOf(frame(hashA)), ScriptedTransport { closeResponse(it) }, gestures)
        assertTrue(c.start())
        runCurrent()
        val s = c.state.value
        assertTrue(s is SessionState.AwaitingConfirmation)
        advanceTimeBy(10_000)
        assertTrue(gestures.taps.isEmpty())
    }

    @Test
    fun `confirmed tap maps coordinates, cools down 2 s, then re-analyzes`() = runTest {
        val gestures = RecordingGestures()
        val source = FakeScreenshotSource(ArrayDeque(listOf(frame(hashA), frame(hashB))))
        var calls = 0
        val transport = ScriptedTransport { req -> if (++calls == 1) closeResponse(req, cx = 360, cy = 784) else simpleResponse(req, AnalysisAction.NO_ACTION) }
        val c = controller(emptyList(), transport, gestures, source = source)
        c.start()
        runCurrent()
        assertTrue(c.confirm())
        runCurrent()
        assertEquals(listOf(540f to 1176f), gestures.taps)
        assertTrue(c.state.value is SessionState.CoolingDown)
        advanceTimeBy(1_999)
        assertEquals(1, source.captures)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(2, source.captures)
        val stopped = c.state.value as SessionState.Stopped
        assertEquals(StopReason.NO_ACTION, stopped.reason)
        assertEquals(1, gestures.taps.size)
    }

    @Test
    fun `WAIT, NO_ACTION, UNSAFE and ERROR stop immediately after one request`() = runTest {
        val cases = mapOf(
            AnalysisAction.WAIT to StopReason.WAIT,
            AnalysisAction.NO_ACTION to StopReason.NO_ACTION,
            AnalysisAction.UNSAFE to StopReason.UNSAFE,
            AnalysisAction.ERROR to StopReason.ERROR,
        )
        for ((action, reason) in cases) {
            val transport = ScriptedTransport { req ->
                if (action == AnalysisAction.ERROR) AnalysisResponse.error(req.requestId, req.screenshotHash, "PACKAGE_NOT_ALLOWED", "no")
                else simpleResponse(req, action, waitSeconds = if (action == AnalysisAction.WAIT) 5 else 0)
            }
            val gestures = RecordingGestures()
            val c = controller(listOf(frame(hashA), frame(hashB)), transport, gestures)
            c.start()
            runCurrent()
            val s = c.state.value as SessionState.Stopped
            assertEquals(action.name, reason, s.reason)
            assertEquals(1, transport.requests.size)
            assertTrue(gestures.taps.isEmpty())
        }
    }

    @Test
    fun `at most three attempts per session`() = runTest {
        val gestures = RecordingGestures()
        val transport = ScriptedTransport { closeResponse(it) }
        val c = controller(listOf(frame(hashA), frame(hashB), frame(hashC), frame(hashD)), transport, gestures)
        c.start()
        repeat(3) {
            runCurrent()
            assertTrue(c.state.value is SessionState.AwaitingConfirmation)
            c.confirm()
            runCurrent()
            advanceTimeBy(2_001)
        }
        runCurrent()
        val s = c.state.value as SessionState.Stopped
        assertEquals(StopReason.MAX_ATTEMPTS, s.reason)
        assertEquals(3, transport.requests.size)
        assertEquals(3, gestures.taps.size)
    }

    @Test
    fun `unchanged screen after a tap stops the session`() = runTest {
        val gestures = RecordingGestures()
        val transport = ScriptedTransport { closeResponse(it) }
        val c = controller(listOf(frame(hashA), frame(hashA or 0b11L)), transport, gestures)
        c.start()
        runCurrent()
        c.confirm()
        runCurrent()
        advanceTimeBy(2_001)
        runCurrent()
        assertEquals(StopReason.SCREEN_UNCHANGED, (c.state.value as SessionState.Stopped).reason)
        assertEquals(1, transport.requests.size)
        assertEquals(1, gestures.taps.size)
    }

    @Test
    fun `confirmation expires after 15 s`() = runTest {
        val gestures = RecordingGestures()
        val c = controller(listOf(frame(hashA)), ScriptedTransport { closeResponse(it) }, gestures)
        c.start()
        runCurrent()
        advanceTimeBy(15_001)
        runCurrent()
        assertEquals(StopReason.CONFIRMATION_EXPIRED, (c.state.value as SessionState.Stopped).reason)
        assertFalse(c.confirm())
        assertTrue(gestures.taps.isEmpty())
    }

    @Test
    fun `foreground app change blocks the tap`() = runTest {
        val gestures = RecordingGestures()
        val fg = MutableForeground()
        val c = controller(listOf(frame(hashA)), ScriptedTransport { closeResponse(it) }, gestures, fg)
        c.start()
        runCurrent()
        fg.pkg = "com.example.banking"
        c.confirm()
        runCurrent()
        assertEquals(StopReason.STALE_SCREEN, (c.state.value as SessionState.Stopped).reason)
        assertTrue(gestures.taps.isEmpty())
    }

    @Test
    fun `second start while busy is rejected - one outstanding request`() = runTest {
        val transport = ScriptedTransport { req -> simpleResponse(req, AnalysisAction.NO_ACTION) }
        val slow = AnalysisTransport { req -> delay(5_000); transport.analyze(req) }
        val c = controller(listOf(frame(hashA), frame(hashB)), slow)
        assertTrue(c.start())
        runCurrent()
        assertTrue(c.state.value is SessionState.Analyzing)
        assertFalse(c.start())
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `cancel during analysis stops the session`() = runTest {
        val slow = AnalysisTransport { req -> delay(30_000); simpleResponse(req, AnalysisAction.NO_ACTION) }
        val c = controller(listOf(frame(hashA)), slow)
        c.start()
        runCurrent()
        c.cancel()
        runCurrent()
        assertEquals(StopReason.USER_CANCELLED, (c.state.value as SessionState.Stopped).reason)
    }

    @Test
    fun `failed gesture stops with CLICK_FAILED`() = runTest {
        val c = controller(listOf(frame(hashA)), ScriptedTransport { closeResponse(it) }, RecordingGestures(result = false))
        c.start()
        runCurrent()
        c.confirm()
        runCurrent()
        assertEquals(StopReason.CLICK_FAILED, (c.state.value as SessionState.Stopped).reason)
    }

    @Test
    fun `capture failure stops with CAPTURE_FAILED and sends nothing`() = runTest {
        val transport = ScriptedTransport { closeResponse(it) }
        val failing = ScreenshotSource { throw ScaException(ErrorCode.CAPTURE_NOT_RUNNING, "not running") }
        val c = controller(emptyList(), transport, source = failing)
        c.start()
        runCurrent()
        assertEquals(StopReason.CAPTURE_FAILED, (c.state.value as SessionState.Stopped).reason)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `invalid CLOSE from transport is converted to ERROR and never tapped`() = runTest {
        val gestures = mockk<GestureDispatcher>()
        coEvery { gestures.tap(any(), any()) } returns true
        val transport = ScriptedTransport { req -> closeResponse(req, confidence = 0.5) }
        val c = controller(listOf(frame(hashA)), transport, gestures)
        c.start()
        runCurrent()
        val s = c.state.value as SessionState.Stopped
        assertEquals(StopReason.ERROR, s.reason)
        assertEquals(ErrorCode.CONFIDENCE_INVALID.name, s.lastResponse?.warnings?.first())
        coVerify(exactly = 0) { gestures.tap(any(), any()) }
    }

    @Test
    fun `request carries session id, hash hex and FIND_AD_CLOSE`() = runTest {
        val transport = ScriptedTransport { req -> simpleResponse(req, AnalysisAction.NO_ACTION) }
        val c = controller(listOf(frame(0x1FL)), transport)
        c.start()
        runCurrent()
        val req = transport.requests.single()
        assertEquals("id-1", req.sessionId)
        assertEquals("id-2", req.requestId)
        assertEquals("000000000000001f", req.screenshotHash)
        assertEquals(REQUESTED_ACTION_FIND_AD_CLOSE, req.requestedAction)
    }
}
