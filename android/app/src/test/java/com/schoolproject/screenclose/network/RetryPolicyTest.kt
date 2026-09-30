package com.schoolproject.screenclose.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RetryPolicyTest {
    private val policy = RetryPolicy(maxAttempts = 3, baseDelayMillis = 1_000, maxDelayMillis = 8_000, random = Random(42))

    @Test fun `retryable statuses`() {
        listOf(408, 429, 502, 503, 504).forEach { assertTrue("$it", policy.isRetryableStatus(it)) }
        listOf(200, 400, 401, 403, 409, 500).forEach { assertFalse("$it", policy.isRetryableStatus(it)) }
    }

    @Test fun `exponential with equal jitter stays within bounds`() {
        repeat(100) {
            assertTrue(policy.delayMillis(1, null) in 500..1_000)
            assertTrue(policy.delayMillis(2, null) in 1_000..2_000)
            assertTrue(policy.delayMillis(3, null) in 2_000..4_000)
            assertTrue(policy.delayMillis(10, null) in 4_000..8_000)
        }
    }

    @Test fun `retry-after header wins and is capped`() {
        assertEquals(3_000, policy.delayMillis(1, "3"))
        assertEquals(8_000, policy.delayMillis(1, "120"))
    }

    @Test fun `malformed retry-after falls back to backoff`() {
        assertTrue(policy.delayMillis(1, "Wed, 21 Oct 2015 07:28:00 GMT") in 500..1_000)
    }
}
