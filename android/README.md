# Lumen Keyboard — Milestone 3

Native Android IME built in Kotlin. This milestone adds next-word and spelling
suggestions plus hybrid Smart Format while preserving Dictionary-first UX.

## Included

- Android `InputMethodService` with letter, shift, delete, space, punctuation,
  enter/action, and next-keyboard controls.
- Dictionary-first toolbar; Dictionary replaces the traditional GIF position.
- Fully offline lookup of the word immediately before the cursor, with meaning,
  part of speech, example, synonyms, prefix suggestions, and an insert-word action.
- Setup activity with shortcuts to enable and select Lumen Keyboard.
- Three-slot cursor-aware suggestion strip for prefix completion, spelling
  correction, and lightweight next-word prediction.
- Smart Format grammar correction and rewriting with Simple, Professional,
  Formal, and Casual tones.
- Offline text cleanup with automatic fallback when the network is unavailable.
- Optional HTTPS backend endpoint and per-device private-beta token; no AI provider secret is stored in the APK.
- Clipboard paste, Translate preview, Emoji insertion and Android voice-typing guidance.
- Accelerating hold-to-delete: deletion speeds up in stages while held.
- Optional keypress sound and enlarged key-preview popups.
- Private on-device learning for personal vocabulary and word-pair predictions.
- Expanded six-candidate suggestion strip combining learned words, spelling,
  completions, next-word predictions, and an installed language pack.
- Lumen Toolbox containing Theme, Resize, Floating, Font, Undo, Redo, Feedback,
  and Settings controls.
- Five-level Lumen edit undo and redo history.
- Twenty selectable keyboard key font styles.
- Disk-backed SQLite language-pack index designed for up to 1,000,000 entries.

## Open and run

1. Open this folder in Android Studio.
2. Allow Gradle sync to finish (JDK 17, Android SDK 35).
3. Run the `app` configuration on an Android 8.0+ device or emulator.
4. Tap **Enable Lumen Keyboard**, enable it in Android settings, return to the
   app, then tap **Choose Keyboard**.
5. Open any text field and select Lumen Keyboard. Type a word, leave the cursor
   after it, and tap **Dictionary**.
6. To enable server-enhanced Smart Format, paste `https://kakaos.name.ng/api/assist`
   and a private-beta access token in the setup app, then tap **Save secure endpoint**.
   Leaving either blank keeps the feature fully offline.
7. Optional: install a licensed UTF-8 `.txt.gz` language pack from Settings.
   Each line must contain `word` or `word frequency`. Imports are capped at
   1,000,000 valid entries and indexed on disk to protect IME memory.

## Privacy

Personal words and word-pair counts remain in the app's private local storage.
They can be erased from Settings. The optional Smart Format server receives
only selected text or the current sentence when invoked. Suggestions, learning,
and Smart Format are automatically disabled in password and sensitive fields.

## Secure Smart Format API contract

Lumen sends only the selected text or current sentence to the configured HTTPS
endpoint. It never embeds an AI provider key in the app.

Request:

```json
{"text":"i cant attend","action":"grammar","tone":"professional"}
```

Response:

```json
{"text":"I can't attend."}
```

Accepted actions are `grammar`, `rewrite`, and `translate`. Accepted tones are `simple`,
`professional`, `formal`, and `casual`.

## Release-readiness status

This is an engineering prototype, not a production release. Core IME input,
offline lookup, local suggestions, Smart Format, HTTPS enforcement, timeouts,
and offline fallback are implemented. Before release, add a production-scale
language model/dictionary, device tests, accessibility review, privacy policy,
backend authentication/rate limiting, crash reporting, and signed releases.

The importer and million-entry index are included, but a third-party million-word
dataset is deliberately not bundled without a verified redistribution licence.

## Next milestone

Add authenticated backend sessions, production-scale language data, clipboard,
translation, emoji, and voice controls. Then run a physical-device reliability
matrix across Android 8–15.

