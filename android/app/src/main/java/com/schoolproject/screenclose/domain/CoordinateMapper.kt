package com.schoolproject.screenclose.domain

import kotlin.math.roundToInt

/** A validated target expressed in physical screen pixels (what dispatchGesture expects). */
data class ScreenTarget(
    val x: Float,
    val y: Float,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val label: String,
)

/**
 * Maps coordinates from the downscaled image that was sent to the model back to physical
 * screen pixels. The image was produced by uniformly scaling the full-screen capture, so both
 * axes use their own ratio (they are equal up to rounding).
 */
object CoordinateMapper {
    fun toScreen(target: AnalysisTarget, sentWidth: Int, sentHeight: Int, screenWidth: Int, screenHeight: Int): ScreenTarget {
        require(sentWidth > 0 && sentHeight > 0 && screenWidth > 0 && screenHeight > 0) { "dimensions must be positive" }
        val sx = screenWidth.toDouble() / sentWidth
        val sy = screenHeight.toDouble() / sentHeight
        fun mx(v: Int) = (v * sx).roundToInt().coerceIn(0, screenWidth - 1)
        fun my(v: Int) = (v * sy).roundToInt().coerceIn(0, screenHeight - 1)
        return ScreenTarget(
            x = mx(target.centerX).toFloat(),
            y = my(target.centerY).toFloat(),
            left = mx(target.left),
            top = my(target.top),
            right = mx(target.right),
            bottom = my(target.bottom),
            label = target.label,
        )
    }
}
