package com.alananasss.kittytune.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/** True while the YTMusicPixelated pixel theme is active; read by [com.alananasss.kittytune.ui.icons.Icon] and [Switch]. */
val LocalPixelTheme = androidx.compose.runtime.compositionLocalOf { false }

/** Only typography is fixed here: colors and shapes stay whatever the user already picked. */
val PixelFontFamily: FontFamily by lazy {
    val bytes = object {}.javaClass.getResourceAsStream("/fonts/lores9ot.ttf")!!.readBytes()
    FontFamily(Font(identity = "lores9ot", data = bytes, weight = FontWeight.Normal))
}

val PixelTypography: Typography by lazy {
    val base = Typography()
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = PixelFontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = PixelFontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = PixelFontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = PixelFontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = PixelFontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = PixelFontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = PixelFontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = PixelFontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = PixelFontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = PixelFontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = PixelFontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = PixelFontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = PixelFontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = PixelFontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = PixelFontFamily),
    )
}
