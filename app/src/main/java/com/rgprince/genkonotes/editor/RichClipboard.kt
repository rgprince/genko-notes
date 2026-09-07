package com.rgprince.genkonotes.editor

import android.content.ClipData
import android.content.Context
import android.os.Build

object RichClipboard {
    fun copyText(context: Context, annotated: androidx.compose.ui.text.AnnotatedString, label: String = "GenkoNotes") {
        // Styled HTML payload (b/i/u/s/tt/heads/marker washes) + plain fallback,
        // so our own paste — and any HTML-aware app — keeps the formatting.
        setHtml(context, annotated.text, annotatedToHtml(annotated), label)
    }

    fun setHtml(context: Context, plain: String, html: String, label: String = "GenkoNotes") {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(ClipData.newHtmlText(label, plain, html))
    }

    /** Styled clipboard segments, or null when the clip carries no HTML. */
    fun pasteSegments(context: Context): List<HtmlSeg>? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val html: String = clip.getItemAt(0).htmlText ?: return null
        return parseHtmlSegments(html)
    }

    fun pastePlain(context: Context): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = cm.primaryClip ?: return ""
        if (clip.itemCount == 0) return ""
        return clip.getItemAt(0).coerceToText(context)?.toString() ?: ""
    }
}
