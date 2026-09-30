package com.schoolproject.screenclose.capture

import android.graphics.Bitmap
import com.schoolproject.screenclose.core.ErrorCode
import com.schoolproject.screenclose.core.ScaException
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.math.max
import kotlin.math.roundToInt

/** Result of encoding one screen capture in memory. Nothing is written to disk (requirement: no persistence). */
class EncodedImage(
    val base64: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val hash: Long,
)

/**
 * Downscales so the long edge is <= maxLongEdge (keeps model coordinates 1:1 with the pixels
 * sent, see docs/10 rule A11), computes the dHash, JPEG-encodes and base64-encodes.
 */
class FrameEncoder(
    private val maxLongEdge: Int,
    private val jpegQuality: Int,
    private val maxEncodedBytes: Int,
) {
    fun encode(source: Bitmap): EncodedImage {
        val scaled = scaleToLongEdge(source)
        try {
            val hash = hashOf(scaled)
            val bytes = ByteArrayOutputStream(scaled.width * scaled.height / 4).use { out ->
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, out)) {
                    throw ScaException(ErrorCode.ENCODE_FAILED, "JPEG compression returned false")
                }
                out.toByteArray()
            }
            if (bytes.size > maxEncodedBytes) {
                throw ScaException(ErrorCode.IMAGE_TOO_LARGE, "encoded image ${bytes.size} B exceeds $maxEncodedBytes B")
            }
            val b64 = Base64.getEncoder().encodeToString(bytes)
            bytes.fill(0)
            return EncodedImage(b64, "image/jpeg", scaled.width, scaled.height, hash)
        } finally {
            if (scaled !== source) scaled.recycle()
        }
    }

    private fun scaleToLongEdge(src: Bitmap): Bitmap {
        val longEdge = max(src.width, src.height)
        if (longEdge <= maxLongEdge) return src
        val ratio = maxLongEdge.toDouble() / longEdge
        val w = max(1, (src.width * ratio).roundToInt())
        val h = max(1, (src.height * ratio).roundToInt())
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    private fun hashOf(bmp: Bitmap): Long {
        val small = Bitmap.createScaledBitmap(bmp, 90, 80, true)
        try {
            val pixels = IntArray(small.width * small.height)
            small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
            return PerceptualHash.dHash(pixels, small.width, small.height)
        } finally {
            if (small !== bmp) small.recycle()
        }
    }
}
