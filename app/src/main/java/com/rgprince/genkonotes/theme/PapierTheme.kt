package com.rgprince.genkonotes.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

val LocalPapierColors = compositionLocalOf { PaperLightColors }
val LocalPapierTypePack = compositionLocalOf { AnimePack.SHONEN }
val LocalFontScale = compositionLocalOf { 1.0f }

object PapierTheme {
    val colors: PapierColors
        @Composable get() = LocalPapierColors.current
    val pack: AnimePack
        @Composable get() = LocalPapierTypePack.current
    val fontScale: Float
        @Composable get() = LocalFontScale.current
    val type: PapierType
        @Composable get() {
            val pack = LocalPapierTypePack.current
            return remember(pack) { typeForPack(pack) }
        }
}

@Composable
fun NijiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pack: AnimePack = AnimePack.SHONEN,
    fontScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) InkDarkColors else PaperLightColors
    CompositionLocalProvider(
        LocalPapierColors provides colors,
        LocalPapierTypePack provides pack,
        LocalFontScale provides fontScale,
        content = content
    )
}

fun Color.withAlphaFraction(fraction: Float): Color {
    return copy(alpha = fraction)
}
