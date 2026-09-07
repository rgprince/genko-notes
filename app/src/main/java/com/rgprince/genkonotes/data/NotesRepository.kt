package com.rgprince.genkonotes.data

import android.content.Context
import com.rgprince.genkonotes.editor.InlineRun
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class StoredNote(
    val id: String,
    val title: String,
    val text: String,
    /** Styled runs — the real formatted model. `text` stays as plain fallback/search index. */
    val runs: List<InlineRun> = emptyList(),
    val folder: String = "Inbox",
    val tags: List<String> = emptyList(),
    val pinned: Boolean = false,
    val blockKind: String = "paragraph",
    val updatedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = 0L,
    val trashed: Boolean = false,
    val trashedAt: Long = 0L,
    val cover: String = "NONE"
)

/**
 * MMKV-backed store (mmap, synchronous, main-thread safe — no apply/sync dance).
 * Same public API as before (StateFlows + suspend setters) so no UI changes.
 * First launch imports the legacy notes.json + SharedPreferences exactly once.
 */
class NotesRepository(context: Context) {
    private val appContext = context.applicationContext
    private val kv: MMKV = MMKV.mmkvWithID("genko")
    private val json = Json { ignoreUnknownKeys = true }

    init {
        importLegacyOnce()
    }

    private val _notes = MutableStateFlow(loadNotes())
    private val _theme = MutableStateFlow(kv.decodeString(THEME_KEY, "SHONEN") ?: "SHONEN")
    private val _fontScale = MutableStateFlow(kv.decodeFloat(FONT_SCALE_KEY, 1.0f))
    private val _dark = MutableStateFlow(kv.decodeString(DARK_KEY, "AUTO") ?: "AUTO")
    private val _sort = MutableStateFlow(kv.decodeString(SORT_KEY, "UPDATED") ?: "UPDATED")
    private val _defaultFolder =
        MutableStateFlow(kv.decodeString(DEFAULT_FOLDER_KEY, "Inbox") ?: "Inbox")
    private val _dots = MutableStateFlow(kv.decodeBool(DOTS_KEY, true))
    private val _marginLine = MutableStateFlow(kv.decodeBool(MARGIN_LINE_KEY, true))

    fun notesFlow(): Flow<List<StoredNote>> = _notes.asStateFlow()

    /** Synchronous snapshot for the home-screen widget (same process, MMKV mmap). */
    fun snapshot(): List<StoredNote> = _notes.value

    suspend fun saveAll(notes: List<StoredNote>) {
        kv.encode(NOTES_KEY, json.encodeToString(notes))
        _notes.value = notes
    }

    suspend fun upsert(note: StoredNote) {
        val cur = _notes.value.toMutableList()
        val i = cur.indexOfFirst { it.id == note.id }
        if (i >= 0) cur[i] = note else cur.add(0, note)
        saveAll(cur)
    }

    suspend fun delete(id: String) {
        saveAll(_notes.value.filterNot { it.id == id })
    }

    fun themeFlow(): Flow<String> = _theme.asStateFlow()

    suspend fun setTheme(pack: String) {
        kv.encode(THEME_KEY, pack)
        _theme.value = pack
    }

    fun fontScaleFlow(): Flow<Float> = _fontScale.asStateFlow()

    suspend fun setFontScale(v: Float) {
        kv.encode(FONT_SCALE_KEY, v)
        _fontScale.value = v
    }

    fun darkFlow(): Flow<String> = _dark.asStateFlow()

    suspend fun setDark(v: String) {
        kv.encode(DARK_KEY, v)
        _dark.value = v
    }

    fun sortFlow(): Flow<String> = _sort.asStateFlow()

    suspend fun setSort(v: String) {
        kv.encode(SORT_KEY, v)
        _sort.value = v
    }

    fun defaultFolderFlow(): Flow<String> = _defaultFolder.asStateFlow()

    suspend fun setDefaultFolder(v: String) {
        kv.encode(DEFAULT_FOLDER_KEY, v)
        _defaultFolder.value = v
    }

    fun dotsFlow(): Flow<Boolean> = _dots.asStateFlow()

    suspend fun setDots(v: Boolean) {
        kv.encode(DOTS_KEY, v)
        _dots.value = v
    }

    fun marginLineFlow(): Flow<Boolean> = _marginLine.asStateFlow()

    suspend fun setMarginLine(v: Boolean) {
        kv.encode(MARGIN_LINE_KEY, v)
        _marginLine.value = v
    }

    private fun loadNotes(): List<StoredNote> {
        return try {
            val raw = kv.decodeString(NOTES_KEY) ?: return seedNotes()
            json.decodeFromString<List<StoredNote>>(raw)
        } catch (_: Exception) {
            seedNotes()
        }.filterNot { n ->
            // Auto-purge trash older than 30 days on launch.
            n.trashed && n.trashedAt > 0 &&
                System.currentTimeMillis() - n.trashedAt > 30L * 24 * 60 * 60 * 1000
        }
    }

    private fun importLegacyOnce() {
        if (kv.decodeBool(IMPORTED_KEY, false)) return
        try {
            val notesFile = File(appContext.filesDir, "notes.json")
            if (notesFile.exists()) {
                kv.encode(NOTES_KEY, notesFile.readText())
                try {
                    notesFile.delete()
                } catch (_: Exception) {
                }
            }
            val prefs = appContext.getSharedPreferences("genko_prefs", Context.MODE_PRIVATE)
            prefs.getString("theme_pack", null)?.let { kv.encode(THEME_KEY, it) }
            prefs.getString("font_scale", null)?.toFloatOrNull()?.let { kv.encode(FONT_SCALE_KEY, it) }
            prefs.getString("dark_mode", null)?.let { kv.encode(DARK_KEY, it) }
        } catch (_: Exception) {
        }
        kv.encode(IMPORTED_KEY, true)
    }

    private fun seedNotes(): List<StoredNote> {
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        return listOf(
            StoredNote(
                id = "seed-1",
                title = "Groceries",
                text = "- Potatoes 1kg\n- Onions 2kg\n- Tomatoes 1kg\n- Bread + milk\n- Pasta 500g\n\nDon't forget cheese, guests coming tomorrow.",
                folder = "Inbox",
                pinned = true,
                updatedAt = now,
                createdAt = now - day
            ),
            StoredNote(
                id = "seed-2",
                title = "Weekend trip plan",
                text = "End of month, 4 people. Checked trains — sleeper works.\n\n- Hostel near beach, 2 nights\n- Rent scooters for a day\n- Celebrate Mike's birthday there\n\nCollect advance from everyone first.",
                folder = "Ideas",
                updatedAt = now - 2 * day,
                createdAt = now - 4 * day
            ),
            StoredNote(
                id = "seed-3",
                title = "Meeting notes",
                text = "Saturday 11am, settle the shop account.\n\n- Old balance: 4,500\n- New order: 12 boxes\n- Take a photo of the bill\n\nDon't be late this time.",
                folder = "Inbox",
                updatedAt = now - 3 * day,
                createdAt = now - 3 * day
            ),
            StoredNote(
                id = "seed-4",
                title = "Mom's birthday gift",
                text = "Ideas:\n- Blue scarf she liked\n- New mixer jar, old one broke\n- Framed childhood photo\n\nBudget around $40 max.",
                folder = "Personal",
                updatedAt = now - 5 * day,
                createdAt = now - 6 * day
            ),
            StoredNote(
                id = "seed-5",
                title = "Pasta recipe",
                text = "Boil pasta with salt, 8 min.\nSauce: olive oil, garlic, onion, tomatoes, chili flakes.\nParmesan on top. Best with garlic bread.",
                folder = "Personal",
                updatedAt = now - 8 * day,
                createdAt = now - 8 * day
            )
        )
    }

    private companion object {
        const val NOTES_KEY = "notes_json"
        const val THEME_KEY = "theme_pack"
        const val FONT_SCALE_KEY = "font_scale"
        const val DARK_KEY = "dark_mode"
        const val SORT_KEY = "sort_mode"
        const val DEFAULT_FOLDER_KEY = "default_folder"
        const val DOTS_KEY = "halftone_dots"
        const val MARGIN_LINE_KEY = "margin_line"
        const val IMPORTED_KEY = "mmkv_imported"
    }
}
