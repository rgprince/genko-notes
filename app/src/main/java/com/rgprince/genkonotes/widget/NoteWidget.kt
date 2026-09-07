package com.rgprince.genkonotes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.rgprince.genkonotes.MainActivity
import com.rgprince.genkonotes.R
import com.rgprince.genkonotes.data.NotesRepository
import com.rgprince.genkonotes.data.StoredNote

/**
 * Zero-dependency home widget: 3 recent notes + quick capture.
 * Reads MMKV directly (same process mmap); refreshed from NijiApp on every
 * notes change plus the standard onUpdate path. No Glance, no new deps.
 */
class NoteWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        refresh(context)
    }

    companion object {
        const val EXTRA_OPEN_NOTE = "extra_open_note"
        const val EXTRA_NEW_NOTE = "extra_new_note"

        fun refresh(context: Context) {
            val app = context.applicationContext
            val mgr = AppWidgetManager.getInstance(app)
            val ids = mgr.getAppWidgetIds(ComponentName(app, NoteWidget::class.java))
            if (ids.isEmpty()) return
            val notes = runCatching { NotesRepository(app).snapshot() }
                .getOrDefault(emptyList())
                .filterNot { it.trashed }
                .sortedWith(
                    compareByDescending<StoredNote> { it.pinned }
                        .thenByDescending { it.updatedAt }
                )
                .take(3)
            ids.forEach { id ->
                runCatching { mgr.updateAppWidget(id, views(app, notes)) }
            }
        }

        private fun views(context: Context, notes: List<StoredNote>): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_notes)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

            val newIntent = Intent(context, MainActivity::class.java)
                .setAction("com.rgprince.genkonotes.NEW_NOTE")
                .setData(Uri.parse("genko://new"))
                .putExtra(EXTRA_NEW_NOTE, true)
            rv.setOnClickPendingIntent(
                R.id.widget_new,
                PendingIntent.getActivity(context, 1001, newIntent, flags)
            )

            val slots = intArrayOf(R.id.widget_slot0, R.id.widget_slot1, R.id.widget_slot2)
            val titles = intArrayOf(R.id.widget_title0, R.id.widget_title1, R.id.widget_title2)
            val snippets = intArrayOf(R.id.widget_snippet0, R.id.widget_snippet1, R.id.widget_snippet2)
            notes.forEachIndexed { i, n ->
                if (i >= 3) return@forEachIndexed
                rv.setViewVisibility(slots[i], View.VISIBLE)
                rv.setTextViewText(titles[i], n.title.ifBlank { "Untitled" })
                rv.setTextViewText(snippets[i], n.text.ifBlank { "No text" })
                val open = Intent(context, MainActivity::class.java)
                    .setAction("com.rgprince.genkonotes.OPEN_NOTE")
                    .setData(Uri.parse("genko://note/${n.id}"))
                    .putExtra(EXTRA_OPEN_NOTE, n.id)
                rv.setOnClickPendingIntent(
                    slots[i],
                    PendingIntent.getActivity(context, 2000 + i, open, flags)
                )
            }
            for (i in notes.size until 3) {
                rv.setViewVisibility(slots[i], View.GONE)
            }
            rv.setViewVisibility(
                R.id.widget_empty,
                if (notes.isEmpty()) View.VISIBLE else View.GONE
            )
            return rv
        }
    }
}
