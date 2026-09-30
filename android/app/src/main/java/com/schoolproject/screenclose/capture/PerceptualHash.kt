package com.schoolproject.screenclose.capture

/**
 * 64-bit difference hash (dHash), ADR-006. Pure Kotlin on ARGB_8888 pixels so it can be unit
 * tested on the JVM. The image is box-averaged into a 9x8 luminance grid; bit (row*8+col) is 1
 * when cell(col) is brighter than cell(col+1).
 */
object PerceptualHash {
    private const val GRID_W = 9
    private const val GRID_H = 8

    fun dHash(argb: IntArray, width: Int, height: Int): Long {
        require(width >= GRID_W && height >= GRID_H) { "image must be at least ${GRID_W}x$GRID_H" }
        require(argb.size == width * height) { "pixel array size mismatch" }
        val grid = DoubleArray(GRID_W * GRID_H)
        for (gy in 0 until GRID_H) {
            val y0 = gy * height / GRID_H
            val y1 = maxOf(y0 + 1, (gy + 1) * height / GRID_H)
            for (gx in 0 until GRID_W) {
                val x0 = gx * width / GRID_W
                val x1 = maxOf(x0 + 1, (gx + 1) * width / GRID_W)
                var sum = 0.0
                for (y in y0 until y1) {
                    val row = y * width
                    for (x in x0 until x1) sum += luminance(argb[row + x])
                }
                grid[gy * GRID_W + gx] = sum / ((y1 - y0) * (x1 - x0))
            }
        }
        var hash = 0L
        var bit = 0
        for (gy in 0 until GRID_H) {
            for (gx in 0 until GRID_W - 1) {
                if (grid[gy * GRID_W + gx] > grid[gy * GRID_W + gx + 1]) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    fun hamming(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    private fun luminance(p: Int): Double {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        return 0.299 * r + 0.587 * g + 0.114 * b
    }
}
