package com.schoolproject.screenclose.network

import kotlin.math.min
import kotlin.random.Random

/**
 * Exponential backoff with "equal jitter": delay = base*2^(n-1)/2 + random(0, base*2^(n-1)/2),
 * capped at [maxDelayMillis]. A server Retry-After (seconds) wins when present, capped likewise.
 * Retries are safe because requestId is an idempotency key enforced by Dataverse (ADR-002).
 */
class RetryPolicy(
    val maxAttempts: Int = 3,
    private val baseDelayMillis: Long = 1_000,
    private val maxDelayMillis: Long = 8_000,
    private val random: Random = Random.Default,
) {
    init {
        require(maxAttempts in 1..5) { "maxAttempts must be 1..5" }
        require(baseDelayMillis > 0 && maxDelayMillis >= baseDelayMillis) { "invalid delays" }
    }

    fun isRetryableStatus(code: Int): Boolean = code == 408 || code == 429 || code == 502 || code == 503 || code == 504

    /** @param attempt the attempt that just failed, starting at 1. */
    fun delayMillis(attempt: Int, retryAfterHeader: String?): Long {
        retryAfterHeader?.trim()?.toLongOrNull()?.let { seconds ->
            if (seconds >= 0) return min(seconds * 1_000, maxDelayMillis)
        }
        val exp = min(maxDelayMillis, baseDelayMillis shl (attempt - 1).coerceIn(0, 20))
        val half = exp / 2
        return half + random.nextLong(half + 1)
    }
}
