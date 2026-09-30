package com.schoolproject.screenclose.core

import java.security.SecureRandom
import java.util.UUID

/** Time source abstraction so the session state machine can be tested with virtual time. */
fun interface Clock {
    fun nowMillis(): Long
}

object SystemClockMillis : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

fun interface IdGenerator {
    fun newId(): String
}

object UuidGenerator : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}

/** W3C trace-context helper; the trace id is derived from the requestId for correlation. */
object TraceContext {
    private val random = SecureRandom()

    fun traceparentFor(requestId: String): String {
        val hex = requestId.replace("-", "").lowercase()
        val traceId = if (hex.length == 32 && hex.all { it in '0'..'9' || it in 'a'..'f' }) hex else randomHex(16)
        return "00-$traceId-${randomHex(8)}-01"
    }

    private fun randomHex(bytes: Int): String {
        val b = ByteArray(bytes)
        random.nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }
}
