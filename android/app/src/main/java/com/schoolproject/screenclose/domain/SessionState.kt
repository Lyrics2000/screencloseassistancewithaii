package com.schoolproject.screenclose.domain

enum class StopReason {
    USER_CANCELLED,
    MAX_ATTEMPTS,
    WAIT,
    NO_ACTION,
    UNSAFE,
    ERROR,
    SCREEN_UNCHANGED,
    STALE_SCREEN,
    CONFIRMATION_EXPIRED,
    CLICK_FAILED,
    CAPTURE_FAILED,
}

sealed interface SessionState {
    data object Idle : SessionState

    data class Capturing(val sessionId: String, val attempt: Int) : SessionState

    data class Analyzing(val sessionId: String, val attempt: Int, val requestId: String) : SessionState

    data class AwaitingConfirmation(
        val sessionId: String,
        val attempt: Int,
        val response: AnalysisResponse,
        val target: ScreenTarget,
        val foregroundPackage: String,
        val frameHash: Long,
        val expiresAtMillis: Long,
    ) : SessionState

    data class Clicking(val sessionId: String, val attempt: Int) : SessionState

    data class CoolingDown(val sessionId: String, val attempt: Int, val untilMillis: Long) : SessionState

    data class Stopped(
        val sessionId: String?,
        val reason: StopReason,
        val detail: String,
        val lastResponse: AnalysisResponse?,
    ) : SessionState

    val isBusy: Boolean
        get() = this !is Idle && this !is Stopped
}

data class SessionConfig(
    val maxAttempts: Int = 3,
    val cooldownMillis: Long = 2_000,
    val confirmationTimeoutMillis: Long = 15_000,
    val duplicateHammingThreshold: Int = 4,
) {
    init {
        require(maxAttempts in 1..3) { "maxAttempts must be 1..3" }
        require(cooldownMillis >= 2_000) { "cooldown must be at least 2 s" }
        require(confirmationTimeoutMillis in 3_000..60_000) { "confirmation timeout must be 3..60 s" }
        require(duplicateHammingThreshold in 0..16) { "hamming threshold must be 0..16" }
    }
}
