# Spellcheck Postmortem — removed, do not re-add without reading this

> Status (2026-09-06): the entire spellcheck feature was REMOVED from the app
> after it never worked on the test device despite five fix rounds. The code was
> deleted (see git history); this file preserves what happened so the next attempt
> doesn't repeat it.

## What was built

Custom spell + grammar overlay on the WYSIWYG editor, all display-only
(never entered the saved span model):

- `SpellCheck.kt` — system client via `TextServicesManager` +
  `SpellCheckerSession.getSentenceSuggestions(TextInfo[], int)` (the
  non-deprecated sentence path), chunked requests, red spelling underlines.
- Local grammar heuristics (repeated words, a/an agreement), blue underlines.
- `SpellPopup.kt` — word-anchored suggestion card (caret, primary action,
  Ignore, session ignore list).
- Manual `ABC✓` trigger + status caption + `adb logcat -s NijiSpell` tracing.

## Fix timeline (all shipped, feature still dead on device)

1. **Looper crash (proven by logcat).** Session was created on `Dispatchers.IO`;
   `newSpellCheckerSession()` builds a `Handler` internally and threw
   `Can't create handler inside thread...`, latching `disabled = true` forever.
   Fixed by creating the session on `Dispatchers.Main.immediate`.
2. **Recall gap (16 vs 99+ elsewhere).** Added: grammar-flag handling
   (`RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR`), typo-without-suggestions,
   `DONT_SHOW_UI` respect, `-1` count handling, silent-service detection,
   sentence-aware chunking (ICU BreakIterator), keyboard-locale sessions.
3. **Locale instability across keyboards.** Pinned sessions to
   `Locale.getDefault()` instead of the keyboard subtype.
4. **Still not working.** Feature removed pending real study.

## Prime suspects (UNVERIFIED — study these first next time)

1. **System spell checker toggle OFF on device**
   (Settings → System → Languages → Spell checker). Gboard's *inline*
   underlines work independently of this toggle, which fully explains
   "works in other apps but not ours". The ABC✓ readout printed
   `enabled=… checkers=… locale=…` for exactly this diagnosis — check it first.
2. **Service idle until Gboard activates.** Known Gboard behavior: the spell
   check service may not respond until Gboard has been opened at least once
   since boot.
3. **Hollow/dead spellchecker service on the device.** Known on some OEMs
   (documented for Samsung; Xiaomi-class devices can behave the same).
   Session creates fine but every request returns empty.
4. **IME suppressing the service** (`isInputMethodSuppressingSpellChecker`
   makes the API return zero-length results by design).

## Lessons / do-not-repeat checklist

- **"Works in other apps" proves nothing.** Gboard inline checking is a
  different engine on a different toggle from the `SpellCheckerSession` API.
  Never treat other apps' underlines as proof the API works on a device.
- **Surface service state in-app from day one** (enabled flag, checker count,
  locale) before building any overlay UI.
- **Session creation MUST run on a Looper thread.** Off-main creation crashes.
- **Read the full flag contract before parsing results**: grammar flag,
  typo-without-suggestions, `DONT_SHOW_UI`, `-1` counts.
- **Chunk on sentence boundaries**; log chunk counts + per-chunk result counts.
- **Get a device logcat BEFORE theorizing.** The only proven bug in this whole
  saga (the Looper crash) was found in the log within seconds.
- **Compare recall on a fixed paragraph counting flagged WORDS**, not suggestions.
- **Keep the checker out of the saved model.** The display-only overlay design
  was correct and should be reused (merge at render, reflow from pure model).

## Revisit plan (when studied)

1. Fresh logcat with ABC✓ taps on a known-typo paragraph; read the
   `enabled=/checkers=/locale=` line first.
2. If the service is hollow on this device, stop fighting the API and pick an
   alternative: bundled Hunspell dictionaries (offline, full control),
   LanguageTool API (needs network), or Gboard proofread integration.
3. Re-add incrementally: session + logging + counts first; overlay UI only
   after recall is verified on-device against a fixed test paragraph.
