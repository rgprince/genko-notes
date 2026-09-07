package com.rgprince.genkonotes.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rgprince.genkonotes.data.NotesRepository
import com.rgprince.genkonotes.data.StoredNote
import com.rgprince.genkonotes.widget.NoteWidget
import com.rgprince.genkonotes.theme.AnimePack
import com.rgprince.genkonotes.theme.HalftonePaper
import com.rgprince.genkonotes.theme.InkBar
import com.rgprince.genkonotes.theme.NijiTheme
import com.rgprince.genkonotes.theme.PapierTheme
import com.rgprince.genkonotes.theme.rememberPapierPress
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun NijiApp(openNoteId: String? = null, composeNew: Boolean = false) {
    val context = LocalContext.current
    val repo = remember { NotesRepository(context) }
    val scope = rememberCoroutineScope()

    val notes by repo.notesFlow().collectAsState(initial = emptyList())
    val packName by repo.themeFlow().collectAsState(initial = "SHONEN")
    val fontScale by repo.fontScaleFlow().collectAsState(initial = 1.0f)
    val darkPref by repo.darkFlow().collectAsState(initial = "AUTO")
    val sortMode by repo.sortFlow().collectAsState(initial = "UPDATED")
    val defaultFolder by repo.defaultFolderFlow().collectAsState(initial = "Inbox")
    val showDots by repo.dotsFlow().collectAsState(initial = true)
    val marginLine by repo.marginLineFlow().collectAsState(initial = true)

    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var pinnedOnly by remember { mutableStateOf(false) }
    var openId by remember { mutableStateOf<String?>(null) }
    var trashToast by remember { mutableStateOf<StoredNote?>(null) }

    LaunchedEffect(trashToast) {
        if (trashToast != null) {
            kotlinx.coroutines.delay(5000)
            trashToast = null
        }
    }

    // Widget deep links — one-shot (rotation-safe via the handled flag).
    var deepLinkHandled by remember { mutableStateOf(false) }
    LaunchedEffect(openNoteId, composeNew) {
        if (deepLinkHandled) return@LaunchedEffect
        deepLinkHandled = true
        if (composeNew) {
            val now = System.currentTimeMillis()
            val n = StoredNote(
                id = UUID.randomUUID().toString(),
                title = "",
                text = "",
                folder = defaultFolder,
                updatedAt = now,
                createdAt = now
            )
            repo.upsert(n)
            openId = n.id
        } else if (openNoteId != null) {
            openId = openNoteId
        }
    }

    // Keep the home-screen widget in step with every mutation.
    LaunchedEffect(notes) {
        NoteWidget.refresh(context)
    }

    val pack = if (packName == "SHOJO") AnimePack.SHOJO else AnimePack.SHONEN
    val dark = when (darkPref) {
        "LIGHT" -> false
        "DARK" -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    NijiTheme(darkTheme = dark, pack = pack, fontScale = fontScale) {
        @Composable
        fun AppColumn() {
            // Top wears the status bar; the bottom is split on purpose: the home
            // screens keep navigation-bar clearance for the InkBar, while the open
            // editor owns its own bottom inset (nav ∪ keyboard, never the sum —
            // summing them is what floated the toolbar above the keyboard).
            val bottomClearance =
                if (openId == null) Modifier.navigationBarsPadding() else Modifier
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .then(bottomClearance)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // Spatial home<->editor transition (slide + fade, never an instant cut).
                    AnimatedContent(
                        targetState = openId,
                        transitionSpec = {
                            val opening = targetState != null
                            (slideInHorizontally(tween(220, easing = EaseOutCubic)) {
                                if (opening) it / 7 else -it / 7
                            } + fadeIn(tween(140))) togetherWith
                                (slideOutHorizontally(tween(180, easing = EaseOut)) {
                                    if (opening) -it / 7 else it / 7
                                } + fadeOut(tween(120)))
                        },
                        contentAlignment = Alignment.TopStart
                    ) { id ->
                        val open = notes.firstOrNull { it.id == id }
                        if (open != null) {
                        EditorScreen(
                            note = open,
                            marginLine = marginLine,
                            onBack = { updated ->
                                // Back always persists; empty notes vanish instead of cluttering Home.
                                scope.launch {
                                    if (updated.title.isBlank() && updated.text.isBlank()) {
                                        repo.delete(updated.id)
                                    } else {
                                        repo.upsert(updated)
                                    }
                                }
                                openId = null
                            },
                            onSave = { updated ->
                                scope.launch { repo.upsert(updated) }
                            },
                            onDelete = { id ->
                                scope.launch {
                                    notes.firstOrNull { it.id == id }?.let { gone ->
                                        repo.upsert(
                                            gone.copy(
                                                trashed = true,
                                                trashedAt = System.currentTimeMillis()
                                            )
                                        )
                                        trashToast = gone
                                    }
                                }
                                openId = null
                            },
                            onDuplicate = { copy ->
                                scope.launch { repo.upsert(copy) }
                                openId = copy.id
                            },
                            onAutosave = { updated ->
                                scope.launch { repo.upsert(updated) }
                            }
                        )
                    } else {
                        when (tab) {
                            0 -> HomeScreen(
                                notes = notes,
                                query = query,
                                showPinnedOnly = pinnedOnly,
                                sortMode = sortMode,
                                onQuery = { query = it },
                                onTogglePinnedFilter = { pinnedOnly = !pinnedOnly },
                                onOpen = { openId = it.id },
                                onNew = {
                                    val now = System.currentTimeMillis()
                                    val n = StoredNote(
                                        id = UUID.randomUUID().toString(),
                                        title = "",
                                        text = "",
                                        folder = defaultFolder,
                                        updatedAt = now,
                                        createdAt = now
                                    )
                                    scope.launch { repo.upsert(n) }
                                    openId = n.id
                                }
                            )
                            1 -> FoldersScreen(
                                notes = notes,
                                onOpen = { openId = it.id },
                                onNew = {
                                    val now = System.currentTimeMillis()
                                    val n = StoredNote(
                                        id = UUID.randomUUID().toString(),
                                        title = "",
                                        text = "",
                                        folder = defaultFolder,
                                        updatedAt = now,
                                        createdAt = now
                                    )
                                    scope.launch { repo.upsert(n) }
                                    openId = n.id
                                }
                            )
                            2 -> SearchScreen(notes = notes, query = query, onQuery = { query = it }, onOpen = { openId = it.id })
                            else -> SettingsScreen(
                                packName = packName,
                                fontScale = fontScale,
                                darkMode = darkPref,
                                sortMode = sortMode,
                                defaultFolder = defaultFolder,
                                showDots = showDots,
                                marginLine = marginLine,
                                trashed = notes.filter { it.trashed },
                                onPack = { scope.launch { repo.setTheme(it) } },
                                onScale = { scope.launch { repo.setFontScale(it) } },
                                onDark = { scope.launch { repo.setDark(it) } },
                                onSort = { scope.launch { repo.setSort(it) } },
                                onDefaultFolder = { scope.launch { repo.setDefaultFolder(it) } },
                                onDots = { scope.launch { repo.setDots(it) } },
                                onMarginLine = { scope.launch { repo.setMarginLine(it) } },
                                onRestore = { note ->
                                    scope.launch {
                                        repo.upsert(note.copy(trashed = false, trashedAt = 0))
                                    }
                                },
                                onEmptyTrash = {
                                    scope.launch { repo.saveAll(notes.filterNot { it.trashed }) }
                                }
                            )
                        }
                    }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                AnimatedVisibility(
                    visible = trashToast != null && openId == null,
                    enter = slideInVertically(tween(220, easing = EaseOutCubic)) { 48 } +
                        fadeIn(tween(140)),
                    exit = slideOutVertically(tween(180, easing = EaseOut)) { 24 } +
                        fadeOut(tween(120))
                ) {
                    UndoToast(
                        text = "Note moved to Trash",
                        onUndo = {
                            val t = trashToast
                            if (t != null) {
                                scope.launch { repo.upsert(t.copy(trashed = false, trashedAt = 0)) }
                            }
                            trashToast = null
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                if (openId == null) {
                    InkBar(
                        selected = tab,
                        onSelect = { tab = it },
                        onCompose = {
                            val now = System.currentTimeMillis()
                            val n = StoredNote(
                                id = UUID.randomUUID().toString(),
                                title = "",
                                text = "",
                                folder = defaultFolder,
                                updatedAt = now,
                                createdAt = now
                            )
                            scope.launch { repo.upsert(n) }
                            openId = n.id
                        }
                    )
                }
            }
        }
        if (showDots) {
            HalftonePaper(modifier = Modifier.fillMaxSize()) { AppColumn() }
        } else {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(PapierTheme.colors.paper)
            ) {
                AppColumn()
            }
        }
    }
}

/** Ink toast with an undo action (custom, no Material snackbar). */
@Composable
fun UndoToast(
    text: String,
    modifier: Modifier = Modifier,
    onUndo: () -> Unit
) {
    val colors = PapierTheme.colors
    val shape = RoundedCornerShape(12.dp)
    val undoPress = rememberPapierPress()
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.matchParentSize()
                .offset(x = 0.dp, y = 3.dp)
                .clip(shape)
                .background(colors.shadow)
        )
        Row(
            modifier = Modifier.clip(shape)
                .background(colors.paperRaised)
                .border(2.dp, colors.ink, shape)
                .defaultMinSize(minHeight = 48.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                text = text,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                style = TextStyle(
                    color = colors.ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = PapierTheme.type.bodyFamily
                )
            )
            Spacer(modifier = Modifier.width(12.dp))
            BasicText(
                text = "UNDO",
                modifier = Modifier
                    .then(undoPress.modifier)
                    .semantics { contentDescription = "Undo delete" }
                    .clickable(
                        role = Role.Button,
                        indication = null,
                        interactionSource = undoPress.source,
                        onClick = onUndo
                    )
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                style = TextStyle(
                    color = colors.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = PapierTheme.type.bodyFamily
                )
            )
        }
    }
}
