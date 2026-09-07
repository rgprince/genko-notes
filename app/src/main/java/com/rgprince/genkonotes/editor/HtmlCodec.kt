package com.rgprince.genkonotes.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString

/**
 * Styled clipboard codec: live AnnotatedString <-> minimal HTML subset.
 *
 * Copy emits real inline tags (<b> <i> <u> <s> <tt> <h1> <h2> and
 * <span data-bg="#AARRGGBB"> for marker washes) inside <p> paragraphs, so
 * other apps also receive formatted HTML — not just our own paste path.
 *
 * Paste parses a small tag subset back into [HtmlSeg]s (pure Kotlin, no
 * android.text.Html so it stays JVM-unit-testable). Unknown tags are ignored
 * with their text kept, so foreign HTML degrades to styled-or-plain text
 * instead of failing.
 */
data class HtmlSeg(val text: String, val attrs: Set<Attr>, val bg: Color = Color.Unspecified)

/** Nearest marker ink for a pasted wash color (foreign yellows land on Dandelion, …). */
fun nearestInk(color: Color): HighlightInk {
    HighlightInk.entries.firstOrNull { it != HighlightInk.NONE && it.toColor() == color }
        ?.let { return it }
    var best = HighlightInk.DANDELION
    var bestD = Float.MAX_VALUE
    for (ink in HighlightInk.entries) {
        if (ink == HighlightInk.NONE) continue
        val c = ink.toColor()
        val dr = color.red - c.red
        val dg = color.green - c.green
        val db = color.blue - c.blue
        val d = dr * dr + dg * dg + db * db
        if (d < bestD) {
            bestD = d
            best = ink
        }
    }
    return best
}

fun annotatedToHtml(ann: AnnotatedString): String {
    val t = ann.text
    val sb = StringBuilder("<html><body>")
    var start = 0
    while (true) {
        val nl = t.indexOf('\n', start)
        val end = if (nl == -1) t.length else nl
        sb.append("<p>")
        appendParaHtml(sb, ann, start, end)
        sb.append("</p>")
        if (nl == -1) break
        start = nl + 1
    }
    sb.append("</body></html>")
    return sb.toString()
}

private fun escapeHtml(s: String): String {
    val sb = StringBuilder(s.length)
    s.forEach { c ->
        when (c) {
            '&' -> sb.append("&amp;")
            '<' -> sb.append("&lt;")
            '>' -> sb.append("&gt;")
            else -> sb.append(c)
        }
    }
    return sb.toString()
}

private fun bgHex(color: Color): String {
    // Never read color.value (packed color-space bits, not ARGB) — rebuild hex
    // from the float channels so the exact marker color round-trips.
    fun b(f: Float) = (f * 255f + 0.5f).toInt().coerceIn(0, 255)
    return "#%02X%02X%02X%02X".format(b(color.alpha), b(color.red), b(color.green), b(color.blue))
}

/** Canonical tag order: heads, b, i, u, s, tt, wash span. */
private fun tagsFor(attrs: Set<Attr>, bg: Color?): List<Pair<String, String?>> {
    val out = mutableListOf<Pair<String, String?>>()
    if (attrs.contains(Attr.HEAD1)) out.add("h1" to null)
    else if (attrs.contains(Attr.HEAD2)) out.add("h2" to null)
    else if (attrs.contains(Attr.BOLD)) out.add("b" to null)
    if (attrs.contains(Attr.ITALIC)) out.add("i" to null)
    if (attrs.contains(Attr.UNDERLINE)) out.add("u" to null)
    if (attrs.contains(Attr.STRIKE)) out.add("s" to null)
    if (attrs.contains(Attr.CODE)) out.add("tt" to null)
    if (attrs.contains(Attr.HIGHLIGHT)) {
        if (bg != null && bg != Color.Unspecified) out.add("span" to bgHex(bg))
        else out.add("mark" to null)
    }
    return out
}

private fun appendParaHtml(sb: StringBuilder, ann: AnnotatedString, s: Int, e: Int) {
    if (s >= e) return
    val cuts = sortedSetOf(s, e)
    ann.spanStyles.forEach { r ->
        if (r.end > s && r.start < e) {
            cuts.add(r.start.coerceIn(s, e))
            cuts.add(r.end.coerceIn(s, e))
        }
    }
    val pts = cuts.toList()
    val open = mutableListOf<Pair<String, String?>>()
    for (i in 0 until pts.size - 1) {
        val a = pts[i]
        val b = pts[i + 1]
        if (a >= b) continue
        val attrs = mutableSetOf<Attr>()
        var bg: Color? = null
        ann.spanStyles.forEach { r ->
            if (r.start <= a && r.end >= b) {
                val ra = attrsOf(r.item)
                attrs.addAll(ra)
                if (ra.contains(Attr.HIGHLIGHT)) bg = r.item.background
            }
        }
        val want = tagsFor(attrs, bg)
        var common = 0
        while (common < open.size && common < want.size && open[common] == want[common]) common++
        for (k in open.size - 1 downTo common) sb.append("</").append(open[k].first).append('>')
        open.subList(common, open.size).clear()
        for (k in common until want.size) {
            val (tag, param) = want[k]
            sb.append('<').append(tag)
            if (param != null) sb.append(" data-bg=\"").append(param).append('"')
            sb.append('>')
            open.add(want[k])
        }
        sb.append(escapeHtml(ann.text.substring(a, b)))
    }
    for (k in open.size - 1 downTo 0) sb.append("</").append(open[k].first).append('>')
}

// ---------- parser ----------

private data class TagFrame(val name: String, val attrs: Set<Attr>, val bg: Color)

private val BLOCKS = setOf("p", "div", "li", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote")

private fun frameFor(tag: String, raw: String): TagFrame {
    val attrs = mutableSetOf<Attr>()
    var bg = Color.Unspecified
    when (tag) {
        "b", "strong" -> attrs.add(Attr.BOLD)
        "i", "em", "cite", "dfn" -> attrs.add(Attr.ITALIC)
        "u", "ins" -> attrs.add(Attr.UNDERLINE)
        "s", "strike", "del" -> attrs.add(Attr.STRIKE)
        "tt", "code", "kbd" -> attrs.add(Attr.CODE)
        "h1" -> {
            attrs.add(Attr.HEAD1)
            attrs.add(Attr.BOLD)
        }
        "h2", "h3", "h4", "h5", "h6" -> {
            attrs.add(Attr.HEAD2)
            attrs.add(Attr.BOLD)
        }
        "mark" -> attrs.add(Attr.HIGHLIGHT)
        "span" -> {
            parseDataBg(raw)?.let {
                attrs.add(Attr.HIGHLIGHT)
                bg = it
            }
        }
    }
    return TagFrame(tag, attrs, bg)
}

private fun parseDataBg(rawTag: String): Color? {
    val m = Regex("data-bg\\s*=\\s*[\"']([^\"']+)[\"']").find(rawTag) ?: return null
    val hex = m.groupValues[1].removePrefix("#")
    return try {
        when (hex.length) {
            6 -> Color(0xFF000000L or hex.toLong(16))
            8 -> Color(hex.toLong(16))
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

private fun unescapeHtml(s: String): String {
    var out = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
    out = Regex("&#(\\d+);").replace(out) {
        val code = it.groupValues[1].toIntOrNull() ?: return@replace it.value
        code.toChar().toString()
    }
    out = Regex("&#x([0-9a-fA-F]+);").replace(out) {
        val code = it.groupValues[1].toIntOrNull(16) ?: return@replace it.value
        code.toChar().toString()
    }
    // &amp; last so double-escaped entities resolve exactly once.
    return out.replace("&amp;", "&")
}

fun parseHtmlSegments(html: String): List<HtmlSeg> {
    data class Tok(val isTag: Boolean, val text: String)
    val toks = mutableListOf<Tok>()
    var i = 0
    while (i < html.length) {
        val lt = html.indexOf('<', i)
        if (lt == -1) {
            toks.add(Tok(false, html.substring(i)))
            break
        }
        if (lt > i) toks.add(Tok(false, html.substring(i, lt)))
        val gt = html.indexOf('>', lt + 1)
        if (gt == -1) {
            toks.add(Tok(false, html.substring(lt)))
            break
        }
        toks.add(Tok(true, html.substring(lt + 1, gt)))
        i = gt + 1
    }

    val segs = mutableListOf<HtmlSeg>()
    val stack = mutableListOf<TagFrame>()
    fun currentAttrs(): Set<Attr> {
        val a = mutableSetOf<Attr>()
        stack.forEach { a.addAll(it.attrs) }
        return a
    }
    fun currentBg(): Color {
        var bg = Color.Unspecified
        stack.forEach { if (it.bg != Color.Unspecified) bg = it.bg }
        return bg
    }
    fun emitText(raw: String) {
        val t = unescapeHtml(raw)
        if (t.isEmpty()) return
        val a = currentAttrs()
        val bg = currentBg()
        val last = segs.lastOrNull()
        if (last != null && last.attrs == a && last.bg == bg) {
            segs[segs.lastIndex] = last.copy(text = last.text + t)
        } else {
            segs.add(HtmlSeg(t, a, bg))
        }
    }
    fun emitNewline() {
        // No dedup guard: an empty <p></p> between paragraphs IS a real blank
        // line, and the open/close rules already fire exactly once per boundary.
        segs.add(HtmlSeg("\n", currentAttrs(), currentBg()))
    }
    fun hasOutput(): Boolean = segs.any { it.text.isNotEmpty() }
    fun endsWithNewline(): Boolean {
        for (k in segs.size - 1 downTo 0) {
            val t = segs[k].text
            if (t.isNotEmpty()) return t.endsWith('\n')
        }
        return true
    }
    fun nextOpensBlock(from: Int): Boolean {
        for (k in from until toks.size) {
            val tk = toks[k]
            if (!tk.isTag) {
                if (tk.text.isNotBlank()) return false
                continue
            }
            val name = tk.text.trimStart('/').substringBefore(' ').substringBefore('\t')
                .trimEnd('/').lowercase()
            if (name in BLOCKS && !tk.text.trimStart().startsWith("/")) return true
            if (name == "br") return true
            if (tk.text.trimStart().startsWith("/") || name in
                setOf("html", "body", "ul", "ol", "head", "span", "mark", "font", "a")
            ) continue
            return false
        }
        return false
    }

    toks.forEachIndexed { idx, tk ->
        if (!tk.isTag) {
            emitText(tk.text)
            return@forEachIndexed
        }
        val raw = tk.text.trim()
        val closing = raw.startsWith("/")
        val name = raw.trimStart('/').substringBefore(' ').substringBefore('\t')
            .trimEnd('/').lowercase()
        if (name == "br") {
            emitNewline()
        } else if (closing) {
            if (name in BLOCKS) {
                if (nextOpensBlock(idx + 1)) emitNewline()
            }
            val at = stack.indexOfLast { it.name == name }
            if (at >= 0) stack.subList(at, stack.size).clear()
        } else {
            if (name in BLOCKS) {
                if (hasOutput() && !endsWithNewline()) emitNewline()
            }
            stack.add(frameFor(name, raw))
        }
    }
    // Coalesce adjacent twins (newlines included).
    val out = mutableListOf<HtmlSeg>()
    segs.forEach { s ->
        val last = out.lastOrNull()
        if (last != null && last.attrs == s.attrs && last.bg == s.bg) {
            out[out.lastIndex] = last.copy(text = last.text + s.text)
        } else {
            out.add(s)
        }
    }
    return out
}
