package com.schoolproject.screenclose.core

/**
 * Logging port. Production uses [AndroidLogger]; JVM tests use [RecordingLogger] or [NoopLogger].
 * Callers pass structured fields; [SafeLog.redact] is applied to every value so screenshots,
 * bearer tokens and API keys can never reach logcat (requirement 9).
 */
interface Logger {
    fun info(event: String, fields: Map<String, Any?> = emptyMap())
    fun warn(event: String, fields: Map<String, Any?> = emptyMap(), error: Throwable? = null)
    fun error(event: String, fields: Map<String, Any?> = emptyMap(), error: Throwable? = null)
}

object NoopLogger : Logger {
    override fun info(event: String, fields: Map<String, Any?>) = Unit
    override fun warn(event: String, fields: Map<String, Any?>, error: Throwable?) = Unit
    override fun error(event: String, fields: Map<String, Any?>, error: Throwable?) = Unit
}

class RecordingLogger : Logger {
    data class Entry(val level: String, val line: String, val error: Throwable?)

    private val lock = Any()
    private val _entries = mutableListOf<Entry>()
    val entries: List<Entry> get() = synchronized(lock) { _entries.toList() }

    override fun info(event: String, fields: Map<String, Any?>) = add("I", event, fields, null)
    override fun warn(event: String, fields: Map<String, Any?>, error: Throwable?) = add("W", event, fields, error)
    override fun error(event: String, fields: Map<String, Any?>, error: Throwable?) = add("E", event, fields, error)

    private fun add(level: String, event: String, fields: Map<String, Any?>, error: Throwable?) {
        synchronized(lock) { _entries += Entry(level, SafeLog.format(event, fields), error) }
    }
}

object SafeLog {
    private val base64Run = Regex("[A-Za-z0-9+/=_-]{200,}")
    private val bearer = Regex("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+")
    private val anthropicKey = Regex("sk-ant-[A-Za-z0-9_-]+")
    private val jwt = Regex("eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}")
    private val sasSig = Regex("(?i)sig=[^&\\s]+")
    private val sensitiveKeys = setOf("authorization", "screenshotbase64", "x-api-key", "token", "accesstoken", "body")

    fun redact(value: String): String = value
        .replace(anthropicKey, "[REDACTED_KEY]")
        .replace(bearer, "Bearer [REDACTED]")
        .replace(jwt, "[REDACTED_JWT]")
        .replace(sasSig, "sig=[REDACTED]")
        .replace(base64Run, "[REDACTED_BLOB]")

    fun format(event: String, fields: Map<String, Any?>): String {
        val sb = StringBuilder("event=").append(event)
        for ((k, v) in fields) {
            val safe = if (k.lowercase() in sensitiveKeys) "[REDACTED]" else redact(v.toString())
            sb.append(' ').append(k).append('=').append(safe)
        }
        return sb.toString()
    }
}
