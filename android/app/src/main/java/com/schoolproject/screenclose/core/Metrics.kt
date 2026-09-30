package com.schoolproject.screenclose.core

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Minimal metrics port. Counters and latency samples are kept in memory (shown on the
 * diagnostics card) and emitted as structured log lines ("metric=...") that can be scraped
 * from logcat or forwarded to Application Insights later without touching call sites.
 */
interface Metrics {
    fun increment(name: String, tags: Map<String, String> = emptyMap())
    fun recordLatency(name: String, millis: Long, tags: Map<String, String> = emptyMap())
    fun snapshot(): Map<String, Long>
}

class InMemoryMetrics(private val logger: Logger) : Metrics {
    private val counters = ConcurrentHashMap<String, AtomicLong>()
    private val lastLatency = ConcurrentHashMap<String, AtomicLong>()

    override fun increment(name: String, tags: Map<String, String>) {
        val key = key(name, tags)
        val value = counters.computeIfAbsent(key) { AtomicLong() }.incrementAndGet()
        logger.info("metric", mapOf("name" to name, "type" to "counter", "value" to value) + tags)
    }

    override fun recordLatency(name: String, millis: Long, tags: Map<String, String>) {
        lastLatency.computeIfAbsent(key(name, tags)) { AtomicLong() }.set(millis)
        logger.info("metric", mapOf("name" to name, "type" to "latency_ms", "value" to millis) + tags)
    }

    override fun snapshot(): Map<String, Long> =
        counters.mapValues { it.value.get() } + lastLatency.mapKeys { "${it.key}.last_ms" }.mapValues { it.value.get() }

    private fun key(name: String, tags: Map<String, String>): String =
        if (tags.isEmpty()) name else name + tags.entries.sortedBy { it.key }.joinToString(",", "{", "}") { "${it.key}=${it.value}" }
}
