package com.alananasss.kittytune.ui.theme

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Same silhouette as [androidx.compose.foundation.shape.CircleShape]/a full pill (default, no
 * [cornerRadius]) or a [androidx.compose.foundation.shape.RoundedCornerShape] of [cornerRadius]
 * (clamped to half the shorter side, same as RoundedCornerShape does), but built from stacked
 * rectangles instead of a smooth arc/curve, so round controls (play/pause, prev/next, switch
 * track+thumb, toggle pills) read as "pixel-art round" instead of anti-aliased round, without
 * changing their outer form to a square.
 */
class PixelCapsuleShape(
    private val cornerRadius: Dp? = null,
    private val rows: Int = 10,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height
        val maxRadius = min(w, h) / 2f
        if (maxRadius <= 0f) return Outline.Rectangle(Rect(0f, 0f, w, h))
        val radius = cornerRadius?.let { with(density) { it.toPx() } }?.coerceAtMost(maxRadius) ?: maxRadius

        val stepCount = rows.coerceAtLeast(2)
        val rowHeight = h / stepCount
        val path = Path()

        for (i in 0 until stepCount) {
            val rowTop = i * rowHeight
            val rowCenterY = rowTop + rowHeight / 2f
            val dy = when {
                rowCenterY < radius -> radius - rowCenterY
                rowCenterY > h - radius -> rowCenterY - (h - radius)
                else -> 0f
            }
            val inset = if (dy >= radius) radius else radius - sqrt(radius * radius - dy * dy)
            val left = inset
            val right = w - inset
            if (right <= left) continue
            path.addRect(Rect(left, rowTop, right, rowTop + rowHeight))
        }
        return Outline.Generic(path)
    }
}
