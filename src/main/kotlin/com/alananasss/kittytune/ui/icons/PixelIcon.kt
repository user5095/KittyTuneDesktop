package com.alananasss.kittytune.ui.icons

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import com.alananasss.kittytune.ui.theme.LocalPixelTheme

/**
 * Drop-in replacement for [androidx.compose.material3.Icon]: when the pixel theme is active and this vector
 * has a pixelarticons equivalent in [PixelIconMap], draws that instead. Files opt in by importing
 * `com.alananasss.kittytune.ui.icons.Icon` next to their `androidx.compose.material3.*` import.
 */
@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val pixelPath = if (LocalPixelTheme.current) PixelIconMap[imageVector.name.substringAfterLast('.')] else null
    if (pixelPath != null) {
        androidx.compose.material3.Icon(painter = painterResource(pixelPath), contentDescription = contentDescription, modifier = modifier, tint = tint)
    } else {
        androidx.compose.material3.Icon(imageVector = imageVector, contentDescription = contentDescription, modifier = modifier, tint = tint)
    }
}

@Composable
fun Icon(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    androidx.compose.material3.Icon(painter = painter, contentDescription = contentDescription, modifier = modifier, tint = tint)
}
