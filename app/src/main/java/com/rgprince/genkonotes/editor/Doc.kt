package com.rgprince.genkonotes.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import kotlinx.serialization.Serializable

/** Highlight ink colors for the anime marker system. */
enum class HighlightInk(val argb: Long, val label: String) {
    DANDELION(0x52E9B44C, "Dandelion"),
    ROSE(0x52F2B8A0, "Rose"),
    MINT(0x52A8D5A0, "Mint"),
    LILAC(0x52B8A0E8, "Lilac"),
    NONE(0x00000000, "None");

    fun toColor(): Color = Color(argb)
}

/** Single styled run — the doc model never stores markdown symbols. */
@Serializable
data class InlineRun(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    val highlight: String = "NONE",
    val code: Boolean = false,
    val head: String = "BODY"
)

/** Span attribute vocabulary shared by the live editor state and persistence. */
enum class Attr { BOLD, ITALIC, UNDERLINE, STRIKE, CODE, HIGHLIGHT, HEAD1, HEAD2 }

val UL_AND_STRIKE =
    TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))

val HEAD1_SIZE = 22.sp
val HEAD2_SIZE = 19.sp

fun isMarkerColor(color: Color): Boolean {
    if (color == Color.Unspecified) return false
    return HighlightInk.entries.any { it != HighlightInk.NONE && it.toColor() == color }
}

fun attrsOf(style: SpanStyle): Set<Attr> {
    val out = mutableSetOf<Attr>()
    if (style.fontStyle == FontStyle.Italic) out.add(Attr.ITALIC)
    val td = style.textDecoration
    if (td == TextDecoration.Underline || td == UL_AND_STRIKE) out.add(Attr.UNDERLINE)
    if (td == TextDecoration.LineThrough || td == UL_AND_STRIKE) out.add(Attr.STRIKE)
    if (style.fontFamily == FontFamily.Monospace) out.add(Attr.CODE)
    if (isMarkerColor(style.background)) out.add(Attr.HIGHLIGHT)
    if (style.fontSize == HEAD1_SIZE) {
        out.add(Attr.HEAD1)
        out.add(Attr.BOLD)
    } else if (style.fontSize == HEAD2_SIZE) {
        out.add(Attr.HEAD2)
        out.add(Attr.BOLD)
    } else if (style.fontWeight == FontWeight.Bold) {
        out.add(Attr.BOLD)
    }
    return out
}

fun stripAttr(style: SpanStyle, attr: Attr): SpanStyle? {
    var out = style
    when (attr) {
        Attr.BOLD -> if (style.fontWeight == FontWeight.Bold && style.fontSize != HEAD1_SIZE && style.fontSize != HEAD2_SIZE) {
            out = out.copy(fontWeight = null)
        }
        Attr.ITALIC -> if (style.fontStyle == FontStyle.Italic) out = out.copy(fontStyle = null)
        Attr.UNDERLINE -> out = when (style.textDecoration) {
            TextDecoration.Underline -> out.copy(textDecoration = null)
            UL_AND_STRIKE -> out.copy(textDecoration = TextDecoration.LineThrough)
            else -> out
        }
        Attr.STRIKE -> out = when (style.textDecoration) {
            TextDecoration.LineThrough -> out.copy(textDecoration = null)
            UL_AND_STRIKE -> out.copy(textDecoration = TextDecoration.Underline)
            else -> out
        }
        Attr.HIGHLIGHT -> if (isMarkerColor(style.background)) out = out.copy(background = Color.Unspecified)
        Attr.CODE -> {
            if (style.fontFamily == FontFamily.Monospace) out = out.copy(fontFamily = null)
            if (out.background != Color.Unspecified && !isMarkerColor(out.background)) {
                out = out.copy(background = Color.Unspecified)
            }
        }
        Attr.HEAD1 -> if (style.fontSize == HEAD1_SIZE) {
            out = out.copy(fontSize = androidx.compose.ui.unit.TextUnit.Unspecified, fontWeight = null)
        }
        Attr.HEAD2 -> if (style.fontSize == HEAD2_SIZE) {
            out = out.copy(fontSize = androidx.compose.ui.unit.TextUnit.Unspecified, fontWeight = null)
        }
    }
    return if (out == SpanStyle()) null else out
}

/** Bare style (no text color — inherits the field style) for save/load round-trips. */
fun InlineRun.toBareSpanStyle(codeBg: Color): SpanStyle {
    var style = SpanStyle()
    if (bold) style = style.copy(fontWeight = FontWeight.Bold)
    if (italic) style = style.copy(fontStyle = FontStyle.Italic)
    if (underline && strike) style = style.copy(textDecoration = UL_AND_STRIKE)
    else if (underline) style = style.copy(textDecoration = TextDecoration.Underline)
    else if (strike) style = style.copy(textDecoration = TextDecoration.LineThrough)
    val hl = try { HighlightInk.valueOf(highlight) } catch (_: Exception) { HighlightInk.NONE }
    if (hl != HighlightInk.NONE) style = style.copy(background = hl.toColor())
    if (code) {
        style = style.copy(
            fontFamily = FontFamily.Monospace,
            background = if (hl != HighlightInk.NONE) hl.toColor() else codeBg
        )
    }
    when (head) {
        "H1" -> style = style.copy(fontSize = HEAD1_SIZE, fontWeight = FontWeight.Bold)
        "H2" -> style = style.copy(fontSize = HEAD2_SIZE, fontWeight = FontWeight.Bold)
    }
    return style
}

/** Flatten a live AnnotatedString into persistable runs (merges adjacent twins). */
fun AnnotatedString.toRuns(): List<InlineRun> {
    if (text.isEmpty()) return emptyList()
    val cuts = sortedSetOf(0, text.length)
    spanStyles.forEach { r ->
        cuts.add(r.start.coerceIn(0, text.length))
        cuts.add(r.end.coerceIn(0, text.length))
    }
    val pts = cuts.toList()
    val out = mutableListOf<InlineRun>()
    for (i in 0 until pts.size - 1) {
        val a = pts[i]
        val b = pts[i + 1]
        if (a >= b) continue
        val covering = spanStyles.filter { it.start <= a && it.end >= b }
        var bold = false
        var italic = false
        var ul = false
        var st = false
        var code = false
        var hlName = "NONE"
        var head = "BODY"
        covering.forEach { r ->
            val at = attrsOf(r.item)
            if (at.contains(Attr.BOLD)) bold = true
            if (at.contains(Attr.ITALIC)) italic = true
            if (at.contains(Attr.UNDERLINE)) ul = true
            if (at.contains(Attr.STRIKE)) st = true
            if (at.contains(Attr.CODE)) code = true
            if (at.contains(Attr.HIGHLIGHT)) {
                HighlightInk.entries.firstOrNull { it.toColor() == r.item.background }
                    ?.let { hlName = it.name }
            }
            if (at.contains(Attr.HEAD1)) head = "H1"
            else if (at.contains(Attr.HEAD2) && head == "BODY") head = "H2"
        }
        val run = InlineRun(text.substring(a, b), bold, italic, ul, st, hlName, code, head)
        val last = out.lastOrNull()
        if (last != null && last.bold == bold && last.italic == italic &&
            last.underline == ul && last.strike == st && last.highlight == hlName &&
            last.code == code && last.head == head
        ) {
            out[out.lastIndex] = last.copy(text = last.text + run.text)
        } else {
            out.add(run)
        }
    }
    return out
}

/** Rebuild a live AnnotatedString from persisted runs. */
fun List<InlineRun>.toAnnotated(codeBg: Color): AnnotatedString {
    return buildAnnotatedString {
        forEach { run ->
            val s = run.toBareSpanStyle(codeBg)
            if (s == SpanStyle()) append(run.text)
            else {
                pushStyle(s)
                append(run.text)
                pop()
            }
        }
    }
}
