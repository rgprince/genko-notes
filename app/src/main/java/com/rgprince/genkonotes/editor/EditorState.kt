package com.rgprince.genkonotes.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import java.util.UUID

data class NoteDoc(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val body: AnnotatedString = AnnotatedString(""),
    val folder: String = "Inbox",
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

/** Span-toggle editor state. Toolbar mutates SpanStyles — never inserts ** or _ symbols. */
class NijiEditorState(initial: AnnotatedString = AnnotatedString("")) {
    var field by mutableStateOf(TextFieldValue(initial))
        private set

    private val undoStack = mutableStateListOf<AnnotatedString>()
    private val redoStack = mutableStateListOf<AnnotatedString>()

    var blockKind by mutableStateOf("paragraph")
    var highlightInk by mutableStateOf(HighlightInk.DANDELION)
    var codeBg by mutableStateOf(Color.Transparent)

    /** Typing attributes used when the cursor is collapsed (Docs-style). */
    var activeAttrs by mutableStateOf(setOf<Attr>())
        private set

    /**
     * Content generation. Bumped on every span/text mutation (never on bare
     * cursor moves) so autosave can use a cheap exact dirty key.
     */
    var version by mutableStateOf(0)
        private set

    private fun bump() {
        version++
    }

    /**
     * Last valid styled range. If a toolbar tap arrives with a collapsed cursor
     * (focus raced ahead of onClick on some keyboards), we still style the word
     * the user had selected instead of silently flipping a typing flag.
     */
    private var lastRange: Pair<Int, Int>? = null

    /**
     * One-shot "user explicitly left heading mode" latch. Headings normally extend
     * at their line end, but after a Body/disarm tap the next typed characters must
     * stay Body without forcing the user to press Enter first. Consumed by the next
     * text change; cleared whenever heading state is explicitly rewritten.
     */
    private var suppressHeadExtend = false

    /** Whether the body field currently holds focus (wired from onFocusChanged). */
    var isFocused by mutableStateOf(false)
        private set

    fun onFocusChanged(focused: Boolean) {
        isFocused = focused
    }

    fun update(next: TextFieldValue) {
        val old = field
        if (next.text != old.text) {
            pushUndo(old.annotatedString)
            lastRange = null
            field = next.copy(annotatedString = reflow(old.annotatedString, old.text, next.text))
            bump()
        } else if (next.selection != old.selection) {
            // Single source of truth: with identical text OUR spans are the newest
            // possible state — the framework never authors spans. Echoes that arrive
            // span-stripped (IME restart, focus dance, app resume) must not wipe
            // committed formatting, so we keep our model and take only the selection.
            field = next.copy(annotatedString = old.annotatedString)
            if (next.selection.collapsed) {
                // Cursor moved without typing: typing attributes follow the new position.
                // End-exclusive rule -> moving to the end of an italic word clears italic.
                syncActiveFromCursor(old.annotatedString, next.selection.start)
            } else {
                lastRange = minOf(next.selection.start, next.selection.end) to
                    maxOf(next.selection.start, next.selection.end)
            }
        } else {
            // Identical text+selection (e.g. composition-only echo): same rule.
            field = next.copy(annotatedString = old.annotatedString)
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            suppressHeadExtend = false
            redoStack.add(field.annotatedString)
            val prev = undoStack.removeAt(undoStack.lastIndex)
            lastRange = null
            field = field.copy(annotatedString = prev, selection = TextRange(prev.length))
            syncActiveFromCursor(prev, prev.length)
            bump()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            suppressHeadExtend = false
            undoStack.add(field.annotatedString)
            val next = redoStack.removeAt(redoStack.lastIndex)
            lastRange = null
            field = field.copy(annotatedString = next, selection = TextRange(next.length))
            syncActiveFromCursor(next, next.length)
            bump()
        }
    }

    /**
     * Styled paste: clipboard HTML -> native runs spliced at the cursor.
     * Returns false when there is no styled payload (caller falls back to plain).
     * Commits directly like the toolbar toggles — no typing-attr inheritance,
     * so pasted italic stays exactly italic.
     */
    fun pasteStyled(context: android.content.Context): Boolean {
        val segs = RichClipboard.pasteSegments(context) ?: return false
        if (segs.isEmpty()) return false
        val built = buildAnnotatedString {
            segs.forEach { seg ->
                if (seg.text.isEmpty()) return@forEach
                val from = length
                append(seg.text)
                val a = seg.attrs.toMutableSet()
                if (a.contains(Attr.HIGHLIGHT)) {
                    val ink = if (seg.bg != Color.Unspecified) nearestInk(seg.bg)
                    else highlightInk
                    if (ink == HighlightInk.NONE) {
                        a.remove(Attr.HIGHLIGHT)
                    } else {
                        highlightInk = ink
                    }
                }
                val hlColor = if (a.contains(Attr.HIGHLIGHT)) highlightInk.toColor()
                else Color.Unspecified
                styleForAttrs(a, hlColor)?.let { addStyle(it, from, from + seg.text.length) }
            }
        }
        if (built.text.isEmpty()) return false
        val cur = field
        val sel = cur.selection
        val start = minOf(sel.start, sel.end).coerceIn(0, cur.text.length)
        val end = maxOf(sel.start, sel.end).coerceIn(0, cur.text.length)
        val next = cur.text.substring(0, start) + built.text + cur.text.substring(end)
        val rebuilt = buildAnnotatedString {
            append(next)
            cur.annotatedString.spanStyles.forEach { r ->
                if (r.end <= start) addStyle(r.item, r.start, r.end)
                else if (r.start >= end) {
                    addStyle(r.item, r.start + built.length, r.end + built.length)
                } else {
                    if (r.start < start) addStyle(r.item, r.start, start)
                    if (r.end > end) {
                        addStyle(r.item, start + built.length, r.end + built.length - (end - start))
                    }
                }
            }
            built.spanStyles.forEach { r ->
                addStyle(r.item, r.start + start, r.end + start)
            }
        }
        pushUndo(cur.annotatedString)
        lastRange = null
        val committed = normalize(rebuilt)
        field = cur.copy(
            annotatedString = committed,
            selection = TextRange(start + built.length)
        )
        syncActiveFromCursor(committed, start + built.length)
        bump()
        return true
    }

    fun wordCount(): Int {
        val t = field.text.trim()
        if (t.isEmpty()) return 0
        return t.split(Regex("\\s+")).size
    }

    /** Toolbar display state: live span check over a range, typing attrs for a cursor. */
    fun displayActive(attr: Attr): Boolean {
        val sel = field.selection
        if (sel.collapsed) return activeAttrs.contains(attr)
        val s = minOf(sel.start, sel.end).coerceIn(0, field.text.length)
        val e = maxOf(sel.start, sel.end).coerceIn(0, field.text.length)
        if (s >= e) return activeAttrs.contains(attr)
        return uniformAttr(field.annotatedString, s, e, attr)
    }

    fun toggleBold() = toggleAttr(Attr.BOLD)
    fun toggleItalic() = toggleAttr(Attr.ITALIC)
    fun toggleUnderline() = toggleAttr(Attr.UNDERLINE)
    fun toggleStrike() = toggleAttr(Attr.STRIKE)

    fun toggleHighlight() = toggleAttr(Attr.HIGHLIGHT)

    /** Cut helper: delete the current selection (native toolbar Cut). */
    fun deleteSelection(): Boolean {
        val cur = field
        val sel = cur.selection
        if (sel.collapsed) return false
        val s = minOf(sel.start, sel.end).coerceIn(0, cur.text.length)
        val e = maxOf(sel.start, sel.end).coerceIn(0, cur.text.length)
        if (s >= e) return false
        update(cur.copy(text = cur.text.removeRange(s, e), selection = TextRange(s)))
        return true
    }

    // ---------- block / line tools (top toolbar) ----------

    /** [start, end) bounds (end exclusive) of the line holding the cursor. */
    fun currentLineBounds(): Pair<Int, Int> {
        val t = field.text
        val c = field.selection.start.coerceIn(0, t.length)
        val s = if (c == 0) 0 else {
            val i = t.lastIndexOf('\n', c - 1)
            if (i == -1) 0 else i + 1
        }
        val e = t.indexOf('\n', c).let { if (it == -1) t.length else it }
        return s to e
    }

    fun lineHasPrefix(prefix: String): Boolean {
        val (s, _) = currentLineBounds()
        return field.text.startsWith(prefix, s)
    }

    fun clearHead() {
        if (field.selection.collapsed) {
            // No selection: future text is Body (plain paragraph mode).
            // The existing line under the cursor is never touched. The latch
            // below additionally breaks the line-end head extension so the very
            // next typed characters stay Body (no Enter required).
            activeAttrs = activeAttrs - Attr.HEAD1 - Attr.HEAD2 - Attr.BOLD
            suppressHeadExtend = true
            return
        }
        val (s, e) = currentLineBounds()
        if (s >= e) return
        suppressHeadExtend = false
        val cur = field.annotatedString
        var a = stripAttrRange(cur, s, e, Attr.HEAD1)
        a = stripAttrRange(a, s, e, Attr.HEAD2)
        if (a != cur) {
            pushUndo(cur)
            val committed = normalize(a)
            field = field.copy(annotatedString = committed)
            refreshActiveOver(committed, s, e)
            bump()
        }
    }

    fun toggleHead(attr: Attr) {
        require(attr == Attr.HEAD1 || attr == Attr.HEAD2)
        val other = if (attr == Attr.HEAD1) Attr.HEAD2 else Attr.HEAD1
        if (field.selection.collapsed) {
            // No selection: arm/disarm the heading for subsequently typed text
            // only. The existing line under the cursor is never restyled.
            // Disarming latches head suppression (like Body); arming clears it.
            val arming = !activeAttrs.contains(attr)
            activeAttrs = if (arming) (activeAttrs - other) + attr + Attr.BOLD
            else activeAttrs - attr - Attr.BOLD
            suppressHeadExtend = !arming
            return
        }
        val (s, e) = currentLineBounds()
        if (s >= e) return
        suppressHeadExtend = false
        val cur = field.annotatedString
        if (uniformAttr(cur, s, e, attr)) {
            pushUndo(cur)
            val committed = normalize(stripAttrRange(cur, s, e, attr))
            field = field.copy(annotatedString = committed)
            refreshActiveOver(committed, s, e)
            bump()
        } else {
            var a = stripAttrRange(cur, s, e, Attr.HEAD1)
            a = stripAttrRange(a, s, e, Attr.HEAD2)
            pushUndo(cur)
            val b = normalize(
                buildAnnotatedString {
                    append(a.text)
                    a.spanStyles.forEach { addStyle(it.item, it.start, it.end) }
                    addStyle(freshStyle(attr), s, e)
                }
            )
            field = field.copy(annotatedString = b)
            lastRange = s to e
            refreshActiveOver(b, s, e)
            bump()
        }
    }

    fun toggleLinePrefix(prefix: String) {
        val (s, e) = currentLineBounds()
        val t = field.text
        val line = t.substring(s, e)
        val c = field.selection.start.coerceIn(0, t.length)
        if (line.startsWith(prefix)) {
            val nt = t.removeRange(s, s + prefix.length)
            val nc = (c - prefix.length).coerceAtLeast(s)
            applyTextEdit(nt, nc)
        } else {
            val nt = t.substring(0, s) + prefix + t.substring(s)
            val nc = if (c <= s) c else c + prefix.length
            applyTextEdit(nt, nc)
        }
    }

    fun toggleBullet() = toggleLinePrefix("• ")

    fun toggleQuote() = toggleLinePrefix("▌ ")

    fun toggleChecklist() {
        val (s, e) = currentLineBounds()
        val t = field.text
        val line = t.substring(s, e)
        val c = field.selection.start.coerceIn(0, t.length)
        val nt = when {
            line.startsWith("☐ ") -> t.replaceRange(s, s + "☐ ".length, "☑ ")
            line.startsWith("☑ ") -> t.replaceRange(s, s + "☑ ".length, "☐ ")
            else -> t.substring(0, s) + "☐ " + t.substring(s)
        }
        val added = nt.length - t.length
        val nc = if (c <= s) c else (c + added).coerceIn(0, nt.length)
        applyTextEdit(nt, nc)
    }

    fun toggleCodeLine() {
        if (field.selection.collapsed) {
            // No selection: arm/disarm CODE for subsequently typed text only.
            activeAttrs = if (activeAttrs.contains(Attr.CODE)) activeAttrs - Attr.CODE
            else activeAttrs + Attr.CODE
            return
        }
        val (s, e) = currentLineBounds()
        if (s >= e) return
        // Line-scoped CODE toggle that preserves the user's selection.
        toggleAttrRange(Attr.CODE, s, e)
    }

    // ---------- internals ----------

    private fun pushUndo(current: AnnotatedString) {
        if (undoStack.lastOrNull() != current) {
            undoStack.add(current)
            if (undoStack.size > 100) undoStack.removeAt(0)
            redoStack.clear()
        }
    }

    /** Programmatic text edit (prefix toggles): spans remapped, undo recorded. */
    private fun applyTextEdit(newText: String, cursor: Int) {
        val old = field
        if (newText == old.text) return
        pushUndo(old.annotatedString)
        lastRange = null
        val mapped = reflow(old.annotatedString, old.text, newText)
        val at = cursor.coerceIn(0, newText.length)
        field = TextFieldValue(mapped, TextRange(at))
        syncActiveFromCursor(mapped, at)
        bump()
    }

    /** Re-derive typing attributes from the spans fully covering a styled range. */
    private fun refreshActiveOver(ann: AnnotatedString, s: Int, e: Int) {
        val found = mutableSetOf<Attr>()
        ann.spanStyles.forEach { r ->
            if (r.start <= s && r.end >= e) {
                found.addAll(attrsOf(r.item))
                if (isMarkerColor(r.item.background)) {
                    HighlightInk.entries.firstOrNull { it.toColor() == r.item.background }
                        ?.let { highlightInk = it }
                }
            }
        }
        activeAttrs = found
    }

    private fun toggleAttr(attr: Attr) {
        val sel = field.selection
        val direct = if (!sel.collapsed) {
            minOf(sel.start, sel.end) to maxOf(sel.start, sel.end)
        } else null
        // Fall back to the last valid range ONLY for a focus-loss race: the field
        // is unfocused AND the collapse landed at the old selection's edge. A
        // deliberate cursor move elsewhere (still focused, or landed far away)
        // means typing-attribute flip — never restyle a word the user left.
        val fb = lastRange?.takeIf { (s, e) ->
            s < e && e <= field.text.length && !isFocused &&
                sel.start in (s - 1)..(e + 1)
        }
        val range = direct ?: fb
        if (range == null) {
            // No selection: flip the typing attribute for whatever gets typed next.
            activeAttrs = if (activeAttrs.contains(attr)) activeAttrs - attr else activeAttrs + attr
            return
        }
        val (s, e) = range
        toggleAttrRange(attr, s, e)
        if (direct == null) {
            // Focus-race fallback: spans are committed, but the cursor NEVER moves.
            // Internal selections stay invisible — no code path may paint a
            // highlight the user didn't drag themselves.
            lastRange = s to e
        }
    }

    /**
     * Core span toggle over [s, e). Never touches the selection — callers own
     * cursor behavior. (Block ops must NOT force-select the line: the next
     * spacebar would otherwise replace the whole sentence.)
     */
    private fun toggleAttrRange(attr: Attr, s: Int, e: Int) {
        val cur = field.annotatedString
        // B on a heading demotes the whole head (size rides on the same toggle).
        val target = if (attr == Attr.BOLD) {
            when {
                uniformAttr(cur, s, e, Attr.HEAD1) -> Attr.HEAD1
                uniformAttr(cur, s, e, Attr.HEAD2) -> Attr.HEAD2
                else -> attr
            }
        } else attr
        val had = uniformAttr(cur, s, e, target)
        val stripped = stripAttrRange(cur, s, e, target)
        val next = if (had || (target == Attr.HIGHLIGHT && highlightInk == HighlightInk.NONE)) {
            stripped
        } else {
            buildAnnotatedString {
                append(stripped.text)
                stripped.spanStyles.forEach { addStyle(it.item, it.start, it.end) }
                addStyle(freshStyle(target), s, e)
            }
        }
        pushUndo(cur)
        val committed = normalize(next)
        field = field.copy(annotatedString = committed)
        refreshActiveOver(committed, s, e)
        bump()
    }

    private fun freshStyle(attr: Attr): SpanStyle {
        return when (attr) {
            Attr.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            Attr.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            Attr.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
            Attr.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
            Attr.CODE -> SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)
            Attr.HIGHLIGHT -> SpanStyle(background = highlightInk.toColor())
            Attr.HEAD1 -> SpanStyle(fontSize = HEAD1_SIZE, fontWeight = FontWeight.Bold)
            Attr.HEAD2 -> SpanStyle(fontSize = HEAD2_SIZE, fontWeight = FontWeight.Bold)
        }
    }

    private fun uniformAttr(cur: AnnotatedString, s: Int, e: Int, attr: Attr): Boolean {
        if (s >= e) return false
        // Interval coverage sweep: O(spans log spans), never O(length x spans),
        // so toolbar state stays instant on long documents (Bug 3).
        var cover = s
        cur.spanStyles
            .filter { r -> attrsOf(r.item).contains(attr) && r.end > s && r.start < e }
            .map { maxOf(it.start, s) to minOf(it.end, e) }
            .sortedBy { it.first }
            .forEach { (a, b) ->
                if (a > cover) return false
                if (b > cover) cover = b
                if (cover >= e) return true
            }
        return cover >= e
    }

    /**
     * Canonical span key. Two spans with equal keys render identically, so
     * overlapping/adjacent twins merge with zero visual change (Bug 3: without
     * this every keystroke appends a span and the list — plus every toolbar
     * and autosave scan over it — grows without bound).
     */
    private data class SpanKey(
        val attrs: Set<Attr>,
        val background: Color,
        val fontSize: androidx.compose.ui.unit.TextUnit,
        val family: FontFamily?
    )

    private fun spanKeyOf(style: SpanStyle): SpanKey {
        return SpanKey(attrsOf(style), style.background, style.fontSize, style.fontFamily)
    }

    /** Merge overlapping/adjacent identically-rendered spans; drop empty ranges. */
    private fun normalize(ann: AnnotatedString): AnnotatedString {
        val spans = ann.spanStyles.filter { it.start < it.end }
        if (spans.size < 2) {
            if (spans.size == ann.spanStyles.size) return ann
            return buildAnnotatedString {
                append(ann.text)
                spans.forEach { addStyle(it.item, it.start, it.end) }
            }
        }
        data class Acc(val key: SpanKey, val item: SpanStyle, val start: Int, var end: Int)
        val sorted = spans.sortedWith(compareBy({ it.start }, { it.end }))
        val merged = mutableListOf<Acc>()
        for (r in sorted) {
            val key = spanKeyOf(r.item)
            val last = merged.lastOrNull()
            if (last != null && last.key == key && r.start <= last.end) {
                if (r.end > last.end) last.end = r.end
            } else {
                merged.add(Acc(key, r.item, r.start, r.end))
            }
        }
        if (merged.size == ann.spanStyles.size) {
            val orig = ann.spanStyles.sortedWith(compareBy({ it.start }, { it.end }))
            var identical = true
            for (i in orig.indices) {
                val o = orig[i]
                val m = merged[i]
                if (o.start != m.start || o.end != m.end || spanKeyOf(o.item) != m.key) {
                    identical = false
                    break
                }
            }
            if (identical) return ann
        }
        return buildAnnotatedString {
            append(ann.text)
            merged.forEach { addStyle(it.item, it.start, it.end) }
        }
    }

    private fun stripAttrRange(cur: AnnotatedString, s: Int, e: Int, attr: Attr): AnnotatedString {
        return buildAnnotatedString {
            append(cur.text)
            cur.spanStyles.forEach { r ->
                if (r.end <= s || r.start >= e) {
                    addStyle(r.item, r.start, r.end)
                } else {
                    if (r.start < s) addStyle(r.item, r.start, minOf(s, r.end))
                    if (r.end > e) addStyle(r.item, maxOf(e, r.start), r.end)
                    val midS = maxOf(r.start, s)
                    val midE = minOf(r.end, e)
                    if (midS < midE) {
                        val stripped = stripAttr(r.item, attr)
                        if (stripped != null) addStyle(stripped, midS, midE)
                    }
                }
            }
        }
    }

    private fun syncActiveFromCursor(ann: AnnotatedString, cursor: Int) {
        val c = cursor.coerceIn(0, ann.length)
        val found = mutableSetOf<Attr>()
        ann.spanStyles.forEach { r ->
            val a = attrsOf(r.item)
            if (r.start <= c && r.end > c) {
                // End-exclusive for inline styles: a span ending exactly at the
                // cursor does NOT apply (stops italic/highlight leaking at word end).
                found.addAll(a)
            } else if (r.end == c && c > 0 &&
                (a.contains(Attr.HEAD1) || a.contains(Attr.HEAD2)) &&
                ann.text.getOrNull(c - 1) != '\n' && !suppressHeadExtend
            ) {
                // ...but headings are line properties: sitting at the end of a
                // headed line still counts as "in" the heading (Bug 1) — unless
                // the user explicitly tapped Body/disarm (suppression latch).
                found.addAll(a)
            }
            if (r.start <= c && r.end > c && isMarkerColor(r.item.background)) {
                HighlightInk.entries.firstOrNull { it.toColor() == r.item.background }
                    ?.let { highlightInk = it }
            }
        }
        activeAttrs = found
    }

    /**
     * Rebuild spans after a text change so styles never spill onto newly typed
     * characters at a styled boundary. New chars get ONLY the typing attributes
     * (+ styles inherited from strictly inside a span, never from its end edge).
     */
    private fun reflow(oldAnn: AnnotatedString, oldText: String, newText: String): AnnotatedString {
        // One-shot latch: any text change consumes it (checked below before clearing).
        val suppressHead = suppressHeadExtend
        suppressHeadExtend = false
        var p = 0
        val minLen = minOf(oldText.length, newText.length)
        while (p < minLen && oldText[p] == newText[p]) p++
        var s = 0
        while (s < (oldText.length - p) && s < (newText.length - p) &&
            oldText[oldText.length - 1 - s] == newText[newText.length - 1 - s]
        ) s++
        val insStart = p
        val insEnd = newText.length - s
        val insLen = insEnd - insStart
        val delta = newText.length - oldText.length
        val oldCutStart = p
        val oldCutEnd = oldText.length - s

        // Styles strictly containing the edit point (end-exclusive) carry over.
        // Headings additionally extend at their exact end edge on the same line:
        // a heading is a line property, so typing at the end of an H1 line stays H1.
        val inherited = mutableSetOf<Attr>()
        var inheritedHighlight: Color? = null
        oldAnn.spanStyles.forEach { r ->
            val a = attrsOf(r.item)
            val strictlyInside = r.start <= oldCutStart && r.end > oldCutStart
            val headAtEdge = r.end == oldCutStart &&
                (a.contains(Attr.HEAD1) || a.contains(Attr.HEAD2)) &&
                newText.getOrNull(insStart - 1) != '\n' && !suppressHead
            if (strictlyInside || headAtEdge) {
                inherited.addAll(a)
                if (inheritedHighlight == null && isMarkerColor(r.item.background)) {
                    inheritedHighlight = r.item.background
                }
            }
        }
        if (inherited.contains(Attr.HIGHLIGHT)) {
            inheritedHighlight?.let { c ->
                HighlightInk.entries.firstOrNull { it.toColor() == c }?.let { highlightInk = it }
            }
        }

        val rebuilt = buildAnnotatedString {
            append(newText)
            mapSpans(this, oldAnn, oldCutStart, oldCutEnd, insStart, insEnd, delta)
            if (insLen > 0) {
                val applied = (activeAttrs + inherited).toMutableSet()
                val hlColor = inheritedHighlight ?: highlightInk.toColor()
                if (applied.contains(Attr.HIGHLIGHT) && highlightInk == HighlightInk.NONE && inheritedHighlight == null) {
                    applied.remove(Attr.HIGHLIGHT)
                }
                val style = styleForAttrs(applied, hlColor)
                if (style != null) addStyle(style, insStart, insEnd)
                activeAttrs = applied
            }
        }
        val out = normalize(rebuilt)
        if (insLen == 0) {
            // Deletion only: if an entire headed run was removed and its line is
            // now empty, the block keeps its head for whatever gets typed next
            // instead of silently resetting to Body (Bug 1).
            val armed = activeAttrs
            var deletedHead: Attr? = null
            if (oldCutEnd > oldCutStart) {
                if (uniformAttr(oldAnn, oldCutStart, oldCutEnd, Attr.HEAD1)) deletedHead = Attr.HEAD1
                else if (uniformAttr(oldAnn, oldCutStart, oldCutEnd, Attr.HEAD2)) deletedHead = Attr.HEAD2
            }
            syncActiveFromCursor(out, insStart)
            if (deletedHead != null && isLineEmpty(newText, insStart)) {
                activeAttrs = activeAttrs + deletedHead + Attr.BOLD
            }
            // Sticky typing: a deletion never disarms by itself. Armed attrs
            // survive when the deletion didn't touch them at all, OR when a
            // same-attr run still ends at the cursor (backspacing the tail of
            // an italic word keeps italic armed — typing continues the style,
            // Docs-style). Only attrs whose runs are fully gone follow sync.
            if (oldCutEnd > oldCutStart) {
                val touched = mutableSetOf<Attr>()
                oldAnn.spanStyles.forEach { r ->
                    if (r.start < oldCutEnd && r.end > oldCutStart) {
                        touched.addAll(attrsOf(r.item))
                    }
                }
                val keep = armed.filter { attr ->
                    attr !in touched || out.spanStyles.any { r ->
                        attrsOf(r.item).contains(attr) &&
                            r.start < insStart && r.end >= insStart
                    }
                }.toSet()
                if (keep.isNotEmpty()) activeAttrs = activeAttrs + keep
            }
        }
        return out
    }

    /** True when the line holding [pos] in [t] has no characters. */
    private fun isLineEmpty(t: String, pos: Int): Boolean {
        val c = pos.coerceIn(0, t.length)
        val s = if (c == 0) 0 else {
            val i = t.lastIndexOf('\n', c - 1)
            if (i == -1) 0 else i + 1
        }
        val e = t.indexOf('\n', c).let { if (it == -1) t.length else it }
        return s >= e
    }

    private fun mapSpans(
        out: AnnotatedString.Builder,
        oldAnn: AnnotatedString,
        oldCutStart: Int,
        oldCutEnd: Int,
        insStart: Int,
        insEnd: Int,
        delta: Int
    ) {
        oldAnn.spanStyles.forEach { r ->
            if (r.end <= oldCutStart) {
                out.addStyle(r.item, r.start, r.end)
            } else if (r.start >= oldCutEnd) {
                out.addStyle(r.item, r.start + delta, r.end + delta)
            } else {
                if (r.start < oldCutStart) out.addStyle(r.item, r.start, insStart)
                if (r.end > oldCutEnd) out.addStyle(r.item, insEnd, r.end + delta)
            }
        }
    }

    private fun styleForAttrs(attrs: Set<Attr>, hlColor: Color): SpanStyle? {
        if (attrs.isEmpty()) return null
        var style = SpanStyle()
        if (attrs.contains(Attr.HEAD1)) {
            style = style.copy(fontSize = HEAD1_SIZE, fontWeight = FontWeight.Bold)
        } else if (attrs.contains(Attr.HEAD2)) {
            style = style.copy(fontSize = HEAD2_SIZE, fontWeight = FontWeight.Bold)
        } else if (attrs.contains(Attr.BOLD)) {
            style = style.copy(fontWeight = FontWeight.Bold)
        }
        if (attrs.contains(Attr.ITALIC)) style = style.copy(fontStyle = FontStyle.Italic)
        val ul = attrs.contains(Attr.UNDERLINE)
        val st = attrs.contains(Attr.STRIKE)
        style = when {
            ul && st -> style.copy(textDecoration = UL_AND_STRIKE)
            ul -> style.copy(textDecoration = TextDecoration.Underline)
            st -> style.copy(textDecoration = TextDecoration.LineThrough)
            else -> style
        }
        if (attrs.contains(Attr.CODE)) {
            style = style.copy(fontFamily = FontFamily.Monospace, background = codeBg)
        }
        if (attrs.contains(Attr.HIGHLIGHT) && hlColor != Color.Unspecified) {
            style = style.copy(background = hlColor)
        }
        return if (style == SpanStyle()) null else style
    }
}
