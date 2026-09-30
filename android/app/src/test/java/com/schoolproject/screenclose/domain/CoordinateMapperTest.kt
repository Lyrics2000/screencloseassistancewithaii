package com.schoolproject.screenclose.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateMapperTest {
    private val target = AnalysisTarget("X", 360, 784, 0.5, 0.5, 340, 764, 380, 804)

    @Test fun `scales sent-image coordinates to physical screen pixels`() {
        val s = CoordinateMapper.toScreen(target, sentWidth = 720, sentHeight = 1568, screenWidth = 1080, screenHeight = 2352)
        assertEquals(540f, s.x)
        assertEquals(1176f, s.y)
        assertEquals(510, s.left)
        assertEquals(1146, s.top)
        assertEquals(570, s.right)
        assertEquals(1206, s.bottom)
    }

    @Test fun `identity when no scaling happened`() {
        val s = CoordinateMapper.toScreen(target, 720, 1568, 720, 1568)
        assertEquals(360f, s.x)
        assertEquals(784f, s.y)
    }

    @Test fun `clamps to the last pixel`() {
        val edge = target.copy(centerX = 720, right = 720)
        val s = CoordinateMapper.toScreen(edge, 720, 1568, 1080, 2352)
        assertEquals(1079f, s.x)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects zero dimensions`() {
        CoordinateMapper.toScreen(target, 0, 1568, 1080, 2352)
    }
}
