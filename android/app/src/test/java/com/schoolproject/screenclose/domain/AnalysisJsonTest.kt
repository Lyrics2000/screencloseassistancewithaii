package com.schoolproject.screenclose.domain

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisJsonTest {
    private val closeJson = """
        {"requestId":"r","action":"CLOSE","confidence":0.95,
         "target":{"label":"X","centerX":10,"centerY":20,"normalizedX":0.1,"normalizedY":0.2,"left":5,"top":15,"right":15,"bottom":25},
         "waitSeconds":0,"reason":"ok","warnings":[],"screenshotHash":"h"}
    """.trimIndent()

    @Test fun `decodes the documented contract`() {
        val r = ScaJson.decodeFromString(AnalysisResponse.serializer(), closeJson)
        assertEquals(AnalysisAction.CLOSE, r.action)
        assertEquals(10, r.target?.centerX)
    }

    @Test fun `decodes null target`() {
        val json = """{"requestId":"r","action":"NO_ACTION","confidence":0.9,"target":null,"waitSeconds":0,"reason":"none","warnings":[],"screenshotHash":"h"}"""
        assertNull(ScaJson.decodeFromString(AnalysisResponse.serializer(), json).target)
    }

    @Test(expected = SerializationException::class)
    fun `rejects unknown fields`() {
        ScaJson.decodeFromString(AnalysisResponse.serializer(), closeJson.replace("\"reason\"", "\"extra\":1,\"reason\""))
    }

    @Test(expected = SerializationException::class)
    fun `rejects missing target key`() {
        ScaJson.decodeFromString(
            AnalysisResponse.serializer(),
            """{"requestId":"r","action":"NO_ACTION","confidence":0.9,"waitSeconds":0,"reason":"none","warnings":[],"screenshotHash":"h"}""",
        )
    }

    @Test(expected = SerializationException::class)
    fun `rejects unknown action`() {
        ScaJson.decodeFromString(AnalysisResponse.serializer(), closeJson.replace("\"CLOSE\"", "\"CLICK\""))
    }

    @Test(expected = SerializationException::class)
    fun `rejects markdown wrapped json`() {
        ScaJson.decodeFromString(AnalysisResponse.serializer(), "```json\n$closeJson\n```")
    }

    @Test fun `request encodes every field including requestedAction`() {
        val req = AnalysisRequest("r", "s", "/9j/", "image/jpeg", 10, 20, "0000000000000000", "p")
        val json = ScaJson.encodeToString(AnalysisRequest.serializer(), req)
        assertTrue(json.contains("\"requestedAction\":\"FIND_AD_CLOSE\""))
        assertTrue(json.contains("\"sessionId\":\"s\""))
    }
}
