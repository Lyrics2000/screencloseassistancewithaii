package com.schoolproject.screenclose.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PerceptualHashTest {

    private fun gradient(width: Int, height: Int, leftToRightDarker: Boolean): IntArray =
        IntArray(width * height) { i ->
            val x = i % width
            val v = if (leftToRightDarker) 255 - x * 255 / (width - 1) else x * 255 / (width - 1)
            (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }

    @Test
    fun `left-bright gradient sets every bit`() {
        val hash = PerceptualHash.dHash(gradient(90, 80, leftToRightDarker = true), 90, 80)
        assertEquals(-1L, hash)
    }

    @Test
    fun `left-dark gradient clears every bit`() {
        val hash = PerceptualHash.dHash(gradient(90, 80, leftToRightDarker = false), 90, 80)
        assertEquals(0L, hash)
    }

    @Test
    fun `small noise keeps hamming distance low`() {
        val base = gradient(90, 80, leftToRightDarker = true)
        val noisy = base.copyOf().also { px ->
            for (i in px.indices step 37) {
                val v = ((px[i] and 0xFF) - 3).coerceAtLeast(0)
                px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
        }
        val d = PerceptualHash.hamming(PerceptualHash.dHash(base, 90, 80), PerceptualHash.dHash(noisy, 90, 80))
        assertTrue("distance was $d", d <= 4)
    }

    @Test
    fun `different images produce different hashes`() {
        val a = PerceptualHash.dHash(gradient(90, 80, true), 90, 80)
        val b = PerceptualHash.dHash(gradient(90, 80, false), 90, 80)
        assertNotEquals(a, b)
        assertEquals(64, PerceptualHash.hamming(a, b))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects images smaller than the grid`() {
        PerceptualHash.dHash(IntArray(8 * 8), 8, 8)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects mismatched pixel array`() {
        PerceptualHash.dHash(IntArray(10), 90, 80)
    }
}
