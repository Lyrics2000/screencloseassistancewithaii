package com.schoolproject.screenclose.domain

/**
 * Ports used by [SessionController]. Android implementations live in capture/, network/ and
 * accessibility/; unit tests supply fakes (ADR-005).
 */

/** A captured, downscaled, encoded frame. Bytes live only in memory and are dropped after the request. */
class EncodedFrame(
    val base64: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val screenWidth: Int,
    val screenHeight: Int,
    val hash: Long,
    val foregroundPackage: String,
    val capturedAtMillis: Long,
) {
    override fun toString(): String =
        "EncodedFrame(${width}x$height from ${screenWidth}x$screenHeight, hash=${PerceptualHashHex.toHex(hash)}, package=$foregroundPackage)"
}

fun interface ScreenshotSource {
    /** Captures one frame with the overlay hidden. Throws ScaException on failure. */
    suspend fun capture(): EncodedFrame
}

fun interface AnalysisTransport {
    /** Never throws for transport or protocol failures; returns an ERROR response instead. */
    suspend fun analyze(request: AnalysisRequest): AnalysisResponse
}

fun interface GestureDispatcher {
    /** Performs exactly one tap at physical screen coordinates. Returns true when the system completed it. */
    suspend fun tap(x: Float, y: Float): Boolean
}

fun interface ForegroundPackageProvider {
    fun currentPackage(): String?
}

object PerceptualHashHex {
    fun toHex(hash: Long): String = java.lang.Long.toUnsignedString(hash, 16).padStart(16, '0')
}
