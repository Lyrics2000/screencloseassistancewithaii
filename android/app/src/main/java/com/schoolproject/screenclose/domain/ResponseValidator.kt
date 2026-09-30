package com.schoolproject.screenclose.domain

import com.schoolproject.screenclose.core.ErrorCode
import kotlin.math.abs

/** Facts about the screenshot that was actually sent; the response must be consistent with them. */
data class SentFrame(
    val requestId: String,
    val screenshotHash: String,
    val width: Int,
    val height: Int,
)

/**
 * Client-side re-validation of the flow's response (defence in depth, ADR-003). Mirrors
 * docs/10-validation-conditions.md sections D and E. Any violation converts the response to
 * ERROR with the violated rule's code in warnings[0]; nothing is "repaired".
 */
class ResponseValidator(
    private val minConfidence: Double = MIN_CONFIDENCE,
    private val normalizedTolerance: Double = 0.01,
) {
    init {
        require(minConfidence >= MIN_CONFIDENCE) { "minConfidence can only be raised above $MIN_CONFIDENCE" }
    }

    fun validate(sent: SentFrame, response: AnalysisResponse): AnalysisResponse {
        val violation = findViolation(sent, response) ?: return response
        return AnalysisResponse.error(
            requestId = sent.requestId,
            screenshotHash = sent.screenshotHash,
            code = violation.first.name,
            reason = violation.second,
        )
    }

    private fun findViolation(sent: SentFrame, r: AnalysisResponse): Pair<ErrorCode, String>? {
        if (r.requestId != sent.requestId || r.screenshotHash != sent.screenshotHash) {
            return ErrorCode.RESPONSE_MISMATCH to "Response does not echo the requestId/screenshotHash that was sent."
        }
        if (r.confidence.isNaN() || r.confidence < 0.0 || r.confidence > 1.0) {
            return ErrorCode.CONFIDENCE_INVALID to "Confidence outside 0..1."
        }
        if (r.waitSeconds !in 0..60) {
            return ErrorCode.WAIT_SECONDS_INVALID to "waitSeconds outside 0..60."
        }
        if (r.action == AnalysisAction.ERROR) {
            // Backend errors are passed through unchanged but must still honour the target contract.
            return if (r.target != null) ErrorCode.TARGET_CONTRACT_VIOLATION to "ERROR response carried a target." else null
        }
        if (r.reason.isBlank()) {
            return ErrorCode.REASON_MISSING to "Response reason is blank."
        }
        if (r.action != AnalysisAction.CLOSE) {
            if (r.target != null) return ErrorCode.TARGET_CONTRACT_VIOLATION to "Target must be null for ${r.action}."
            if (r.action == AnalysisAction.WAIT && r.waitSeconds !in 1..60) {
                return ErrorCode.WAIT_SECONDS_INVALID to "WAIT requires waitSeconds within 1..60."
            }
            return null
        }
        val t = r.target ?: return ErrorCode.TARGET_CONTRACT_VIOLATION to "CLOSE requires a target."
        if (r.confidence < minConfidence) {
            return ErrorCode.CONFIDENCE_INVALID to "CLOSE returned with confidence below $minConfidence."
        }
        if (t.label.isBlank()) return ErrorCode.TARGET_CONTRACT_VIOLATION to "CLOSE target label is blank."
        val w = sent.width
        val h = sent.height
        val boxOk = t.left >= 0 && t.top >= 0 && t.left < t.right && t.top < t.bottom && t.right <= w && t.bottom <= h
        val centerOk = t.centerX in 0 until w && t.centerY in 0 until h &&
            t.centerX in t.left..t.right && t.centerY in t.top..t.bottom
        if (!boxOk || !centerOk) {
            return ErrorCode.TARGET_OUT_OF_BOUNDS to "Target lies outside the ${w}x$h screenshot or its box."
        }
        val expectedX = t.centerX.toDouble() / w
        val expectedY = t.centerY.toDouble() / h
        if (abs(t.normalizedX - expectedX) > normalizedTolerance || abs(t.normalizedY - expectedY) > normalizedTolerance) {
            return ErrorCode.NORMALIZED_MISMATCH to "Normalized coordinates disagree with pixel coordinates."
        }
        return null
    }

    companion object {
        const val MIN_CONFIDENCE = 0.90
    }
}
