package com.lumen.keyboard

import android.graphics.Color
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

class LumenInputMethodService : InputMethodService() {
    private lateinit var root: LinearLayout
    private lateinit var keyboardArea: LinearLayout
    private lateinit var dictionaryArea: LinearLayout
    private lateinit var smartArea: LinearLayout
    private lateinit var toolboxArea: LinearLayout
    private lateinit var dictionary: DictionaryRepository
    private lateinit var suggestionEngine: SuggestionEngine
    private lateinit var smartEngine: SmartFormatEngine
    private lateinit var learned: UserLanguageModel
    private val history = EditHistory(5)
    private val repeatHandler = Handler(Looper.getMainLooper())
    private val suggestionButtons = mutableListOf<Button>()
    private lateinit var dictionaryQuery: TextView
    private lateinit var dictionaryResult: TextView
    private lateinit var dictionaryInsert: Button
    private var activeEntry: DictionaryEntry? = null
    private var shifted = false
    private var floating = false
    private var heightStep = 0

    private val fontFamilies = listOf(
        "sans-serif", "sans-serif-light", "sans-serif-medium", "sans-serif-condensed",
        "sans-serif-black", "sans-serif-thin", "sans-serif-smallcaps", "serif",
        "serif-monospace", "monospace", "cursive", "casual", "sans-serif-rounded",
        "sans-serif-condensed-light", "sans-serif-condensed-medium", "sans-serif-condensed-bold",
        "sans-serif-medium-italic", "sans-serif-light-italic", "serif-monospace", "sans"
    )

    private val bg = Color.rgb(16, 19, 26)
    private val panel = Color.rgb(26, 31, 43)
    private val key = Color.rgb(42, 49, 64)
    private val accent = Color.rgb(139, 92, 246)
    private val textColor = Color.rgb(248, 250, 252)
    private val muted = Color.rgb(167, 176, 192)

    override fun onCreate() {
        super.onCreate()
        dictionary = DictionaryRepository(this)
        learned = UserLanguageModel(this)
        suggestionEngine = SuggestionEngine(dictionary.allWords(), learned, DictionaryPackStore(this))
        smartEngine = SmartFormatEngine(this)
    }

    override fun onCreateInputView(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(5), dp(4), dp(6))
            setBackgroundColor(bg)
        }
        root.addView(createToolbar())
        root.addView(createSuggestionStrip())
        keyboardArea = createKeyboard()
        dictionaryArea = createDictionaryPanel().apply { visibility = View.GONE }
        smartArea = createSmartPanel().apply { visibility = View.GONE }
        toolboxArea = createToolbox().apply { visibility = View.GONE }
        root.addView(keyboardArea)
        root.addView(dictionaryArea)
        root.addView(smartArea)
        root.addView(toolboxArea)
        root.post { refreshSuggestions() }
        return root
    }

    private fun createToolbar(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(panel)
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        listOf("☰", "Smart", "Dictionary", "Clipboard", "Translate", "☺", "Mic").forEach { label ->
            row.addView(toolbarButton(label).apply {
                if (label == "Dictionary") setBackgroundColor(accent)
                setOnClickListener {
                    when (label) {
                        "☰" -> toggleToolbox()
                        "Dictionary" -> toggleDictionary()
                        "Smart" -> toggleSmart()
                        "Clipboard" -> pasteClipboard()
                        "Translate" -> toggleTranslate()
                        "☺" -> insertEmoji()
                        "Mic" -> showVoiceHelp()
                    }
                }
            })
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun createSuggestionStrip() = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(this@LumenInputMethodService).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(2), dp(3), dp(2), dp(2))
            repeat(6) {
                addView(Button(this@LumenInputMethodService).apply {
                    text = ""
                    isAllCaps = false
                    minWidth = dp(96)
                    setTextColor(textColor)
                    setBackgroundColor(panel)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)).apply {
                        setMargins(dp(2), 0, dp(2), 0)
                    }
                    setOnClickListener { acceptSuggestion(this.text.toString()) }
                    suggestionButtons += this
                })
            }
        })
    }

    private fun toolbarButton(label: String) = Button(this).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        setTextColor(textColor)
        setBackgroundColor(key)
        minWidth = dp(48)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(43)
        ).apply { setMargins(dp(2), 0, dp(2), 0) }
    }

    private fun createKeyboard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(4), 0, 0)
        addView(letterRow("qwertyuiop"))
        addView(letterRow("asdfghjkl", dp(14)))
        addView(letterRow("zxcvbnm", dp(30), includeShift = true, includeBackspace = true))
        addView(bottomRow())
    }

    private fun letterRow(
        letters: String,
        sidePadding: Int = 0,
        includeShift: Boolean = false,
        includeBackspace: Boolean = false
    ) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(sidePadding, 0, sidePadding, 0)
        if (includeShift) addView(keyButton("⇧", 1.35f) { toggleShift() })
        letters.forEach { char ->
            addView(keyButton(char.toString(), 1f) {
                val output = if (shifted) char.uppercaseChar() else char
                commit(output.toString())
                if (shifted) toggleShift()
                refreshSuggestions()
            })
        }
        if (includeBackspace) addView(deleteButton())
    }

    private fun bottomRow() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(keyButton("🌐", 1.1f) { switchToNextInputMethod(false) })
        addView(keyButton(",", 0.8f) { commit(","); refreshSuggestions() })
        addView(keyButton("space", 4f) { commit(" "); refreshSuggestions() })
        addView(keyButton(".", 0.8f) { commit("."); refreshSuggestions() })
        addView(keyButton("↵", 1.25f) {
            val action = currentInputEditorInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            if (action != null && action != EditorInfo.IME_ACTION_NONE) {
                currentInputConnection.performEditorAction(action)
            } else commit("\n")
        })
    }

    private fun refreshSuggestions() {
        if (!::suggestionEngine.isInitialized || suggestionButtons.isEmpty()) return
        if (isSensitiveInput()) {
            suggestionButtons.forEach { it.text = ""; it.isEnabled = false }
            return
        }
        val before = currentInputConnection?.getTextBeforeCursor(120, 0)?.toString().orEmpty()
        val items = suggestionEngine.suggest(before)
        suggestionButtons.forEachIndexed { index, button ->
            button.text = items.getOrNull(index).orEmpty()
            button.isEnabled = index < items.size
        }
    }

    private fun acceptSuggestion(value: String) {
        if (value.isBlank()) return
        val before = currentInputConnection?.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        if (before.lastOrNull()?.isWhitespace() == true) {
            commit("$value ")
        } else {
            val current = Regex("[\\p{L}'-]+$").find(before)?.value.orEmpty()
            if (current.isNotEmpty()) currentInputConnection.deleteSurroundingText(current.length, 0)
            commit("$value ")
        }
        refreshSuggestions()
    }

    private fun keyButton(label: String, weight: Float, action: () -> Unit) = Button(this).apply {
        text = label
        tag = if (label.length == 1 && label[0].isLetter()) "letter" else "action"
        textSize = if (label == "space") 13f else 18f
        isAllCaps = false
        setTextColor(textColor)
        setBackgroundColor(key)
        setPadding(0, 0, 0, 0)
        layoutParams = LinearLayout.LayoutParams(0, dp(48), weight).apply {
            setMargins(dp(2), dp(2), dp(2), dp(2))
        }
        setOnClickListener { action() }
        typeface = Typeface.create(currentFont(), Typeface.NORMAL)
        setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                playKeySound()
                if (settings().getBoolean("popup", true) && label.length == 1) showKeyPopup(view, label)
            }
            false
        }
    }

    private fun deleteButton(): Button {
        val button = keyButton("⌫", 1.35f) { deleteOnce() }
        var started = 0L
        val repeater = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - started
                val amount = when { elapsed > 2_000 -> 5; elapsed > 900 -> 2; else -> 1 }
                repeat(amount) { deleteOnce(record = false) }
                repeatHandler.postDelayed(this, if (elapsed > 1_200) 35 else 85)
            }
        }
        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    started = System.currentTimeMillis(); playKeySound(); deleteOnce(); repeatHandler.postDelayed(repeater, 380); true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { repeatHandler.removeCallbacks(repeater); true }
                else -> true
            }
        }
        return button
    }

    private fun deleteOnce(record: Boolean = true) {
        val removed = currentInputConnection?.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        if (removed.isNotEmpty()) {
            currentInputConnection.deleteSurroundingText(1, 0)
            if (record) history.record(LumenEdit("", removed))
        }
        refreshSuggestions()
    }

    private fun showKeyPopup(anchor: View, label: String) {
        val bubble = TextView(this).apply {
            text = label.uppercase(); textSize = 27f; gravity = Gravity.CENTER
            setTextColor(textColor); setBackgroundColor(accent)
        }
        PopupWindow(bubble, dp(52), dp(64), false).apply {
            isOutsideTouchable = true
            showAsDropDown(anchor, (anchor.width - dp(52)) / 2, -anchor.height - dp(68))
            repeatHandler.postDelayed({ dismiss() }, 180)
        }
    }

    private fun playKeySound() {
        if (settings().getBoolean("sound", true)) {
            (getSystemService(AUDIO_SERVICE) as AudioManager).playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, 0.35f)
        }
    }

    private fun createDictionaryPanel(): LinearLayout {
        val panelView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(panel)
        }
        val searchRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        dictionaryQuery = TextView(this).apply {
            text = "Type a word, then tap Dictionary"
            setTextColor(textColor)
            setBackgroundColor(key)
            setPadding(dp(12), 0, dp(12), 0)
            gravity = Gravity.CENTER_VERTICAL
        }
        dictionaryResult = TextView(this).apply {
            text = "Lumen reads the word immediately before your cursor."
            textSize = 15f
            setTextColor(textColor)
            setPadding(dp(4), dp(10), dp(4), dp(8))
        }
        dictionaryInsert = Button(this).apply {
            text = "Insert word"
            isAllCaps = false
            setTextColor(textColor)
            setBackgroundColor(accent)
            isEnabled = false
        }
        val refresh = Button(this).apply {
            text = "Refresh"
            isAllCaps = false
            setTextColor(textColor)
            setBackgroundColor(accent)
            setOnClickListener { refreshDictionary() }
        }
        searchRow.addView(dictionaryQuery, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(0, 0, dp(6), 0) })
        searchRow.addView(refresh, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)))
        panelView.addView(searchRow)
        panelView.addView(dictionaryResult, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        panelView.addView(dictionaryInsert, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
        panelView.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(238))
        return panelView
    }

    private fun toggleDictionary() {
        val opening = dictionaryArea.visibility != View.VISIBLE
        dictionaryArea.visibility = if (opening) View.VISIBLE else View.GONE
        smartArea.visibility = View.GONE
        toolboxArea.visibility = View.GONE
        keyboardArea.visibility = if (opening) View.GONE else View.VISIBLE
        if (opening) refreshDictionary()
    }

    private fun createSmartPanel(): LinearLayout {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(panel)
        }
        val status = TextView(this).apply {
            text = "Smart Format works offline and upgrades through your HTTPS endpoint."
            setTextColor(textColor)
            textSize = 14f
            setPadding(dp(4), dp(4), dp(4), dp(8))
        }
        val tones = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        Tone.entries.forEach { tone ->
            addView(Button(this@LumenInputMethodService).apply {
                text = tone.name.lowercase().replaceFirstChar { it.uppercase() }
                isAllCaps = false
                textSize = 11f
                setTextColor(textColor)
                setBackgroundColor(key)
                layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(2), 0, dp(2), 0) }
                setOnClickListener { runSmartFormat(SmartAction.REWRITE, tone, status) }
            })
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(this@LumenInputMethodService).apply {
                text = "Fix grammar"
                isAllCaps = false
                setTextColor(textColor)
                setBackgroundColor(accent)
                layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(2), dp(8), dp(2), 0) }
                setOnClickListener { runSmartFormat(SmartAction.GRAMMAR, Tone.PROFESSIONAL, status) }
            })
            addView(Button(this@LumenInputMethodService).apply {
                text = "Back to keys"
                isAllCaps = false
                setTextColor(textColor)
                setBackgroundColor(key)
                layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(2), dp(8), dp(2), 0) }
                setOnClickListener { toggleSmart() }
            })
        }
        container.addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        container.addView(tones)
        container.addView(actions)
        container.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(238))
        return container
    }

    private fun toggleSmart() {
        val opening = smartArea.visibility != View.VISIBLE
        smartArea.visibility = if (opening) View.VISIBLE else View.GONE
        dictionaryArea.visibility = View.GONE
        toolboxArea.visibility = View.GONE
        keyboardArea.visibility = if (opening) View.GONE else View.VISIBLE
    }

    private fun runSmartFormat(action: SmartAction, tone: Tone, status: TextView) {
        if (isSensitiveInput()) {
            status.text = "Smart Format is disabled in password and sensitive fields."
            return
        }
        val selected = currentInputConnection?.getSelectedText(0)?.toString().orEmpty()
        val before = currentInputConnection?.getTextBeforeCursor(500, 0)?.toString().orEmpty()
        val input = selected.ifBlank { currentSentence(before) }
        if (input.isBlank()) {
            status.text = "Select text or place the cursor after a sentence first."
            return
        }
        status.text = "Working…"
        smartEngine.transform(input, action, tone) { result ->
            status.text = "Preview (${result.source}): ${result.output}"
            val apply = Button(this).apply { text = "Apply preview"; isAllCaps = false; setTextColor(textColor); setBackgroundColor(accent) }
            apply.setOnClickListener {
                if (selected.isNotBlank()) currentInputConnection.commitText(result.output, 1)
                else { currentInputConnection.deleteSurroundingText(input.length, 0); currentInputConnection.commitText(result.output, 1) }
                smartArea.removeView(apply); refreshSuggestions()
            }
            smartArea.findViewWithTag<Button>("lumen_apply")?.let { smartArea.removeView(it) }
            apply.tag = "lumen_apply"; smartArea.addView(apply, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
        }
    }

    private fun toggleTranslate() { toggleSmart(); (smartArea.getChildAt(0) as? TextView)?.text = "Translate selected text or the current sentence. The preview appears before Lumen changes anything."; runSmartFormat(SmartAction.TRANSLATE, Tone.PROFESSIONAL, smartArea.getChildAt(0) as TextView) }
    private fun pasteClipboard() { val clip = (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip; val value = clip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty(); if (value.isBlank()) showUtility("Clipboard is empty.") else commit(value) }
    private fun insertEmoji() { commit("🙂"); refreshSuggestions() }
    private fun showVoiceHelp() { showUtility("Voice typing uses Android's system voice input. Tap the microphone on your device keyboard, then return to Lumen.") }
    private fun showUtility(message: String) { dictionaryArea.visibility = View.VISIBLE; smartArea.visibility = View.GONE; toolboxArea.visibility = View.GONE; keyboardArea.visibility = View.GONE; dictionaryQuery.text = "Lumen"; dictionaryResult.text = message; dictionaryInsert.isEnabled = false; dictionaryInsert.text = "Back to keys"; dictionaryInsert.setOnClickListener { toggleDictionary() } }

    private fun currentSentence(beforeCursor: String): String {
        val boundary = maxOf(beforeCursor.lastIndexOf('.'), beforeCursor.lastIndexOf('!'), beforeCursor.lastIndexOf('?'))
        return beforeCursor.substring(boundary + 1).trimStart()
    }

    private fun refreshDictionary() {
        val beforeCursor = currentInputConnection
            ?.getTextBeforeCursor(64, 0)
            ?.toString()
            .orEmpty()
        val query = Regex("[\\p{L}'-]+$").find(beforeCursor)?.value.orEmpty()
        dictionaryQuery.text = if (query.isBlank()) "No word at cursor" else query
        activeEntry = dictionary.lookup(query)
        val entry = activeEntry
        if (entry == null) {
            val matches = dictionary.suggestions(query)
            dictionaryResult.text = when {
                query.isBlank() -> "Type a word in the app, keep the cursor after it, then tap Refresh."
                matches.isEmpty() -> "“$query” is not in the offline starter dictionary."
                else -> "No exact match. Try: ${matches.joinToString()}"
            }
            dictionaryInsert.isEnabled = false
            dictionaryInsert.text = "Insert word"
        } else {
            dictionaryResult.text = "${entry.word} · ${entry.partOfSpeech}\n${entry.meaning}\n\nExample: ${entry.example}\nSynonyms: ${entry.synonyms.joinToString()}"
            dictionaryInsert.text = "Insert “${entry.word}”"
            dictionaryInsert.isEnabled = true
            dictionaryInsert.setOnClickListener { commit(entry.word) }
        }
    }


    private fun toggleShift() {
        shifted = !shifted
        updateLetters(root)
    }

    private fun updateLetters(view: View) {
        if (view is Button && view.tag == "letter") {
            view.text = if (shifted) view.text.toString().uppercase() else view.text.toString().lowercase()
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) updateLetters(view.getChildAt(i))
        }
    }

    private fun createToolbox(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(6), dp(7), dp(6), dp(7))
        setBackgroundColor(panel)
        addView(TextView(this@LumenInputMethodService).apply {
            text = "Lumen Toolbox"; textSize = 15f; setTextColor(textColor); setPadding(dp(6), 0, 0, dp(5))
        })
        val rows = listOf(
            listOf("Theme", "Resize", "Floating", "Font"),
            listOf("Undo", "Redo", "Feedback", "Settings")
        )
        rows.forEach { labels ->
            addView(LinearLayout(this@LumenInputMethodService).apply {
                orientation = LinearLayout.HORIZONTAL
                labels.forEach { label ->
                    addView(Button(this@LumenInputMethodService).apply {
                        text = label; isAllCaps = false; textSize = 11f; setTextColor(textColor); setBackgroundColor(key)
                        layoutParams = LinearLayout.LayoutParams(0, dp(58), 1f).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) }
                        setOnClickListener { handleTool(label) }
                    })
                }
            })
        }
        addView(TextView(this@LumenInputMethodService).apply {
            text = "Undo and redo retain the last 5 Lumen edits."; setTextColor(muted); textSize = 12f; gravity = Gravity.CENTER
        })
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(238))
    }

    private fun toggleToolbox() {
        val opening = toolboxArea.visibility != View.VISIBLE
        toolboxArea.visibility = if (opening) View.VISIBLE else View.GONE
        dictionaryArea.visibility = View.GONE
        smartArea.visibility = View.GONE
        keyboardArea.visibility = if (opening) View.GONE else View.VISIBLE
    }

    private fun handleTool(label: String) {
        when (label) {
            "Theme" -> {
                val themes = listOf(Color.rgb(16, 19, 26), Color.rgb(18, 37, 34), Color.rgb(38, 24, 45), Color.rgb(245, 245, 248))
                val next = (settings().getInt("theme", 0) + 1) % themes.size
                settings().edit().putInt("theme", next).apply(); root.setBackgroundColor(themes[next])
            }
            "Resize" -> {
                heightStep = (heightStep + 1) % 3
                settings().edit().putInt("height", heightStep).apply()
                val height = listOf(42, 48, 56)[heightStep]
                resizeKeys(root, height)
            }
            "Floating" -> {
                floating = !floating
                val inset = if (floating) dp(34) else dp(4)
                root.setPadding(inset, dp(5), inset, dp(8))
            }
            "Font" -> {
                val next = (settings().getInt("font", 0) + 1) % fontFamilies.size
                settings().edit().putInt("font", next).apply(); applyFont(root)
            }
            "Undo" -> undoEdit()
            "Redo" -> redoEdit()
            "Feedback" -> startActivity(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "Lumen Keyboard feedback")
                putExtra(Intent.EXTRA_TEXT, "Lumen Keyboard feedback:\n"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            "Settings" -> startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun undoEdit() {
        history.takeUndo()?.let { edit ->
            if (edit.inserted.isNotEmpty()) currentInputConnection.deleteSurroundingText(edit.inserted.length, 0)
            if (edit.removed.isNotEmpty()) currentInputConnection.commitText(edit.removed, 1)
            refreshSuggestions()
        }
    }

    private fun redoEdit() {
        history.takeRedo()?.let { edit ->
            if (edit.removed.isNotEmpty()) currentInputConnection.deleteSurroundingText(edit.removed.length, 0)
            if (edit.inserted.isNotEmpty()) currentInputConnection.commitText(edit.inserted, 1)
            refreshSuggestions()
        }
    }

    private fun resizeKeys(view: View, heightDp: Int) {
        if (view is Button && (view.tag == "letter" || view.tag == "action")) {
            view.layoutParams = view.layoutParams.apply { height = dp(heightDp) }
        } else if (view is ViewGroup) for (i in 0 until view.childCount) resizeKeys(view.getChildAt(i), heightDp)
    }

    private fun applyFont(view: View) {
        if (view is Button) view.typeface = Typeface.create(currentFont(), Typeface.NORMAL)
        if (view is ViewGroup) for (i in 0 until view.childCount) applyFont(view.getChildAt(i))
    }

    private fun currentFont() = fontFamilies[settings().getInt("font", 0).coerceIn(fontFamilies.indices)]
    private fun settings() = getSharedPreferences("lumen_settings", MODE_PRIVATE)

    private fun commit(value: String) {
        currentInputConnection.commitText(value, 1)
        history.record(LumenEdit(value))
        if (!isSensitiveInput() && value.any { it.isWhitespace() }) {
            val context = currentInputConnection.getTextBeforeCursor(120, 0)?.toString().orEmpty()
            val recent = Regex("[\\p{L}'-]+").findAll(context).map { it.value }.toList().takeLast(2).joinToString(" ")
            learned.learn(recent)
        }
    }

    private fun isSensitiveInput(): Boolean {
        val type = currentInputEditorInfo?.inputType ?: return false
        val variation = type and InputType.TYPE_MASK_VARIATION
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        refreshSuggestions()
    }
}

