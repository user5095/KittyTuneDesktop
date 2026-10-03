package com.alananasss.kittytune.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * pixelarticons (the library backing [PixelIconMap]) has no skip-next/skip-previous glyph, so this
 * draws one in the same idiom as its other icons: a blocky staircase triangle (no smooth curves)
 * plus a bar, instead of Material's smooth [androidx.compose.material.icons.Icons.Rounded.SkipNext].
 */
@Composable
fun PixelSkipIcon(forward: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val triangleWidth = size.width * 0.55f
        val barWidth = size.width * 0.18f
        val gap = size.width * 0.08f
        val barX = if (forward) triangleWidth + gap else 0f
        val triangleX = if (forward) 0f else barWidth + gap

        drawRect(
            color = tint,
            topLeft = Offset(barX, 0f),
            size = Size(barWidth, size.height),
        )
        drawStaircaseTriangle(
            pointRight = forward,
            xOffset = triangleX,
            width = triangleWidth,
            tint = tint,
        )
    }
}

private fun DrawScope.drawStaircaseTriangle(
    pointRight: Boolean,
    xOffset: Float,
    width: Float,
    tint: Color,
    steps: Int = 8,
) {
    val stepHeight = size.height / steps
    val half = (steps - 1) / 2f
    for (i in 0 until steps) {
        val distFromCenter = kotlin.math.abs(i - half)
        val widthFraction = (1f - distFromCenter / (steps / 2f)).coerceAtLeast(0.12f)
        val stepWidth = width * widthFraction
        val x = if (pointRight) xOffset else xOffset + (width - stepWidth)
        drawRect(
            color = tint,
            topLeft = Offset(x, i * stepHeight),
            size = Size(stepWidth, stepHeight + 0.5f),
        )
    }
}
