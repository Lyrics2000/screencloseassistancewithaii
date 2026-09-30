package com.schoolproject.screenclose.core

/**
 * Stable, greppable error codes. Every failure inside the app is wrapped in [ScaException]
 * carrying one of these codes plus the original cause (and therefore its stack trace).
 * Codes that the backend can also return are listed in docs/10-validation-conditions.md.
 */
enum class ErrorCode(val code: String) {
    AUTH_NOT_INITIALIZED("SCA-A001"),
    AUTH_INTERACTION_REQUIRED("SCA-A002"),
    AUTH_FAILED("SCA-A003"),
    AUTH_CANCELLED("SCA-A004"),
    CAPTURE_NOT_RUNNING("SCA-C001"),
    CAPTURE_NO_FRAME("SCA-C002"),
    CAPTURE_FAILED("SCA-C003"),
    ENCODE_FAILED("SCA-C004"),
    IMAGE_TOO_LARGE("SCA-C005"),
    NETWORK_FAILED("SCA-N001"),
    HTTP_UNEXPECTED_STATUS("SCA-N002"),
    RESPONSE_UNPARSEABLE("SCA-N003"),
    RESPONSE_MISMATCH("SCA-V001"),
    TARGET_CONTRACT_VIOLATION("SCA-V002"),
    TARGET_OUT_OF_BOUNDS("SCA-V003"),
    NORMALIZED_MISMATCH("SCA-V004"),
    CONFIDENCE_INVALID("SCA-V005"),
    WAIT_SECONDS_INVALID("SCA-V006"),
    REASON_MISSING("SCA-V007"),
    GESTURE_SERVICE_UNAVAILABLE("SCA-G001"),
    GESTURE_CANCELLED("SCA-G002"),
    FOREGROUND_UNKNOWN("SCA-S001"),
    REQUEST_ALREADY_OUTSTANDING("SCA-S002"),
    ALREADY_CLICKED("SCA-S003"),
    CONFIG_INVALID("SCA-K001"),
}

class ScaException(
    val errorCode: ErrorCode,
    message: String,
    cause: Throwable? = null,
) : Exception("[${errorCode.code} ${errorCode.name}] $message", cause)
