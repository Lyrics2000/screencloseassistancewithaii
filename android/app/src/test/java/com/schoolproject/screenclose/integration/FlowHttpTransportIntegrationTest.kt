package com.schoolproject.screenclose.integration

import com.schoolproject.screenclose.auth.TokenProvider
import com.schoolproject.screenclose.core.ErrorCode
import com.schoolproject.screenclose.core.InMemoryMetrics
import com.schoolproject.screenclose.core.RecordingLogger
import com.schoolproject.screenclose.core.ScaException
import com.schoolproject.screenclose.core.SystemClockMillis
import com.schoolproject.screenclose.domain.AnalysisAction
import com.schoolproject.screenclose.domain.AnalysisRequest
import com.schoolproject.screenclose.network.FlowHttpTransport
import com.schoolproject.screenclose.network.HttpClientFactory
import com.schoolproject.screenclose.network.RetryPolicy
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Integration test of the real OkHttp + Retrofit + kotlinx.serialization stack against an
 * in-process HTTP server (MockWebServer) that plays the role of the Power Automate trigger.
 */
class FlowHttpTransportIntegrationTest {

    private lateinit var server: MockWebServer
    private val logger = RecordingLogger()
    private val sleeps = mutableListOf<Long>()
    private var tokenCalls = mutableListOf<Boolean>()

    private val request = AnalysisRequest(
        requestId = "3f2504e0-4f89-11d3-9a0c-0305e82c3301",
        sessionId = "session-1",
        screenshotBase64 = "/9j/" + "Q".repeat(400),
        mimeType = "image/jpeg",
        screenshotWidth = 720,
        screenshotHeight = 1568,
        screenshotHash = "0123456789abcdef",
        foregroundPackage = "com.schoolproject.adtestbed",
    )

    private val closeBody = """
        {"requestId":"${request.requestId}","action":"CLOSE","confidence":0.96,
         "target":{"label":"X","centerX":680,"centerY":60,"normalizedX":0.9444,"normalizedY":0.0383,"left":660,"top":40,"right":700,"bottom":80},
         "waitSeconds":0,"reason":"Close icon top right.","warnings":[],"screenshotHash":"${request.screenshotHash}"}
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun transport(
        tokens: TokenProvider = TokenProvider { force -> tokenCalls.add(force); if (force) "fresh-token" else "cached-token" },
        client: OkHttpClient = HttpClientFactory.okHttp(5).newBuilder().connectionSpecs(listOf(okhttp3.ConnectionSpec.CLEARTEXT)).build(),
    ) = FlowHttpTransport(
        api = HttpClientFactory.flowApi(client, server.url("/").toString()),
        endpointUrl = server.url("/workflows/abc/triggers/manual/paths/invoke?api-version=1").toString(),
        tokenProvider = tokens,
        retryPolicy = RetryPolicy(maxAttempts = 3, baseDelayMillis = 10, maxDelayMillis = 40, random = Random(1)),
        clock = SystemClockMillis,
        logger = logger,
        metrics = InMemoryMetrics(logger),
        sleep = { sleeps += it },
    )

    private fun json(code: Int, body: String) = MockResponse().setResponseCode(code)
        .setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun `success returns the parsed document and sends auth, trace and idempotency headers`() = runTest {
        server.enqueue(json(200, closeBody).setHeader("x-sca-flow-run-id", "08585"))
        val r = transport().analyze(request)
        assertEquals(AnalysisAction.CLOSE, r.action)
        assertEquals(680, r.target?.centerX)

        val recorded = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("POST", recorded.method)
        assertEquals("Bearer cached-token", recorded.getHeader("Authorization"))
        assertEquals(request.requestId, recorded.getHeader("x-ms-client-request-id"))
        assertTrue(recorded.getHeader("traceparent")!!.startsWith("00-3f2504e04f8911d39a0c0305e82c3301-"))
        assertTrue(recorded.getHeader("Content-Type")!!.startsWith("application/json"))
        val sent = Json.parseToJsonElement(recorded.body.readUtf8()).jsonObject
        assertEquals("FIND_AD_CLOSE", sent["requestedAction"]!!.jsonPrimitive.content)
        assertEquals(720, sent["screenshotWidth"]!!.jsonPrimitive.int)
        assertFalse("x-api-key must never be sent from the device", recorded.headers.names().any { it.equals("x-api-key", true) })
    }

    @Test
    fun `backend 400 SCA document is returned as-is without retry`() = runTest {
        val err = """{"requestId":"${request.requestId}","action":"ERROR","confidence":0,"target":null,"waitSeconds":0,"reason":"Request rejected by input validation.","warnings":["PACKAGE_NOT_ALLOWED"],"screenshotHash":"${request.screenshotHash}"}"""
        server.enqueue(json(400, err))
        val r = transport().analyze(request)
        assertEquals(AnalysisAction.ERROR, r.action)
        assertEquals("PACKAGE_NOT_ALLOWED", r.warnings.single())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `429 with Retry-After is retried with the same requestId`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "0").setBody("throttled"))
        server.enqueue(json(200, closeBody))
        val r = transport().analyze(request)
        assertEquals(AnalysisAction.CLOSE, r.action)
        assertEquals(2, server.requestCount)
        val ids = (1..2).map { server.takeRequest()!!.getHeader("x-ms-client-request-id") }
        assertEquals(listOf(request.requestId, request.requestId), ids)
        assertEquals(listOf(0L), sleeps)
    }

    @Test
    fun `503 retries exhaust into NETWORK style ERROR`() = runTest {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(503).setBody("<html>busy</html>")) }
        val r = transport().analyze(request)
        assertEquals(AnalysisAction.ERROR, r.action)
        assertEquals(ErrorCode.HTTP_UNEXPECTED_STATUS.name, r.warnings.single())
        assertEquals(3, server.requestCount)
        assertEquals(2, sleeps.size)
    }

    @Test
    fun `401 triggers exactly one forced token refresh`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody(""))
        server.enqueue(json(200, closeBody))
        val r = transport().analyze(request)
        assertEquals(AnalysisAction.CLOSE, r.action)
        assertEquals(listOf(false, true), tokenCalls)
        server.takeRequest()
        assertEquals("Bearer fresh-token", server.takeRequest()!!.getHeader("Authorization"))
    }

    @Test
    fun `repeated 401 becomes AUTH_FAILED`() = runTest {
        repeat(2) { server.enqueue(MockResponse().setResponseCode(401)) }
        val r = transport().analyze(request)
        assertEquals(ErrorCode.AUTH_FAILED.name, r.warnings.single())
    }

    @Test
    fun `markdown wrapped body becomes RESPONSE_UNPARSEABLE`() = runTest {
        server.enqueue(json(200, "```json\n$closeBody\n```"))
        val r = transport().analyze(request)
        assertEquals(AnalysisAction.ERROR, r.action)
        assertEquals(ErrorCode.RESPONSE_UNPARSEABLE.name, r.warnings.single())
        assertNull(r.target)
    }

    @Test
    fun `body with extra fields becomes RESPONSE_UNPARSEABLE`() = runTest {
        server.enqueue(json(200, closeBody.replace("\"reason\"", "\"apiKey\":\"leak\",\"reason\"")))
        assertEquals(ErrorCode.RESPONSE_UNPARSEABLE.name, transport().analyze(request).warnings.single())
    }

    @Test
    fun `socket disconnects are retried then reported as NETWORK_FAILED`() = runTest {
        repeat(3) { server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)) }
        val r = transport().analyze(request)
        assertEquals(ErrorCode.NETWORK_FAILED.name, r.warnings.single())
        assertEquals(2, sleeps.size)
    }

    @Test
    fun `missing sign-in short-circuits without any HTTP call`() = runTest {
        val r = transport(tokens = { throw ScaException(ErrorCode.AUTH_INTERACTION_REQUIRED, "sign in") }).analyze(request)
        assertEquals(ErrorCode.AUTH_INTERACTION_REQUIRED.name, r.warnings.single())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `logs never contain the token or the screenshot`() = runTest {
        server.enqueue(json(200, closeBody))
        transport().analyze(request)
        val all = logger.entries.joinToString("\n") { it.line }
        assertFalse(all.contains("cached-token"))
        assertFalse(all.contains("QQQQQQQQQQ"))
    }
}
