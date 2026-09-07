package com.rgprince.genkonotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rgprince.genkonotes.ui.NijiApp
import com.rgprince.genkonotes.widget.NoteWidget

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Home-widget deep links: open a note or quick-capture a new one.
        val openId = intent.getStringExtra(NoteWidget.EXTRA_OPEN_NOTE)
        val newNote = intent.getBooleanExtra(NoteWidget.EXTRA_NEW_NOTE, false)
        setContent {
            NijiApp(openNoteId = openId, composeNew = newNote)
        }
    }
}
