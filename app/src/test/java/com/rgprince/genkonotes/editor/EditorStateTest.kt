package com.rgprince.genkonotes.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.rgprince.genkonotes.data.StoredNote
import com.rgprince.genkonotes.ui.sortNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM regression tests for the rich-text model. They run headless in CI
 * (./gradlew :app:testDebugUnitTest) — no device needed.
 *
 * Conventions: TextFieldValue(text, selection) builds a PLAIN echo, exactly what
 * a hostile framework/IME event (restart, focus dance, resume) delivers. The model
 * must survive those (Bug 2) because it is the single source of truth.
 */
class EditorStateTest {

    private fun type(state: NijiEditorState, text: String) {
        state.update(TextFieldValue(text, TextRange(text.length)))
    }

    private fun moveCursor(state: NijiEditorState, pos: Int) {
        state.update(TextFieldValue(state.field.text, TextRange(pos)))
    }

    private fun select(state: NijiEditorState, s: Int, e: Int) {
        state.update(TextFieldValue(state.field.text, TextRange(s, e)))
    }

    private fun runsOf(state: NijiEditorState) = state.field.annotatedString.toRuns()

    @Test
    fun boldSurvivesSelectionChange() {
        val st = NijiEditorState()
        type(st, "hello world")
        select(st, 0, 5)
        st.toggleBold()
        select(st, 6, 11)
        val runs = runsOf(st)
        assertEquals("hello", runs.first().text)
        assertTrue("bold must survive selection change (Bug 2)", runs.first().bold)
    }

    @Test
    fun selectionEchoNeverDropsCommittedSpans() {
        val st = NijiEditorState()
        type(st, "hello world")
        select(st, 0, 5)
        st.toggleBold()
        // Hostile echo: same text, moved cursor, PLAIN spans (IME restart / resume).
        st.update(TextFieldValue("hello world", TextRange(11)))
        val runs = runsOf(st)
        assertEquals("hello", runs.first().text)
        assertTrue("committed spans must survive span-stripped echoes (Bug 2)", runs.first().bold)
    }

    @Test
    fun italicDoesNotSpillAtWordEnd() {
        val st = NijiEditorState()
        type(st, "hi!")
        select(st, 0, 2)
        st.toggleItalic()
        moveCursor(st, 3)
        type(st, "hi!?")
        val runs = runsOf(st)
        assertEquals(2, runs.size)
        assertTrue(runs[0].italic)
        assertFalse(runs[1].italic)
    }

    @Test
    fun headSurvivesDeleteToEmpty() {
        val st = NijiEditorState()
        type(st, "Title")
        select(st, 0, 5)
        st.toggleHead(Attr.HEAD1)
        assertTrue(st.displayActive(Attr.HEAD1))
        var t = "Title"
        while (t.isNotEmpty()) {
            t = t.dropLast(1)
            type(st, t)
        }
        assertEquals("", st.field.text)
        assertTrue("H1 must survive delete-to-empty (Bug 1)", st.displayActive(Attr.HEAD1))
        type(st, "N")
        assertEquals("H1", runsOf(st).single().head)
    }

    @Test
    fun typingAtEndOfHeadedLineExtendsHead() {
        val st = NijiEditorState()
        type(st, "Tit")
        select(st, 0, 3)
        st.toggleHead(Attr.HEAD1)
        moveCursor(st, 3)
        type(st, "Titx")
        assertTrue("headings are line properties (Bug 1)", runsOf(st).all { it.head == "H1" })
    }

    @Test
    fun focusRaceStillStylesWord() {
        val st = NijiEditorState()
        type(st, "hello world")
        select(st, 0, 5)
        st.onFocusChanged(false) // keyboard stole focus before onClick…
        moveCursor(st, 5) // …and the selection collapsed at the range edge
        st.toggleBold() // must fall back to the last range, not flip a flag
        val runs = runsOf(st)
        assertEquals("hello", runs.first().text)
        assertTrue(runs.first().bold)
    }

    @Test
    fun fallbackStylingNeverCreatesVisibleSelection() {
        val st = NijiEditorState()
        type(st, "hello world")
        select(st, 0, 5)
        st.onFocusChanged(false)
        moveCursor(st, 5)
        st.toggleBold()
        // Spans commit, but the collapsed cursor must not move and no highlight
        // may appear: internal selections stay invisible.
        assertEquals(TextRange(5, 5), st.field.selection)
        val runs = runsOf(st)
        assertEquals("hello", runs.first().text)
        assertTrue(runs.first().bold)
    }

    @Test
    fun deliberateMoveArmsTypingAttrInstead() {
        val st = NijiEditorState()
        type(st, "hello world")
        select(st, 0, 5)
        st.onFocusChanged(true) // field still focused: user tapped elsewhere on purpose
        moveCursor(st, 11)
        st.toggleBold() // must NOT restyle the abandoned word
        val runs = runsOf(st)
        assertFalse(runs.first().bold)
        assertTrue("typing attr must be armed", st.displayActive(Attr.BOLD))
    }

    @Test
    fun spanCountStaysBoundedWhileTypingStyled() {
        val st = NijiEditorState()
        st.toggleBold() // arm typing attribute on the empty doc
        var t = ""
        repeat(200) { _ ->
            t += "a"
            type(st, t)
        }
        assertTrue(
            "span list grew unbounded (${st.field.annotatedString.spanStyles.size}) (Bug 3)",
            st.field.annotatedString.spanStyles.size <= 3
        )
        assertTrue(runsOf(st).all { it.bold })
    }

    @Test
    fun saveLoadRoundTrip() {
        val st = NijiEditorState()
        type(st, "Hi there")
        select(st, 0, 2)
        st.toggleBold()
        select(st, 3, 8)
        st.toggleItalic()
        st.toggleHighlight()
        val before = runsOf(st)
        val reloaded = before.toAnnotated(Color.Transparent).toRuns()
        assertEquals(before, reloaded)
    }

    @Test
    fun typingInsideBoldInherits() {
        val st = NijiEditorState()
        type(st, "helloworld")
        select(st, 0, 10)
        st.toggleBold()
        st.update(TextFieldValue("hello world", TextRange(6)))
        assertTrue(runsOf(st).all { it.bold })
    }

    @Test
    fun undoRestoresPlain() {
        val st = NijiEditorState()
        type(st, "hello")
        select(st, 0, 5)
        st.toggleBold()
        assertTrue(runsOf(st).first().bold)
        st.undo()
        assertFalse(runsOf(st).first().bold)
    }

    @Test
    fun checklistCyclesPrefixes() {
        val st = NijiEditorState()
        type(st, "milk")
        moveCursor(st, 0)
        st.toggleChecklist()
        assertTrue(st.field.text.startsWith("☐ "))
        st.toggleChecklist()
        assertTrue(st.field.text.startsWith("☑ "))
    }

    @Test
    fun versionBumpsOnlyOnContentChange() {
        val st = NijiEditorState()
        assertEquals(0, st.version)
        type(st, "hi")
        assertEquals(1, st.version)
        moveCursor(st, 1)
        assertEquals("bare cursor moves must not dirty autosave", 1, st.version)
        select(st, 0, 2)
        st.toggleBold()
        assertEquals(2, st.version)
        st.onFocusChanged(true) // deliberate in-field move…
        moveCursor(st, 2)
        st.toggleItalic() // collapsed flip = typing attr only, no content change
        assertEquals(2, st.version)
    }

    @Test
    fun headButtonCollapsedArmsFutureTextOnly() {
        val st = NijiEditorState()
        type(st, "Hello")
        moveCursor(st, 2)
        st.toggleHead(Attr.HEAD1)
        // Cursor untouched, existing line untouched — only future typing is headed.
        assertEquals(TextRange(2, 2), st.field.selection)
        assertEquals("BODY", runsOf(st).single().head)
        assertTrue("H1 chip must show armed", st.displayActive(Attr.HEAD1))
        // Typing at the cursor inserts headed text mid-line.
        st.update(TextFieldValue("Hexllo", TextRange(3)))
        val runs = runsOf(st)
        assertEquals(3, runs.size)
        assertEquals("BODY", runs[0].head)
        assertEquals("H1", runs[1].head)
        assertEquals("x", runs[1].text)
        assertEquals("BODY", runs[2].head)
    }

    @Test
    fun headRangeConvertsWholeLine() {
        val st = NijiEditorState()
        type(st, "Hello")
        select(st, 1, 3)
        st.toggleHead(Attr.HEAD1)
        assertEquals(TextRange(1, 3), st.field.selection)
        assertEquals("H1", runsOf(st).single().head)
    }

    @Test
    fun bodyButtonCollapsedDisarmsOnly() {
        val st = NijiEditorState()
        type(st, "Hello")
        select(st, 0, 5)
        st.toggleHead(Attr.HEAD1) // range: whole line headed, selection kept
        assertEquals("H1", runsOf(st).single().head)
        moveCursor(st, 2)
        st.clearHead() // collapsed: disarm future typing, line untouched
        assertEquals(TextRange(2, 2), st.field.selection)
        assertEquals("H1", runsOf(st).single().head)
        assertFalse(st.displayActive(Attr.HEAD1))
        // With a real selection, Body converts the line.
        select(st, 0, 5)
        st.clearHead()
        assertEquals("BODY", runsOf(st).single().head)
        assertEquals(TextRange(0, 5), st.field.selection)
    }

    @Test
    fun collapsedCodeArmsOnly() {
        val st = NijiEditorState()
        type(st, "Hello")
        moveCursor(st, 2)
        st.toggleCodeLine()
        assertEquals(TextRange(2, 2), st.field.selection)
        assertFalse(runsOf(st).single().code)
        assertTrue(st.displayActive(Attr.CODE))
    }

    @Test
    fun codeLineKeepsExplicitRange() {
        val st = NijiEditorState()
        type(st, "Hello")
        select(st, 1, 3)
        st.toggleCodeLine()
        assertEquals("line CODE must not force-select the line", TextRange(1, 3), st.field.selection)
        assertTrue(st.displayActive(Attr.CODE))
    }

    private fun note(
        id: String,
        title: String,
        updated: Long,
        created: Long = 0L
    ) = StoredNote(id = id, title = title, text = "", updatedAt = updated, createdAt = created)

    @Test
    fun sortUpdatedNewestFirst() {
        val notes = listOf(note("a", "A", 100), note("b", "B", 300), note("c", "C", 200))
        assertEquals(listOf("b", "c", "a"), sortNotes(notes, "UPDATED").map { it.id })
    }

    @Test
    fun sortCreatedFallsBackToUpdated() {
        val notes = listOf(
            note("a", "A", 100, 1000),
            note("b", "B", 900, 0L),
            note("c", "C", 200, 500)
        )
        assertEquals(listOf("a", "b", "c"), sortNotes(notes, "CREATED").map { it.id })
    }

    @Test
    fun sortTitleCaseInsensitive() {
        val notes = listOf(note("a", "banana", 1), note("b", "Apple", 1), note("c", "cherry", 1))
        assertEquals(listOf("b", "a", "c"), sortNotes(notes, "TITLE").map { it.id })
    }

    @Test
    fun deleteKeepsArmedItalic() {
        val st = NijiEditorState()
        type(st, "ab")
        st.toggleItalic() // Collapsed cursor: arms italic for next typing.
        assertTrue(st.displayActive(Attr.ITALIC))
        // Backspace over a plain char: armed italic must survive.
        st.update(TextFieldValue("a", TextRange(1)))
        assertEquals("a", st.field.text)
        assertTrue("delete must not disarm untouched italic", st.displayActive(Attr.ITALIC))
    }

    @Test
    fun deleteKeepsArmedHighlight() {
        val st = NijiEditorState()
        type(st, "ab")
        st.toggleHighlight() // Collapsed cursor: arms the marker.
        assertTrue(st.displayActive(Attr.HIGHLIGHT))
        st.update(TextFieldValue("a", TextRange(1)))
        assertTrue("delete must not disarm untouched marker", st.displayActive(Attr.HIGHLIGHT))
    }

    @Test
    fun deleteOfStyledSpanClearsAttr() {
        val st = NijiEditorState()
        type(st, "ab")
        select(st, 0, 2)
        st.toggleItalic()
        assertTrue(st.displayActive(Attr.ITALIC))
        // Select-all + delete removes the italic run itself, so it switches off.
        st.update(TextFieldValue("", TextRange(0)))
        assertFalse("deleting the styled text itself must clear italic", st.displayActive(Attr.ITALIC))
    }

    @Test
    fun deleteTailOfItalicStaysArmed() {
        val st = NijiEditorState()
        // Type into armed italic (the real flow: toolbar on, typing styled).
        type(st, "hell")
        select(st, 0, 4)
        st.toggleItalic()
        type(st, "hello")
        assertTrue(st.displayActive(Attr.ITALIC))
        // Backspace the tail: a same-attr run still ends at the cursor, so
        // italic stays armed and typing continues the style (Docs sticky rule).
        st.update(TextFieldValue("hell", TextRange(4)))
        assertTrue("backspacing a styled tail must keep italic", st.displayActive(Attr.ITALIC))
        type(st, "hell!")
        val runs = runsOf(st)
        assertTrue(runs.all { it.italic })
    }
}
