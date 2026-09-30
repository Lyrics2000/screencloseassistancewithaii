package com.schoolproject.screenclose.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val REQUESTED_ACTION_FIND_AD_CLOSE = "FIND_AD_CLOSE"

/** Request body sent to the "SCA Android HTTP Entry" flow. See power-platform/schemas/android-request.schema.json. */
@Serializable
data class AnalysisRequest(
    val requestId: String,
    val sessionId: String,
    val screenshotBase64: String,
    val mimeType: String,
    val screenshotWidth: Int,
    val screenshotHeight: Int,
    val screenshotHash: String,
    val foregroundPackage: String,
    val requestedAction: String = REQUESTED_ACTION_FIND_AD_CLOSE,
) {
    /** Never prints image bytes (requirement 9). */
    override fun toString(): String =
        "AnalysisRequest(requestId=$requestId, sessionId=$sessionId, mimeType=$mimeType, " +
            "size=${screenshotWidth}x$screenshotHeight, hash=$screenshotHash, package=$foregroundPackage, " +
            "base64Chars=${screenshotBase64.length})"
}

@Serializable
enum class AnalysisAction {
    @SerialName("CLOSE") CLOSE,
    @SerialName("WAIT") WAIT,
    @SerialName("NO_ACTION") NO_ACTION,
    @SerialName("UNSAFE") UNSAFE,
    @SerialName("ERROR") ERROR,
}

@Serializable
data class AnalysisTarget(
    val label: String,
    val centerX: Int,
    val centerY: Int,
    val normalizedX: Double,
    val normalizedY: Double,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/** Response contract. See power-platform/schemas/android-response.schema.json. */
@Serializable
data class AnalysisResponse(
    val requestId: String,
    val action: AnalysisAction,
    val confidence: Double,
    val target: AnalysisTarget?,
    val waitSeconds: Int,
    val reason: String,
    val warnings: List<String>,
    val screenshotHash: String,
) {
    companion object {
        fun error(requestId: String, screenshotHash: String, code: String, reason: String): AnalysisResponse =
            AnalysisResponse(
                requestId = requestId,
                action = AnalysisAction.ERROR,
                confidence = 0.0,
                target = null,
                waitSeconds = 0,
                reason = reason,
                warnings = listOf(code),
                screenshotHash = screenshotHash,
            )
    }
}

/**
 * Strict JSON: unknown keys, missing fields, wrong types or unknown enum values all fail
 * decoding, which the transport converts into ERROR / RESPONSE_UNPARSEABLE.
 */
val ScaJson: Json = Json {
    ignoreUnknownKeys = false
    isLenient = false
    explicitNulls = true
    encodeDefaults = true
    coerceInputValues = false
    allowSpecialFloatingPointValues = false
}
