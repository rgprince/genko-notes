package com.rgprince.genkonotes.editor

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rgprince.genkonotes.theme.CaptionText
import com.rgprince.genkonotes.theme.PapierCard
import com.rgprince.genkonotes.theme.PapierTheme
import com.rgprince.genkonotes.theme.rememberPapierPress
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun InkToolbarButton(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    description: String = label,
    fill: Color? = null,
    onClick: () -> Unit
) {
    val colors = PapierTheme.colors
    val press = rememberPapierPress()
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .then(press.modifier)
            .clip(RoundedCornerShape(10.dp))
            .background(fill ?: if (active) colors.accent else colors.paperRaised)
            .border(1.5.dp, colors.ink, RoundedCornerShape(10.dp))
            .semantics { contentDescription = description }
            .clickable(
                role = Role.Button,
                indication = null,
                interactionSource = press.source,
                onClick = onClick
            )
            // Never steal text-field focus: keeps selection + IME alive while formatting.
            .focusProperties { canFocus = false }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.text.BasicText(
            text = label,
            style = TextStyle(
                color = when {
                    fill != null -> colors.ink
                    active -> colors.accentInk
                    else -> colors.ink
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = PapierTheme.type.bodyFamily
            )
        )
    }
}

/** Ink-bordered sunken panel that groups a toolbar row (anime sticker bar). */
@Composable
fun ToolbarPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = PapierTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.matchParentSize()
                .offset(x = 0.dp, y = 3.dp)
                .clip(shape)
                .background(colors.shadow)
        )
        Box(
            modifier = Modifier.clip(shape)
                .background(colors.paperSunken)
                .border(2.dp, colors.ink, shape)
                .padding(6.dp)
        ) {
            content()
        }
    }
}

/** Hairline divider between toolbar groups. */
@Composable
fun ToolbarDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(1.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(PapierTheme.colors.line)
    )
}

/** Full-width 48dp sheet row for block/overflow menus. */
@Composable
fun SheetRow(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    danger: Boolean = false,
    description: String = label,
    onClick: () -> Unit
) {
    val colors = PapierTheme.colors
    val press = rememberPapierPress()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .then(press.modifier)
            .clip(RoundedCornerShape(8.dp))
            .semantics { contentDescription = description }
            .clickable(
                role = Role.Button,
                indication = null,
                interactionSource = press.source,
                onClick = onClick
            )
            .focusProperties { canFocus = false }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        androidx.compose.foundation.text.BasicText(
            text = (if (active) "● " else "") + label,
            style = TextStyle(
                color = when {
                    danger -> colors.berry
                    active -> colors.accent
                    else -> colors.ink
                },
                fontSize = 16.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                fontFamily = PapierTheme.type.bodyFamily
            )
        )
    }
}

/** 48dp-hitbox marker swatch with a 32dp ink dot. */
@Composable
fun MarkerSwatch(
    ink: HighlightInk,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val colors = PapierTheme.colors
    val press = rememberPapierPress()
    Box(
        modifier = modifier
            .size(48.dp)
            .then(press.modifier)
            .clip(RoundedCornerShape(12.dp))
            .semantics { contentDescription = "Marker ${ink.label}" }
            .clickable(
                role = Role.RadioButton,
                indication = null,
                interactionSource = press.source,
                onClick = onClick
            )
            .focusProperties { canFocus = false }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(ink.toColor().copy(alpha = 1f))
                .border(
                    width = if (selected) 3.dp else 1.5.dp,
                    color = if (selected) colors.accent else colors.ink,
                    shape = CircleShape
                )
        )
    }
}

/**
 * System selection toolbar that carries styles. Native long-press Copy/Cut
 * write the SELECTION as styled HTML (never platform plain text), and native
 * Paste routes through the styled parser. Every interception falls back to
 * the platform default on any failure, so copy/paste can never break.
 */
private class NijiTextToolbar(
    private val state: NijiEditorState,
    private val context: Context,
    private val fallback: TextToolbar
) : TextToolbar {
    override val status: TextToolbarStatus get() = fallback.status

    override fun hide() = fallback.hide()

    private fun selectedHtml(): AnnotatedString? {
        val cur = state.field
        val sel = cur.selection
        if (sel.collapsed) return null
        val s = minOf(sel.start, sel.end).coerceIn(0, cur.text.length)
        val e = maxOf(sel.start, sel.end).coerceIn(0, cur.text.length)
        if (s >= e) return null
        return cur.annotatedString.subSequence(s, e)
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        fallback.showMenu(
            rect = rect,
            onCopyRequested = onCopyRequested?.let { plain ->
                {
                    val sel = runCatching { selectedHtml() }.getOrNull()
                    if (sel == null || sel.text.isEmpty()) {
                        plain()
                    } else {
                        val ok = runCatching {
                            RichClipboard.setHtml(context, sel.text, annotatedToHtml(sel))
                        }.isSuccess
                        if (!ok) plain()
                    }
                }
            },
            onPasteRequested = onPasteRequested?.let { plain ->
                {
                    val ok = runCatching { state.pasteStyled(context) }.getOrDefault(false)
                    if (!ok) plain()
                }
            },
            onCutRequested = onCutRequested?.let { plain ->
                {
                    val sel = runCatching { selectedHtml() }.getOrNull()
                    val copied = sel != null && sel.text.isNotEmpty() && runCatching {
                        RichClipboard.setHtml(context, sel.text, annotatedToHtml(sel))
                    }.isSuccess
                    if (copied) state.deleteSelection() else plain()
                }
            },
            onSelectAllRequested = onSelectAllRequested
        )
    }
}

/** Paper sheet + body field only. Toolbars live outside (docked/wedge). */
@Composable
fun NijiEditor(
    state: NijiEditorState,
    modifier: Modifier = Modifier,
    placeholder: String = "Write…",
    focusRequester: FocusRequester,
    showMarginLine: Boolean = true
) {
    val colors = PapierTheme.colors
    state.codeBg = colors.paperSunken
    val selectionColors = TextSelectionColors(
        handleColor = colors.accent,
        backgroundColor = colors.accent.copy(alpha = 0.28f)
    )
    // Styled system toolbar: long-press copy/cut/paste carry formatting.
    val context = LocalContext.current
    val platformToolbar = LocalTextToolbar.current
    val nijiToolbar = remember(state, context, platformToolbar) {
        NijiTextToolbar(state, context, platformToolbar)
    }
    CompositionLocalProvider(
        LocalTextSelectionColors provides selectionColors,
        LocalTextToolbar provides nijiToolbar
    ) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(12.dp))
                .background(colors.paperRaised)
                .border(2.dp, colors.ink, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            // Notebook binding spine (pure decoration, zero layout impact).
            // Gated by the Margin Line setting. Stronger in dark so it survives.
            if (showMarginLine) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val x = 8.dp.toPx()
                    drawLine(
                        color = colors.accent.copy(alpha = if (colors.isDark) 0.8f else 0.55f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 2.5.dp.toPx()
                    )
                }
            }
            BasicTextField(
                value = state.field,
                onValueChange = { state.update(it) },
                modifier = Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .onFocusChanged { state.onFocusChanged(it.isFocused) }
                    .focusRequester(focusRequester),
                cursorBrush = SolidColor(colors.accent),
                textStyle = TextStyle(
                    color = colors.ink,
                    fontSize = (PapierTheme.type.bodySize.value * PapierTheme.fontScale).sp,
                    fontFamily = PapierTheme.type.bodyFamily,
                    lineHeight = (26f * PapierTheme.fontScale).sp
                ),
                decorationBox = { inner ->
                    // Bottom reserve keeps the last lines clear of the docked
                    // toolbar / wedge overlaying the sheet.
                    Box(modifier = Modifier.padding(bottom = 88.dp)) {
                        if (state.field.text.isEmpty()) {
                            androidx.compose.foundation.text.BasicText(
                                text = placeholder,
                                style = TextStyle(
                                    color = colors.inkFaint,
                                    fontSize = (16f * PapierTheme.fontScale).sp,
                                    fontFamily = PapierTheme.type.bodyFamily,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            )
                        }
                        inner()
                    }
                }
            )
        }
    }
}

/** Docked format toolbar: span toggles + block/marker/overflow triggers. */
@Composable
fun FormatToolbar(
    state: NijiEditorState,
    modifier: Modifier = Modifier,
    onBlockMenu: () -> Unit,
    onMarker: () -> Unit,
    onOverflow: () -> Unit
) {
    val context = LocalContext.current
    ToolbarPanel(modifier = modifier) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                InkToolbarButton(label = "B", active = state.displayActive(Attr.BOLD), description = "Bold") {
                    state.toggleBold()
                }
            }
            item {
                InkToolbarButton(label = "I", active = state.displayActive(Attr.ITALIC), description = "Italic") {
                    state.toggleItalic()
                }
            }
            item {
                InkToolbarButton(label = "U", active = state.displayActive(Attr.UNDERLINE), description = "Underline") {
                    state.toggleUnderline()
                }
            }
            item {
                InkToolbarButton(label = "S", active = state.displayActive(Attr.STRIKE), description = "Strikethrough") {
                    state.toggleStrike()
                }
            }
            item { ToolbarDivider() }
            item {
                InkToolbarButton(label = "¶", description = "Block styles") { onBlockMenu() }
            }
            item {
                InkToolbarButton(
                    label = "✎",
                    description = "Marker color, ${state.highlightInk.label}",
                    fill = state.highlightInk.toColor()
                ) { onMarker() }
            }
            item { ToolbarDivider() }
            item {
                InkToolbarButton(label = "↺", description = "Undo") { state.undo() }
            }
            item {
                InkToolbarButton(label = "↻", description = "Redo") { state.redo() }
            }
            item { ToolbarDivider() }
            item {
                InkToolbarButton(label = "⧉", description = "More actions") { onOverflow() }
            }
        }
    }
}

/** Block menu card: Converts the selected range's line (or arms future typing). */
@Composable
fun BlockMenuSheet(
    state: NijiEditorState,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    PapierCard(modifier = modifier.fillMaxWidth()) {
        SheetRow(
            label = "¶ Body",
            active = !state.displayActive(Attr.HEAD1) && !state.displayActive(Attr.HEAD2),
            description = "Body paragraph"
        ) {
            state.clearHead()
            onDismiss()
        }
        SheetRow(label = "H1", active = state.displayActive(Attr.HEAD1), description = "Heading 1") {
            state.toggleHead(Attr.HEAD1)
            onDismiss()
        }
        SheetRow(label = "H2", active = state.displayActive(Attr.HEAD2), description = "Heading 2") {
            state.toggleHead(Attr.HEAD2)
            onDismiss()
        }
        SheetRow(label = "• Bullet list", active = state.lineHasPrefix("• "), description = "Bullet list") {
            state.toggleBullet()
            onDismiss()
        }
        SheetRow(
            label = "☑ Checklist",
            active = state.lineHasPrefix("☐ ") || state.lineHasPrefix("☑ "),
            description = "Checklist"
        ) {
            state.toggleChecklist()
            onDismiss()
        }
        SheetRow(label = "▌ Quote", active = state.lineHasPrefix("▌ "), description = "Quote") {
            state.toggleQuote()
            onDismiss()
        }
        SheetRow(label = "</> Code", active = state.displayActive(Attr.CODE), description = "Code block") {
            state.toggleCodeLine()
            onDismiss()
        }
    }
}

/** Marker color popover card. Auto-collapses shortly after picking. */
@Composable
fun MarkerPopover(
    state: NijiEditorState,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    PapierCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HighlightInk.entries.filter { it != HighlightInk.NONE }.forEach { ink ->
                MarkerSwatch(
                    ink = ink,
                    selected = state.highlightInk == ink,
                    onClick = {
                        state.highlightInk = ink
                        state.toggleHighlight()
                        scope.launch {
                            delay(150)
                            onDismiss()
                        }
                    }
                )
            }
        }
    }
}

/** Overflow card: clipboard, note actions, folder move, delete. */
@Composable
fun OverflowSheet(
    state: NijiEditorState,
    modifier: Modifier = Modifier,
    folders: List<String>,
    currentFolder: String,
    title: String = "",
    cover: String = "NONE",
    onCover: (String) -> Unit = {},
    onMoveFolder: (String) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    PapierCard(modifier = modifier.fillMaxWidth()) {
        SheetRow(label = "⧉ Copy rich text", description = "Copy with formatting") {
            // Copy the selection when one exists, otherwise the whole note.
            val cur = state.field
            val sel = cur.selection
            val ann = if (sel.collapsed) {
                cur.annotatedString
            } else {
                val s = minOf(sel.start, sel.end).coerceIn(0, cur.text.length)
                val e = maxOf(sel.start, sel.end).coerceIn(0, cur.text.length)
                cur.annotatedString.subSequence(s, e)
            }
            RichClipboard.copyText(context, ann)
            onDismiss()
        }
        SheetRow(label = "Paste", description = "Paste with formatting") {
            if (!state.pasteStyled(context)) {
                val pasted = RichClipboard.pastePlain(context)
            if (pasted.isNotEmpty()) {
                val cur = state.field
                val sel = cur.selection
                val start = minOf(sel.start, sel.end).coerceIn(0, cur.text.length)
                val end = maxOf(sel.start, sel.end).coerceIn(0, cur.text.length)
                val next = cur.text.substring(0, start) + pasted + cur.text.substring(end)
                val rebuilt = androidx.compose.ui.text.buildAnnotatedString {
                    append(next)
                    cur.annotatedString.spanStyles.forEach { r ->
                        if (r.end <= start) addStyle(r.item, r.start, r.end)
                        else if (r.start >= end) addStyle(r.item, r.start + pasted.length, r.end + pasted.length)
                        else {
                            if (r.start < start) addStyle(r.item, r.start, start)
                            if (r.end > end) addStyle(r.item, start + pasted.length, r.end + pasted.length - (end - start))
                        }
                    }
                }
                state.update(cur.copy(annotatedString = rebuilt, selection = androidx.compose.ui.text.TextRange(start + pasted.length)))
            }
            }
            onDismiss()
        }
        SheetRow(label = "⧉ Duplicate note", description = "Duplicate note") {
            onDuplicate()
            onDismiss()
        }
        SheetRow(label = "↗ Share text", description = "Share note text") {
            val body = if (title.isBlank()) state.field.text else "$title\n\n${state.field.text}"
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, body)
            }
            context.startActivity(Intent.createChooser(send, "Share note"))
            onDismiss()
        }
        CaptionText(text = "MOVE TO FOLDER")
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            folders.forEach { f ->
                InkToolbarButton(
                    label = f,
                    active = currentFolder == f,
                    description = "Move to $f",
                    onClick = {
                        onMoveFolder(f)
                        onDismiss()
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        CaptionText(text = "COVER")
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("NONE" to "Paper", "BLUE" to "Blue", "ROSE" to "Rose", "MINT" to "Mint", "LILAC" to "Lilac").forEach { (value, label) ->
                item {
                    InkToolbarButton(
                        label = label,
                        active = cover == value,
                        description = "Cover $label",
                        onClick = { onCover(value) }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        SheetRow(label = "Delete note", danger = true, description = "Delete note") {
            onDelete()
        }
    }
}

@Composable
fun HighlightSwatch(
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(color).border(1.dp, PapierTheme.colors.ink, RoundedCornerShape(6.dp)))
}
