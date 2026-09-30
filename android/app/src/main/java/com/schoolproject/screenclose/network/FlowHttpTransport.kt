package com.schoolproject.screenclose.network

import com.schoolproject.screenclose.auth.TokenProvider
import com.schoolproject.screenclose.core.Clock
import com.schoolproject.screenclose.core.ErrorCode
import com.schoolproject.screenclose.core.Logger
import com.schoolproject.screenclose.core.Metrics
import com.schoolproject.screenclose.core.ScaException
import com.schoolproject.screenclose.core.TraceContext
import com.schoolproject.screenclose.domain.AnalysisRequest
import com.schoolproject.screenclose.domain.AnalysisResponse
import com.schoolproject.screenclose.domain.AnalysisTransport
import com.schoolproject.screenclose.domain.ScaJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Authenticated transport to the Power Automate HTTP trigger (ADR-001).
 *
 * Contract: never throws (except cancellation). Every outcome is an [AnalysisResponse]:
 *  - 2xx/4xx/5xx whose body is a valid SCA document -> that document (the flow's own verdict);
 *  - 401 -> one forced token refresh, then retry;
 *  - 408/429/502/503/504 without an SCA document, or IOException -> bounded retry with backoff;
 *  - anything else -> ERROR with a stable code in warnings[0].
 * The same requestId is reused on every retry (idempotency key).
 */
class FlowHttpTransport(
    private val api: FlowApi,
    private val endpointUrl: String,
    private val tokenProvider: TokenProvider,
    private val retryPolicy: RetryPolicy,
    private val clock: Clock,
    private val logger: Logger,
    private val metrics: Metrics,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) : AnalysisTransport {

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    override suspend fun analyze(request: AnalysisRequest): AnalysisResponse {
        val body = ScaJson.encodeToString(AnalysisRequest.serializer(), request)
        val traceparent = TraceContext.traceparentFor(request.requestId)
        var forcedRefresh = false
        var attempt = 0
        var lastError: Pair<ErrorCode, String> = ErrorCode.NETWORK_FAILED to "no attempt made"

        while (attempt < retryPolicy.maxAttempts) {
            attempt++
            val started = clock.nowMillis()
            val token = try {
                tokenProvider.accessToken(forceRefresh = forcedRefresh)
            } catch (e: ScaException) {
                logger.warn("token_unavailable", mapOf("requestId" to request.requestId, "code" to e.errorCode.code), e)
                return error(request, e.errorCode, "Sign-in required before analysis.")
            }
            try {
                val response = api.analyze(
                    url = endpointUrl,
                    authorization = "Bearer $token",
                    clientRequestId = request.requestId,
                    traceparent = traceparent,
                    accept = "application/json",
                    body = body.toRequestBody(jsonMedia),
                )
                val status = response.code()
                val text = (if (response.isSuccessful) response.body() else response.errorBody())?.use { it.string() }.orEmpty()
                metrics.recordLatency("flow_http", clock.nowMillis() - started, mapOf("status" to status.toString()))
                logger.info(
                    "flow_http_response",
                    mapOf(
                        "requestId" to request.requestId,
                        "attempt" to attempt,
                        "status" to status,
                        "flowRunId" to response.headers()["x-sca-flow-run-id"],
                    ),
                )

                val document = decodeOrNull(text)
                if (document != null) return document

                if (status == 401 && !forcedRefresh) {
                    forcedRefresh = true
                    attempt-- // the refresh retry does not consume the budget
                    continue
                }
                if (retryPolicy.isRetryableStatus(status) && attempt < retryPolicy.maxAttempts) {
                    val wait = retryPolicy.delayMillis(attempt, response.headers()["Retry-After"])
                    metrics.increment("flow_http_retry", mapOf("status" to status.toString()))
                    sleep(wait)
                    continue
                }
                lastError = if (response.isSuccessful) {
                    ErrorCode.RESPONSE_UNPARSEABLE to "The service returned a body that is not a valid SCA response."
                } else if (status == 401 || status == 403) {
                    ErrorCode.AUTH_FAILED to "The analysis service rejected the sign-in (HTTP $status)."
                } else {
                    ErrorCode.HTTP_UNEXPECTED_STATUS to "Unexpected HTTP $status from the analysis service."
                }
                break
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                logger.warn("flow_http_io_error", mapOf("requestId" to request.requestId, "attempt" to attempt), e)
                lastError = ErrorCode.NETWORK_FAILED to "Network error contacting the analysis service."
                if (attempt < retryPolicy.maxAttempts) {
                    metrics.increment("flow_http_retry", mapOf("status" to "io"))
                    sleep(retryPolicy.delayMillis(attempt, null))
                    continue
                }
            }
        }
        metrics.increment("flow_http_failed", mapOf("code" to lastError.first.name))
        return error(request, lastError.first, lastError.second)
    }

    private fun decodeOrNull(text: String): AnalysisResponse? {
        if (text.isBlank()) return null
        return try {
            ScaJson.decodeFromString(AnalysisResponse.serializer(), text)
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun error(request: AnalysisRequest, code: ErrorCode, reason: String) =
        AnalysisResponse.error(request.requestId, request.screenshotHash, code.name, reason)
}
