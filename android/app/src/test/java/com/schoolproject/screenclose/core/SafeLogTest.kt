package com.schoolproject.screenclose.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeLogTest {

    @Test
    fun `redacts bearer tokens, jwt, anthropic keys, sas signatures and base64 blobs`() {
        val blob = "A".repeat(500)
        val jwt = "eyJhbGciOiJSUzI1NiJ9.eyJhdWQiOiJodHRwcyJ9.c2lnbmF0dXJlc2lnbmF0dXJl"
        val input = "Authorization: Bearer abc.def.ghi key=sk-ant-api03-SECRET url=https://x/y?sig=SECRET123&sp=1 img=$blob $jwt"
        val out = SafeLog.redact(input)
        assertFalse(out.contains("SECRET"))
        assertFalse(out.contains("abc.def.ghi"))
        assertFalse(out.contains(blob))
        assertFalse(out.contains(jwt))
        assertTrue(out.contains("[REDACTED_BLOB]"))
    }

    @Test
    fun `sensitive field names are always redacted`() {
        val line = SafeLog.format("evt", mapOf("screenshotBase64" to "short", "Authorization" to "x", "requestId" to "r-1"))
        assertFalse(line.contains("short"))
        assertTrue(line.contains("requestId=r-1"))
    }

    @Test
    fun `analysis request toString never contains image bytes`() {
        val req = com.schoolproject.screenclose.domain.AnalysisRequest(
            requestId = "r", sessionId = "s", screenshotBase64 = "/9j/SECRETPIXELS", mimeType = "image/jpeg",
            screenshotWidth = 10, screenshotHeight = 10, screenshotHash = "0000000000000000", foregroundPackage = "p",
        )
        assertFalse(req.toString().contains("SECRETPIXELS"))
    }
}
