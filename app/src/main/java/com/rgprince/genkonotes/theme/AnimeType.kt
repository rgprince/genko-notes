package com.rgprince.genkonotes.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Anime 2D type packs. Phone-only v1 ships with system-font stand-ins so the
 * build stays CI-safe without binary TTFs. Drop-in plan (res/font):
 *  PackA SHONEN -> Dela Gothic One (display) + M PLUS Rounded 1c (body)
 *  PackB SHOJO  -> Yusei Magic (display) + Zen Maru Gothic (body)
 * Swap FontFamily.Default below with FontFamily(Font(R.font.dela_gothic_one)) etc.
 */
enum class AnimePack(val label: String, val blurb: String) {
    SHONEN("Shonen", "Thick title-card display + rounded body"),
    SHOJO("Shojo Pop", "Marker handwritten display + soft maru body")
}

data class PapierType(
    val displayFamily: FontFamily,
    val bodyFamily: FontFamily,
    val monoFamily: FontFamily,
    val displaySize: TextUnit = 24.sp,
    val titleSize: TextUnit = 20.sp,
    val bodySize: TextUnit = 17.sp,
    val smallSize: TextUnit = 14.sp,
    val captionSize: TextUnit = 12.sp,
    val labelSize: TextUnit = 13.sp
)

fun typeForPack(pack: AnimePack): PapierType {
    return when (pack) {
        AnimePack.SHONEN -> PapierType(
            displayFamily = FontFamily.SansSerif,
            bodyFamily = FontFamily.SansSerif,
            monoFamily = FontFamily.Monospace
        )
        AnimePack.SHOJO -> PapierType(
            displayFamily = FontFamily.Cursive,
            bodyFamily = FontFamily.SansSerif,
            monoFamily = FontFamily.Monospace
        )
    }
}

fun displayWeightForPack(pack: AnimePack): FontWeight {
    return when (pack) {
        AnimePack.SHONEN -> FontWeight.Black
        AnimePack.SHOJO -> FontWeight.Bold
    }
}
