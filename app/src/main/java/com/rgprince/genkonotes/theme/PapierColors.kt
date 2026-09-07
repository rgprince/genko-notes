package com.rgprince.genkonotes.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class PapierColors(
    val paper: Color,
    val paperRaised: Color,
    val paperSunken: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val line: Color,
    val accent: Color,
    val accentInk: Color,
    val moss: Color,
    val dandelion: Color,
    val berry: Color,
    val washBlue: Color,
    val washRose: Color,
    val washMint: Color,
    val washLilac: Color,
    /** Hard offset shadow color (never the ink — cream glows in dark mode). */
    val shadow: Color,
    /** Text on danger fills (white in light, dark paper on salmon in dark). */
    val onDanger: Color,
    val isDark: Boolean
)

val PaperLightColors = PapierColors(
    paper = Color(0xFFFAF6EE),
    paperRaised = Color(0xFFFFFFFF),
    paperSunken = Color(0xFFEFE9DB),
    ink = Color(0xFF1C1A15),
    inkSoft = Color(0xFF5B564A),
    inkFaint = Color(0xFF8A8474),
    line = Color(0xFFD9D1BE),
    accent = Color(0xFFC4501A),
    accentInk = Color(0xFFFFFFFF),
    moss = Color(0xFF4A6741),
    dandelion = Color(0xFFE9B44C),
    berry = Color(0xFFB3261E),
    washBlue = Color(0xFFDCE6E8),
    washRose = Color(0xFFF2DDD6),
    washMint = Color(0xFFDDE8D5),
    washLilac = Color(0xFFE2DDEF),
    shadow = Color(0xFF1C1A15),
    onDanger = Color(0xFFFFFFFF),
    isDark = false
)

val InkDarkColors = PapierColors(
    paper = Color(0xFF16140F),
    paperRaised = Color(0xFF201D16),
    paperSunken = Color(0xFF0E0D0A),
    ink = Color(0xFFF2EDE0),
    inkSoft = Color(0xFFB8B0A0),
    inkFaint = Color(0xFF877F6E),
    line = Color(0xFF3A352A),
    accent = Color(0xFFE98A3D),
    accentInk = Color(0xFF1C1A15),
    moss = Color(0xFF9AB88E),
    dandelion = Color(0xFFE9B44C),
    berry = Color(0xFFFF8A80),
    washBlue = Color(0xFF34474B),
    washRose = Color(0xFF5A3A34),
    washMint = Color(0xFF3A4A34),
    washLilac = Color(0xFF464058),
    shadow = Color(0xD9000000),
    onDanger = Color(0xFF16140F),
    isDark = true
)
