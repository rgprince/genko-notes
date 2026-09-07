package com.rgprince.genkonotes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.rgprince.genkonotes.data.StoredNote
import com.rgprince.genkonotes.editor.BlockMenuSheet
import com.rgprince.genkonotes.editor.FormatToolbar
import com.rgprince.genkonotes.editor.MarkerPopover
import com.rgprince.genkonotes.editor.NijiEditor
import com.rgprince.genkonotes.editor.NijiEditorState
import com.rgprince.genkonotes.editor.OverflowSheet
import com.rgprince.genkonotes.editor.toAnnotated
import com.rgprince.genkonotes.editor.toRuns
import com.rgprince.genkonotes.theme.CaptionText
import com.rgprince.genkonotes.theme.PapierTheme
import com.rgprince.genkonotes.theme.rememberPapierPress
import java.util.UUID
import kotlinx.coroutines.delay

/** Storyloom pattern: defer an overlay's enter target one frame so its transition
 *  never skips when the IME and the overlay arrive in the same interaction. */
@Composable
private fun deferredEnter(request: Boolean): Boolean {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(request) {
        if (request) {
            visible = false
            delay(16)
            visible = true
        } else {
            visible = false
        }
    }
    return visible
}

/** 48dp square header button (ghost ink, or accent-filled for Save). */
@Composable
fun EditorHeaderButton(
    glyph: String,
    modifier: Modifier = Modifier,
    description: String = glyph,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    val colors = PapierTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val press = rememberPapierPress()
    Box(
        modifier = modifier
            .size(48.dp)
            .then(press.modifier)
            .clip(shape)
            .background(if (accent) colors.accent else colors.paperRaised)
            .border(2.dp, colors.ink, shape)
            .semantics { contentDescription = description }
            .clickable(
                role = Role.Button,
                indication = null,
                interactionSource = press.source,
                onClick = onClick
            )
            .focusProperties { canFocus = false },
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = glyph,
            style = TextStyle(
                color = if (accent) colors.accentInk else colors.ink,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                fontFamily = PapierTheme.type.bodyFamily
            )
        )
    }
}

@Composable
fun EditorScreen(
    note: StoredNote,
    modifier: Modifier = Modifier,
    marginLine: Boolean = true,
    onBack: (StoredNote) -> Unit,
    onSave: (StoredNote) -> Unit,
    onDelete: (String) -> Unit,
    onDuplicate: (StoredNote) -> Unit,
    onAutosave: (StoredNote) -> Unit
) {
    val colors = PapierTheme.colors
    val codeBg = colors.paperSunken
    val state = remember(note.id) { NijiEditorState(note.runs.toAnnotated(codeBg)) }
    var title by remember(note.id) { mutableStateOf(note.title) }
    var folder by remember(note.id) { mutableStateOf(note.folder) }
    var pinned by remember(note.id) { mutableStateOf(note.pinned) }
    var cover by remember(note.id) { mutableStateOf(note.cover) }
    var justSaved by remember(note.id) { mutableStateOf(false) }
    var showBlocks by remember(note.id) { mutableStateOf(false) }
    var showMarker by remember(note.id) { mutableStateOf(false) }
    var showOverflow by remember(note.id) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(note.id) {
        // Restore the full styled model — never a plain-text downgrade.
        state.update(TextFieldValue(note.runs.toAnnotated(codeBg), TextRange(note.text.length)))
        state.blockKind = note.blockKind
    }

    fun buildNote(): StoredNote {
        val ann = state.field.annotatedString
        return note.copy(
            title = title,
            text = ann.text,
            runs = ann.toRuns(),
            folder = folder,
            pinned = pinned,
            cover = cover,
            blockKind = note.blockKind,
            updatedAt = System.currentTimeMillis()
        )
    }

    // Dirty key: header fields + content generation (bumped on real mutations only,
    // never on bare cursor moves — cheap exact key, no per-recomposition span scans).
    val dirtyKey = title + "\n" + folder + "\n" + pinned + "\n" + cover + "\n" + state.version
    var lastSavedKey by remember(note.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(dirtyKey) {
        if (lastSavedKey == null) {
            lastSavedKey = dirtyKey
            return@LaunchedEffect
        }
        if (dirtyKey == lastSavedKey) return@LaunchedEffect
        delay(1500)
        onAutosave(buildNote())
        lastSavedKey = dirtyKey
    }

    LaunchedEffect(justSaved) {
        if (justSaved) {
            delay(1200)
            justSaved = false
        }
    }

    fun closeSheet() {
        showBlocks = false
        showMarker = false
        showOverflow = false
    }

    // Back closes sheets first (storyloom pattern); a clean back always saves,
    // exactly like the ‹ header button — no silent 1.5s autosave loss.
    BackHandler(enabled = showBlocks || showMarker || showOverflow) { closeSheet() }
    BackHandler { onBack(buildNote()) }

    fun doSave() {
        val snapshot = buildNote()
        lastSavedKey = title + "\n" + folder + "\n" + pinned + "\n" + cover + "\n" + state.version
        onSave(snapshot)
        justSaved = true
    }

    val words = state.wordCount()
    val minutes = maxOf(1, (words + 199) / 200)

    // Bottom inset = max(nav bar, keyboard), never the sum. imePadding() alone
    // stacked on top of the app's nav-bar clearance and floated the dock a
    // nav-bar-height above the keyboard; max() lands it flush on the IME.
    // (Storyloom glides on the same single-inset idea at its Scaffold level.)
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val imeBottom = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    Box(modifier = modifier.fillMaxSize().padding(bottom = max(navBottom, imeBottom))) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Slim 56dp ink header: back · status · pin · save.
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EditorHeaderButton(glyph = "‹", description = "Back, save note") {
                    onBack(buildNote())
                }
                Spacer(modifier = Modifier.width(12.dp))
                CaptionText(
                    text = "${folder.uppercase()} · $words WORDS · $minutes MIN",
                    modifier = Modifier.weight(1f)
                )
                EditorHeaderButton(
                    glyph = if (pinned) "★" else "☆",
                    description = if (pinned) "Unpin note" else "Pin note"
                ) { pinned = !pinned }
                Spacer(modifier = Modifier.width(8.dp))
                EditorHeaderButton(glyph = "✓", description = "Save note", accent = true) {
                    doSave()
                }
            }
            TitleField(value = title, onValueChange = { title = it })
            Spacer(modifier = Modifier.height(4.dp))
            NijiEditor(
                state = state,
                modifier = Modifier.fillMaxWidth().weight(1f),
                focusRequester = focusRequester,
                showMarginLine = marginLine
            )
        }
        // Save flash overlays (never inserts — TitleField must not jump on save).
        AnimatedVisibility(
            visible = justSaved,
            enter = fadeIn(tween(150)) + slideInVertically(tween(150)) { -12 },
            exit = fadeOut(tween(300)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp)
        ) {
            BasicText(
                text = "✓ Saved",
                modifier = Modifier.semantics { contentDescription = "Note saved" },
                style = TextStyle(
                    color = colors.moss,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = PapierTheme.type.monoFamily
                )
            )
        }
        // Writing mode: single toolbar docked above the keyboard. The dock fades +
        // grows from the bottom edge (storyloom rail pattern); sheets slide in above it.
        AnimatedVisibility(
            visible = state.isFocused,
            enter = fadeIn(tween(120)) + expandVertically(tween(180), expandFrom = Alignment.Bottom),
            exit = fadeOut(tween(90)) + shrinkVertically(tween(140), shrinkTowards = Alignment.Bottom),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                val blocksGo = deferredEnter(showBlocks)
                AnimatedVisibility(
                    visible = blocksGo,
                    enter = slideInVertically(tween(220)) { it / 3 } + fadeIn(tween(140)),
                    exit = fadeOut(tween(100)) + shrinkVertically(tween(160), shrinkTowards = Alignment.Bottom)
                ) {
                    Column {
                        BlockMenuSheet(state = state, onDismiss = { showBlocks = false })
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                AnimatedVisibility(
                    visible = showMarker,
                    enter = slideInVertically(tween(220)) { it / 3 } + fadeIn(tween(140)),
                    exit = fadeOut(tween(100)) + shrinkVertically(tween(160), shrinkTowards = Alignment.Bottom)
                ) {
                    Column {
                        MarkerPopover(state = state, onDismiss = { showMarker = false })
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                val overflowGo = deferredEnter(showOverflow)
                AnimatedVisibility(
                    visible = overflowGo,
                    enter = slideInVertically(tween(220)) { it / 3 } + fadeIn(tween(140)),
                    exit = fadeOut(tween(100)) + shrinkVertically(tween(160), shrinkTowards = Alignment.Bottom)
                ) {
                    Column {
                        OverflowSheet(
                            state = state,
                            folders = listOf("Inbox", "Ideas", "Diary"),
                            currentFolder = folder,
                            title = title,
                            cover = cover,
                            onCover = { cover = it },
                            onMoveFolder = { folder = it },
                            onDuplicate = {
                                onDuplicate(
                                    buildNote().copy(
                                        id = UUID.randomUUID().toString(),
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                            },
                            onDelete = { onDelete(note.id) },
                            onDismiss = { showOverflow = false }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
                FormatToolbar(
                    state = state,
                    onBlockMenu = {
                        val open = !showBlocks
                        closeSheet()
                        showBlocks = open
                    },
                    onMarker = {
                        val open = !showMarker
                        closeSheet()
                        showMarker = open
                    },
                    onOverflow = {
                        val open = !showOverflow
                        closeSheet()
                        showOverflow = open
                    }
                )
            }
        }
        // Reading mode: lone compose wedge in the corner (scale + fade).
        AnimatedVisibility(
            visible = !state.isFocused,
            enter = scaleIn(tween(200)) + fadeIn(tween(140)),
            exit = scaleOut(tween(140)) + fadeOut(tween(100)),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Box(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 16.dp)
            ) {
                val wedgeShape = RoundedCornerShape(16.dp)
                val wedgePress = rememberPapierPress()
                Box(modifier = Modifier.size(56.dp)) {
                    Box(
                        modifier = Modifier.matchParentSize()
                            .offset(x = 0.dp, y = 3.dp)
                            .clip(wedgeShape)
                            .background(colors.shadow)
                    )
                    Box(
                        modifier = Modifier.matchParentSize()
                            .then(wedgePress.modifier)
                            .clip(wedgeShape)
                            .background(colors.accent)
                            .border(2.dp, colors.ink, wedgeShape)
                            .semantics { contentDescription = "Show formatting tools" }
                            .clickable(
                                role = Role.Button,
                                indication = null,
                                interactionSource = wedgePress.source,
                                onClick = {
                                    focusRequester.requestFocus()
                                    keyboard?.show()
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = "✎",
                            style = TextStyle(
                                color = colors.accentInk,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black
                            )
                        )
                    }
                }
            }
        }
    }
}
