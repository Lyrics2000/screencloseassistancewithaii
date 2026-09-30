package com.schoolproject.screenclose.core

import android.util.Log

/** Logcat adapter. Stack traces are included for warnings and errors; values are redacted. */
class AndroidLogger(private val tag: String = "SCA") : Logger {
    override fun info(event: String, fields: Map<String, Any?>) {
        Log.i(tag, SafeLog.format(event, fields))
    }

    override fun warn(event: String, fields: Map<String, Any?>, error: Throwable?) {
        Log.w(tag, SafeLog.format(event, fields), error?.let(::sanitize))
    }

    override fun error(event: String, fields: Map<String, Any?>, error: Throwable?) {
        Log.e(tag, SafeLog.format(event, fields), error?.let(::sanitize))
    }

    /** Re-wraps a throwable so its message is redacted while the stack trace is preserved. */
    private fun sanitize(t: Throwable): Throwable {
        val copy = RuntimeException(SafeLog.redact("${t.javaClass.name}: ${t.message}"))
        copy.stackTrace = t.stackTrace
        t.cause?.let { copy.initCause(sanitize(it)) }
        return copy
    }
}
