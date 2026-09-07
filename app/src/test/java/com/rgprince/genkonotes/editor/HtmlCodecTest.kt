package com.rgprince.genkonotes.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests for the styled clipboard codec. Copy must emit real inline tags
 * (not text-in-a-p) and the subset parser must rebuild the same attrs,
 * so italic/highlight survive copy -> new-note paste.
 */
class HtmlCodecTest {

    @Test
    fun italicRoundTrip() {
        val ann = buildAnnotatedString {
            append("plain ")
            pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
            append("hi")
            pop()
        }
        val html = annotatedToHtml(ann)
        assertTrue("copy must emit an italic tag, got: $html", html.contains("<i>"))
        val segs = parseHtmlSegments(html)
        assertEquals("plain hi", segs.joinToString("") { it.text })
        assertTrue(segs.first { it.text == "hi" }.attrs.contains(Attr.ITALIC))
        assertFalse(segs.first { it.text == "plain " }.attrs.contains(Attr.ITALIC))
    }

    @Test
    fun highlightRoundTripKeepsInk() {
        val ann = buildAnnotatedString {
            pushStyle(SpanStyle(background = HighlightInk.ROSE.toColor()))
            append("x")
            pop()
        }
        val html = annotatedToHtml(ann)
        assertTrue("copy must emit the wash color, got: $html", html.contains("data-bg"))
        val segs = parseHtmlSegments(html)
        assertEquals(1, segs.size)
        assertTrue(segs[0].attrs.contains(Attr.HIGHLIGHT))
        assertEquals(HighlightInk.ROSE.toColor(), segs[0].bg)
        assertEquals(HighlightInk.ROSE, nearestInk(segs[0].bg))
    }

    @Test
    fun foreignBoldTolerated() {
        val segs = parseHtmlSegments("<b>x</b> and <i>y</i>")
        assertTrue(segs[0].attrs.contains(Attr.BOLD))
        assertTrue(segs.last().attrs.contains(Attr.ITALIC))
    }

    @Test
    fun paragraphsKeepBreaks() {
        val ann = buildAnnotatedString { append("a\nb\n") }
        val back = parseHtmlSegments(annotatedToHtml(ann)).joinToString("") { it.text }
        assertEquals("trailing newline must survive the round trip", "a\nb\n", back)
    }

    @Test
    fun foreignYellowMapsToDandelion() {
        assertEquals(HighlightInk.DANDELION, nearestInk(Color(0xFFFFFF00)))
    }
}
