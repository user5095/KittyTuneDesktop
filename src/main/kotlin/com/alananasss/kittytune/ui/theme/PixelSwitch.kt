package com.alananasss.kittytune.ui.theme

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * Drop-in replacement for [androidx.compose.material3.Switch]: same pill track / round thumb
 * silhouette as the real M3 Switch, but rendered with [PixelCapsuleShape] (blocky/stepped edges)
 * instead of a smooth anti-aliased curve, since M3's Switch hardcodes that curve internally and
 * ignores the app's [androidx.compose.material3.Shapes]. Colors still come from the passed-in
 * [colors] (or the caller's [MaterialTheme] via [SwitchDefaults.colors]) so this respects whatever
 * palette the user has picked, same as the regular switch would.
 */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    thumbContent: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
) {
    if (!LocalPixelTheme.current) {
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            thumbContent = thumbContent,
            enabled = enabled,
            colors = colors,
            interactionSource = interactionSource,
        )
        return
    }

    val trackWidth = 44.dp
    val trackHeight = 24.dp
    val thumbSize = 18.dp
    val thumbPadding = 3.dp

    val trackColor = if (checked) colors.checkedTrackColor else colors.uncheckedTrackColor
    val thumbColor = if (checked) colors.checkedThumbColor else colors.uncheckedThumbColor
    val borderColor = if (checked) colors.checkedBorderColor else colors.uncheckedBorderColor

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbSize - thumbPadding else thumbPadding,
        label = "PixelSwitchThumb",
    )

    val actualInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val trackShape = remember { PixelCapsuleShape() }
    val thumbShape = remember { PixelCapsuleShape() }

    Box(
        modifier = modifier
            .size(width = trackWidth, height = trackHeight)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = actualInteractionSource,
                indication = null,
                onValueChange = { onCheckedChange?.invoke(it) },
            )
            .background(trackColor, trackShape)
            .border(2.dp, borderColor, trackShape),
    ) {
        Box(
            modifier = Modifier
                .padding(start = thumbOffset)
                .size(thumbSize)
                .align(Alignment.CenterStart)
                .background(thumbColor, thumbShape),
            contentAlignment = Alignment.Center,
        ) {
            thumbContent?.invoke()
        }
    }
}
