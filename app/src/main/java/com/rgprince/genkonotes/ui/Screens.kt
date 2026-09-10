package com.rgprince.genkonotes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rgprince.genkonotes.data.StoredNote
import com.rgprince.genkonotes.theme.BodySnippetText
import com.rgprince.genkonotes.theme.CaptionText
import com.rgprince.genkonotes.theme.NoteTitleText
import com.rgprince.genkonotes.theme.PapierButton
import com.rgprince.genkonotes.theme.PapierButtonVariant
import com.rgprince.genkonotes.theme.PapierCard
import com.rgprince.genkonotes.theme.PapierChip
import com.rgprince.genkonotes.theme.PapierDivider
import com.rgprince.genkonotes.theme.PapierSearchField
import com.rgprince.genkonotes.theme.PapierSwitch
import com.rgprince.genkonotes.theme.PapierTheme
import kotlinx.coroutines.delay

/** Wash tint for a note cover name, or null for NONE/plain. */
@Composable
fun coverWash(cover: String): androidx.compose.ui.graphics.Color? {
    val c = PapierTheme.colors
    return when (cover) {
        "BLUE" -> c.washBlue
        "ROSE" -> c.washRose
        "MINT" -> c.washMint
        "LILAC" -> c.washLilac
        else -> null
    }
}

/** Small wash dot shown before folder captions on covered notes. */
@Composable
fun CoverDot(cover: String, modifier: Modifier = Modifier) {
    val wash = coverWash(cover) ?: return
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(wash)
            .border(1.dp, PapierTheme.colors.ink, RoundedCornerShape(5.dp))
    )
}

/** Shared empty-state card: glyph + title + one line + one button. */
@Composable
fun EmptyInkCard(
    glyph: String,
    title: String,
    body: String,
    buttonText: String,
    onButton: () -> Unit,
    modifier: Modifier = Modifier
) {
    PapierCard(modifier = modifier.fillMaxWidth()) {
        androidx.compose.foundation.text.BasicText(
            text = glyph,
            style = TextStyle(
                color = PapierTheme.colors.inkFaint,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black
            )
        )
        Spacer(modifier = Modifier.height(6.dp))
        NoteTitleText(text = title)
        Spacer(modifier = Modifier.height(6.dp))
        BodySnippetText(text = body)
        Spacer(modifier = Modifier.height(10.dp))
        PapierButton(text = buttonText, onClick = onButton)
    }
}

/** Pure sort for the notes grid (unit-tested). Modes: UPDATED / CREATED / TITLE. */
fun sortNotes(notes: List<StoredNote>, mode: String): List<StoredNote> {
    return when (mode) {
        "CREATED" -> notes.sortedByDescending { if (it.createdAt > 0) it.createdAt else it.updatedAt }
        "TITLE" -> notes.sortedBy { (it.title.ifBlank { it.text }).lowercase() }
        else -> notes.sortedByDescending { it.updatedAt }
    }
}

@Composable
fun HomeScreen(
    notes: List<StoredNote>,
    query: String,
    modifier: Modifier = Modifier,
    showPinnedOnly: Boolean = false,
    sortMode: String = "UPDATED",
    onQuery: (String) -> Unit,
    onTogglePinnedFilter: () -> Unit,
    onOpen: (StoredNote) -> Unit,
    onNew: () -> Unit
) {
    val colors = PapierTheme.colors
    val filtered = remember(notes, query, showPinnedOnly, sortMode) {
        sortNotes(
            notes.filter {
                !it.trashed &&
                    (!showPinnedOnly || it.pinned) &&
                    (query.isBlank() || it.title.contains(query, true) || it.text.contains(query, true))
            },
            sortMode
        )
    }
    Column(modifier = modifier.fillMaxSize()) {
        val liveCount = notes.count { !it.trashed }
        if (notes.isEmpty()) {
            NoteTitleText(text = "Good morning ✎")
        } else {
            CaptionText(text = "$liveCount NOTES")
            Spacer(modifier = Modifier.height(4.dp))
            NoteTitleText(text = "Notes")
        }
        Spacer(modifier = Modifier.height(10.dp))
        PapierSearchField(value = query, onValueChange = onQuery)
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PapierChip(text = "All", selected = !showPinnedOnly, onClick = { if (showPinnedOnly) onTogglePinnedFilter() })
            PapierChip(text = "★ Pinned", selected = showPinnedOnly, onClick = { if (!showPinnedOnly) onTogglePinnedFilter() })
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (filtered.isEmpty()) {
            if (query.isBlank() && !showPinnedOnly) {
                EmptyInkCard(
                    glyph = "✎",
                    title = "A blank page…",
                    body = "Tap + New to start.",
                    buttonText = "New Note",
                    onButton = onNew
                )
            } else {
                EmptyInkCard(
                    glyph = "⌕",
                    title = "Nothing here",
                    body = "Try a different filter.",
                    buttonText = "Show all",
                    onButton = {
                        onQuery("")
                        if (showPinnedOnly) onTogglePinnedFilter()
                    }
                )
            }
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Adaptive(160.dp),
                modifier = Modifier.fillMaxSize(),
                verticalItemSpacing = 12.dp,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filtered, key = { it.id }) { note ->
                    PapierCard(
                        modifier = Modifier.fillMaxWidth(),
                        selected = note.pinned,
                        tint = coverWash(note.cover),
                        onClick = { onOpen(note) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (note.pinned) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.accent)
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    androidx.compose.foundation.text.BasicText(
                                        text = "★",
                                        style = TextStyle(
                                            color = colors.accentInk,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            CoverDot(cover = note.cover)
                            if (note.cover != "NONE") Spacer(modifier = Modifier.width(6.dp))
                            CaptionText(text = note.folder.uppercase())
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        NoteTitleText(text = note.title.ifBlank { "Untitled" }, maxLines = 2)
                        Spacer(modifier = Modifier.height(4.dp))
                        BodySnippetText(text = note.text, maxLines = 3)
                        val cardWords = note.text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                        if (cardWords > 30) {
                            Spacer(modifier = Modifier.height(8.dp))
                            CaptionText(text = "$cardWords words")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FoldersScreen(
    notes: List<StoredNote>,
    modifier: Modifier = Modifier,
    onOpen: (StoredNote) -> Unit,
    onNew: () -> Unit
) {
    val colors = PapierTheme.colors
    val folders = notes.filterNot { it.trashed }.groupBy { it.folder.ifBlank { "Inbox" } }
    if (folders.isEmpty()) {
        Column(modifier = modifier.fillMaxSize()) {
            NoteTitleText(text = "Folders ▤")
            Spacer(modifier = Modifier.height(12.dp))
            EmptyInkCard(
                glyph = "▤",
                title = "No folders yet",
                body = "Notes live in Inbox.",
                buttonText = "New Note",
                onButton = onNew
            )
        }
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            NoteTitleText(text = "Folders ▤")
        }
        folders.forEach { (folder, items) ->
            item(key = "h-$folder") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.accent)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            text = "${items.size}",
                            style = TextStyle(color = colors.accentInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    NoteTitleText(text = folder)
                }
            }
            items(items, key = { it.id }) { note ->
                PapierCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpen(note) }
                ) {
                    NoteTitleText(text = note.title.ifBlank { "Untitled" }, maxLines = 1)
                    Spacer(modifier = Modifier.height(2.dp))
                    BodySnippetText(text = note.text, maxLines = 2)
                }
            }
        }
    }
}

@Composable
fun SearchScreen(
    notes: List<StoredNote>,
    query: String,
    modifier: Modifier = Modifier,
    onQuery: (String) -> Unit,
    onOpen: (StoredNote) -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        NoteTitleText(text = "Search ⌕")
        Spacer(modifier = Modifier.height(8.dp))
        PapierSearchField(value = query, onValueChange = onQuery, hint = "Search")
        Spacer(modifier = Modifier.height(12.dp))
        val results = notes.filter {
            !it.trashed &&
                (query.isBlank() || it.title.contains(query, true) || it.text.contains(query, true) ||
                    it.tags.any { t -> t.contains(query, true) })
        }
        if (query.isNotBlank() && results.isEmpty()) {
            EmptyInkCard(
                glyph = "⌕",
                title = "No ink matches “$query”",
                body = "Try fewer letters.",
                buttonText = "Clear search",
                onButton = { onQuery("") }
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(results, key = { it.id }) { note ->
                    PapierCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { onOpen(note) }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CoverDot(cover = note.cover)
                            if (note.cover != "NONE") Spacer(modifier = Modifier.width(6.dp))
                            CaptionText(text = note.folder.uppercase())
                        }
                        NoteTitleText(text = note.title.ifBlank { "Untitled" }, maxLines = 1)
                        BodySnippetText(text = note.text, maxLines = 2)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    packName: String,
    modifier: Modifier = Modifier,
    fontScale: Float,
    darkMode: String,
    sortMode: String,
    defaultFolder: String,
    showDots: Boolean,
    marginLine: Boolean,
    trashed: List<StoredNote>,
    onPack: (String) -> Unit,
    onScale: (Float) -> Unit,
    onDark: (String) -> Unit,
    onSort: (String) -> Unit,
    onDefaultFolder: (String) -> Unit,
    onDots: (Boolean) -> Unit,
    onMarginLine: (Boolean) -> Unit,
    onRestore: (StoredNote) -> Unit,
    onEmptyTrash: () -> Unit
) {
    val colors = PapierTheme.colors
    var confirmEmpty by remember { mutableStateOf(false) }
    LaunchedEffect(confirmEmpty) {
        if (confirmEmpty) {
            delay(3000)
            confirmEmpty = false
        }
    }
    LazyColumn(modifier = modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            NoteTitleText(text = "Settings ⚙")
        }
        item {
            CaptionText(text = "APPEARANCE")
        }
        item {
            PapierCard(modifier = Modifier.fillMaxWidth()) {
                CaptionText(text = "TYPEFACE")
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PapierChip(text = "SHONEN", selected = packName == "SHONEN", onClick = { onPack("SHONEN") })
                    PapierChip(text = "SHOJO POP", selected = packName == "SHOJO", onClick = { onPack("SHOJO") })
                }
                Spacer(modifier = Modifier.height(8.dp))
                BodySnippetText(
                    text = if (packName == "SHONEN") "Shonen: thick title-card display + rounded body."
                    else "Shojo Pop: marker handwritten display + soft maru body."
                )
                Spacer(modifier = Modifier.height(8.dp))
                PapierDivider()
                Spacer(modifier = Modifier.height(8.dp))
                CaptionText(text = "PAPER MODE")
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("AUTO", "LIGHT", "DARK").forEach { m ->
                        PapierChip(text = m, selected = darkMode == m, onClick = { onDark(m) })
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                PapierDivider()
                Spacer(modifier = Modifier.height(8.dp))
                PapierSwitch(
                    label = "Paper texture",
                    description = "Flat dot grid behind lists.",
                    checked = showDots,
                    onToggle = { onDots(!showDots) }
                )
            }
        }
        item {
            CaptionText(text = "READING & WRITING")
        }
        item {
            PapierCard(modifier = Modifier.fillMaxWidth()) {
                CaptionText(text = "READING SIZE")
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.9f to "S", 1.0f to "M", 1.15f to "L", 1.3f to "XL").forEach { (v, label) ->
                        PapierChip(text = label, selected = fontScale == v, onClick = { onScale(v) })
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                PapierDivider()
                Spacer(modifier = Modifier.height(8.dp))
                PapierSwitch(
                    label = "Margin line",
                    description = "The red notebook rule on the editor's left edge.",
                    checked = marginLine,
                    onToggle = { onMarginLine(!marginLine) }
                )
                Spacer(modifier = Modifier.height(8.dp))
                PapierDivider()
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        CaptionText(text = "AUTOSAVE")
                        Spacer(modifier = Modifier.height(2.dp))
                        BodySnippetText(text = "1.5s after you stop typing.")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.moss)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            text = "ON",
                            style = TextStyle(
                                color = if (colors.isDark) colors.paper else androidx.compose.ui.graphics.Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        )
                    }
                }
            }
        }
        item {
            CaptionText(text = "NOTES")
        }
        item {
            PapierCard(modifier = Modifier.fillMaxWidth()) {
                CaptionText(text = "SORT ORDER")
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PapierChip(text = "UPDATED", selected = sortMode == "UPDATED", onClick = { onSort("UPDATED") })
                    PapierChip(text = "CREATED", selected = sortMode == "CREATED", onClick = { onSort("CREATED") })
                    PapierChip(text = "A–Z", selected = sortMode == "TITLE", onClick = { onSort("TITLE") })
                }
                Spacer(modifier = Modifier.height(8.dp))
                CaptionText(text = "DEFAULT FOLDER")
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Inbox", "Ideas", "Diary").forEach { f ->
                        PapierChip(text = f, selected = defaultFolder == f, onClick = { onDefaultFolder(f) })
                    }
                }
            }
        }
        item {
            CaptionText(text = "TRASH (${trashed.size})", color = colors.berry)
        }
        item {
            PapierCard(modifier = Modifier.fillMaxWidth(), borderOverride = colors.berry) {
                if (trashed.isEmpty()) {
                    BodySnippetText(text = "Trash is empty.")
                } else {
                    trashed.sortedByDescending { it.trashedAt }.forEachIndexed { i, note ->
                        if (i > 0) {
                            Spacer(modifier = Modifier.height(6.dp))
                            PapierDivider()
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BodySnippetText(
                                text = note.title.ifBlank { "Untitled" },
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            PapierChip(text = "Restore", selected = false, onClick = { onRestore(note) })
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    PapierButton(
                        text = if (confirmEmpty) "Tap again to confirm" else "Empty trash",
                        variant = PapierButtonVariant.Danger,
                        onClick = {
                            if (confirmEmpty) {
                                onEmptyTrash()
                                confirmEmpty = false
                            } else {
                                confirmEmpty = true
                            }
                        }
                    )
                }
            }
        }
        item {
            CaptionText(text = "ABOUT & SYSTEM")
        }
        item {
            PapierCard(modifier = Modifier.fillMaxWidth(), ghost = true) {
                BodySnippetText(text = "Genko Notes v1.0.1 — ink on paper.")
                Spacer(modifier = Modifier.height(4.dp))
                CaptionText(text = "Widget: 3 recent notes + quick capture.")
            }
        }
    }
}

@Composable
fun TitleField(
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    val colors = PapierTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = false,
        maxLines = 3,
        cursorBrush = SolidColor(colors.accent),
        textStyle = TextStyle(
            color = colors.ink,
            fontSize = (22f * PapierTheme.fontScale).sp,
            fontWeight = com.rgprince.genkonotes.theme.displayWeightForPack(PapierTheme.pack),
            fontFamily = PapierTheme.type.displayFamily,
            lineHeight = 28.sp
        ),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                androidx.compose.foundation.text.BasicText(
                    text = "Title",
                    style = TextStyle(color = colors.inkFaint, fontSize = 22.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                )
            }
            inner()
        }
    )
}
