package com.schoolproject.screenclose.domain

import com.schoolproject.screenclose.core.ErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ResponseValidatorTest {
    private val validator = ResponseValidator()
    private val sent = SentFrame(requestId = "req-1", screenshotHash = "0123456789abcdef", width = 720, height = 1568)

    private fun close(
        cx: Int = 680, cy: Int = 60,
        left: Int = 660, top: Int = 40, right: Int = 700, bottom: Int = 80,
        confidence: Double = 0.95,
        nx: Double = cx / 720.0, ny: Double = cy / 1568.0,
        label: String = "X",
    ) = AnalysisResponse(
        requestId = "req-1",
        action = AnalysisAction.CLOSE,
        confidence = confidence,
        target = AnalysisTarget(label, cx, cy, nx, ny, left, top, right, bottom),
        waitSeconds = 0,
        reason = "ad with close",
        warnings = emptyList(),
        screenshotHash = "0123456789abcdef",
    )

    private fun assertError(r: AnalysisResponse, code: ErrorCode) {
        assertEquals(AnalysisAction.ERROR, r.action)
        assertNull(r.target)
        assertEquals(code.name, r.warnings.first())
        assertEquals(0.0, r.confidence, 0.0)
    }

    @Test fun `valid CLOSE passes unchanged`() {
        val r = close()
        assertSame(r, validator.validate(sent, r))
    }

    @Test fun `CLOSE below 0_90 becomes ERROR`() = assertError(validator.validate(sent, close(confidence = 0.89)), ErrorCode.CONFIDENCE_INVALID)

    @Test fun `CLOSE at exactly 0_90 passes`() {
        assertEquals(AnalysisAction.CLOSE, validator.validate(sent, close(confidence = 0.90)).action)
    }

    @Test fun `center outside width becomes ERROR`() =
        assertError(validator.validate(sent, close(cx = 720, left = 700, right = 720)), ErrorCode.TARGET_OUT_OF_BOUNDS)

    @Test fun `box outside height becomes ERROR`() =
        assertError(validator.validate(sent, close(cy = 1560, top = 1550, bottom = 1569)), ErrorCode.TARGET_OUT_OF_BOUNDS)

    @Test fun `inverted box becomes ERROR`() =
        assertError(validator.validate(sent, close(left = 700, right = 660)), ErrorCode.TARGET_OUT_OF_BOUNDS)

    @Test fun `center outside its own box becomes ERROR`() =
        assertError(validator.validate(sent, close(cx = 650)), ErrorCode.TARGET_OUT_OF_BOUNDS)

    @Test fun `normalized mismatch becomes ERROR`() =
        assertError(validator.validate(sent, close(nx = 0.5)), ErrorCode.NORMALIZED_MISMATCH)

    @Test fun `blank label becomes ERROR`() =
        assertError(validator.validate(sent, close(label = " ")), ErrorCode.TARGET_CONTRACT_VIOLATION)

    @Test fun `CLOSE without target becomes ERROR`() =
        assertError(validator.validate(sent, close().copy(target = null)), ErrorCode.TARGET_CONTRACT_VIOLATION)

    @Test fun `non-CLOSE with target becomes ERROR`() =
        assertError(validator.validate(sent, close().copy(action = AnalysisAction.NO_ACTION)), ErrorCode.TARGET_CONTRACT_VIOLATION)

    @Test fun `WAIT requires 1 to 60 seconds`() {
        val wait = close().copy(action = AnalysisAction.WAIT, target = null, waitSeconds = 0)
        assertError(validator.validate(sent, wait), ErrorCode.WAIT_SECONDS_INVALID)
        assertEquals(AnalysisAction.WAIT, validator.validate(sent, wait.copy(waitSeconds = 5)).action)
    }

    @Test fun `mismatched requestId or hash becomes ERROR`() {
        assertError(validator.validate(sent, close().copy(requestId = "other")), ErrorCode.RESPONSE_MISMATCH)
        assertError(validator.validate(sent, close().copy(screenshotHash = "ffffffffffffffff")), ErrorCode.RESPONSE_MISMATCH)
    }

    @Test fun `confidence outside 0 to 1 becomes ERROR`() =
        assertError(validator.validate(sent, close(confidence = 1.5)), ErrorCode.CONFIDENCE_INVALID)

    @Test fun `backend ERROR passes through`() {
        val err = AnalysisResponse.error("req-1", "0123456789abcdef", "PACKAGE_NOT_ALLOWED", "no")
        assertSame(err, validator.validate(sent, err))
    }

    @Test fun `blank reason becomes ERROR`() =
        assertError(validator.validate(sent, close().copy(reason = "")), ErrorCode.REASON_MISSING)

    @Test(expected = IllegalArgumentException::class)
    fun `threshold cannot be lowered`() {
        ResponseValidator(minConfidence = 0.5)
    }
}
