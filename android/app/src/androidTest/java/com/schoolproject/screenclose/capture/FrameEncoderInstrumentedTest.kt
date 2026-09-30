package com.schoolproject.screenclose.capture

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.schoolproject.screenclose.core.ScaException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Base64

/** Runs on a device/emulator: real Bitmap scaling, JPEG encoding and hashing. */
@RunWith(AndroidJUnit4::class)
class FrameEncoderInstrumentedTest {

    private fun screen(width: Int = 1080, height: Int = 2400): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.DKGRAY)
        canvas.drawRect(width - 150f, 100f, width - 50f, 200f, Paint().apply { color = Color.WHITE })
        return bmp
    }

    @Test
    fun downscalesToLongEdgeAndProducesDecodableJpeg() {
        val encoded = FrameEncoder(maxLongEdge = 1568, jpegQuality = 80, maxEncodedBytes = 3_000_000).encode(screen())
        assertEquals("image/jpeg", encoded.mimeType)
        assertEquals(1568, maxOf(encoded.width, encoded.height))
        assertEquals(706, encoded.width)
        val bytes = Base64.getDecoder().decode(encoded.base64)
        assertTrue(encoded.base64.startsWith("/9j/"))
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertEquals(encoded.width, decoded.width)
        assertEquals(encoded.height, decoded.height)
    }

    @Test
    fun hashIsStableForIdenticalFrames() {
        val encoder = FrameEncoder(1568, 80, 3_000_000)
        assertEquals(encoder.encode(screen()).hash, encoder.encode(screen()).hash)
    }

    @Test(expected = ScaException::class)
    fun rejectsImagesAboveTheByteBudget() {
        FrameEncoder(1568, 95, maxEncodedBytes = 1_000).encode(screen())
    }
}
