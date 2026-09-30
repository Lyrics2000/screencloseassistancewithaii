package com.schoolproject.screenclose.integration

import com.schoolproject.screenclose.core.InMemoryMetrics
import com.schoolproject.screenclose.core.NoopLogger
import com.schoolproject.screenclose.core.SystemClockMillis
import com.schoolproject.screenclose.core.UuidGenerator
import com.schoolproject.screenclose.domain.SessionController
import com.schoolproject.screenclose.domain.SessionState
import com.schoolproject.screenclose.domain.StopReason
import com.schoolproject.screenclose.network.FlowHttpTransport
import com.schoolproject.screenclose.network.HttpClientFactory
import com.schoolproject.screenclose.network.RetryPolicy
import com.schoolproject.screenclose.testutil.FakeScreenshotSource
import com.schoolproject.screenclose.testutil.MutableForeground
import com.schoolproject.screenclose.testutil.RecordingGestures
import com.schoolproject.screenclose.testutil.frame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ConnectionSpec
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * End-to-end: SessionController + real HTTP stack + a MockWebServer that behaves like the
 * SCA flow (echoes requestId / screenshotHash). Real time is used (one 2 s cooldown).
 */
class SessionEndToEndIntegrationTest {
    private lateinit var server: MockWebServer
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    @Before
    fun setUp() {
        server = MockWebServer()
        val calls = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = Json.parseToJsonElement(request.body.clone().readUtf8()).jsonObject
                val rid = body["requestId"]!!.jsonPrimitive.content
                val hash = body["screenshotHash"]!!.jsonPrimitive.content
                val json = if (calls.incrementAndGet() == 1) {
                    """{"requestId":"$rid","action":"CLOSE","confidence":0.97,"target":{"label":"X","centerX":360,"centerY":784,"normalizedX":0.5,"normalizedY":0.5,"left":340,"top":764,"right":380,"bottom":804},"waitSeconds":0,"reason":"Test ad with close.","warnings":[],"screenshotHash":"$hash"}"""
                } else {
                    """{"requestId":"$rid","action":"NO_ACTION","confidence":0.93,"target":null,"waitSeconds":0,"reason":"No ad visible.","warnings":[],"screenshotHash":"$hash"}"""
                }
                return MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(json)
            }
        }
        server.start()
    }

    @After
    fun tearDown() {
        scope.cancel()
        executor.shutdownNow()
        server.shutdown()
    }

    @Test
    fun `close then confirm then re-analyze ends with NO_ACTION`() = runBlocking {
        val gestures = RecordingGestures()
        val client = HttpClientFactory.okHttp(5).newBuilder().connectionSpecs(listOf(ConnectionSpec.CLEARTEXT)).build()
        val transport = FlowHttpTransport(
            api = HttpClientFactory.flowApi(client, server.url("/").toString()),
            endpointUrl = server.url("/invoke").toString(),
            tokenProvider = { "token" },
            retryPolicy = RetryPolicy(),
            clock = SystemClockMillis,
            logger = NoopLogger,
            metrics = InMemoryMetrics(NoopLogger),
        )
        val controller = SessionController(
            scope = scope,
            screenshotSource = FakeScreenshotSource(ArrayDeque(listOf(frame(0L), frame(-1L)))),
            transport = transport,
            gestureDispatcher = gestures,
            foregroundPackageProvider = MutableForeground(),
            clock = SystemClockMillis,
            ids = UuidGenerator,
            logger = NoopLogger,
            metrics = InMemoryMetrics(NoopLogger),
        )

        withTimeout(15_000) {
            withContext(dispatcher) { controller.start() }
            controller.state.first { it is SessionState.AwaitingConfirmation }
            withContext(dispatcher) { controller.confirm() }
            val stopped = controller.state.first { it is SessionState.Stopped } as SessionState.Stopped
            assertEquals(StopReason.NO_ACTION, stopped.reason)
        }

        assertEquals(listOf(540f to 1176f), gestures.taps)
        assertEquals(2, server.requestCount)
        val first = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val second = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals(first["sessionId"], second["sessionId"])
        assertNotEquals(first["requestId"], second["requestId"])
    }
}
