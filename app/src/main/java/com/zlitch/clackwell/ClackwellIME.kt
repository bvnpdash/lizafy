package com.zlitch.clackwell

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.InputMethodSubtype
import android.os.IBinder
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast

class ClackwellIME : InputMethodService(), KeyboardView.Listener, SharedPreferences.OnSharedPreferenceChangeListener {
    private lateinit var prefs: Prefs
    private lateinit var sound: SoundEngine
    private lateinit var haptics: Haptics
    private var keys: KeyboardView? = null
    private var toolbar: ToolbarView? = null
    private var emojiPanel: EmojiPanel? = null
    private var listPanel: ListPanel? = null
    private val shift = ShiftState()
    private val dictionaries = HashMap<Language, Suggester>()
    private val loading = HashSet<Language>()
    private val main = Handler(Looper.getMainLooper())
    /** Some apps (Instagram, WebViews, Google search) report cursor changes late or never, so re-check after each edit. */
    private val recheck = Runnable { updateSuggestions() }
    private var offers: Suggestions? = null
    /** True right after a tapped suggestion added its own space. */
    private var autoSpace = false
    private var page = Page.LETTERS
    private var selStart = 0
    private var selEnd = 0
    /** In memory only, cleared when the keyboard process ends. Never written to disk. */
    private val clips = ArrayList<String>()

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        sound = SoundEngine(this)
        haptics = Haptics(this)
        sound.prepare()
        prefs.store.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroy() {
        prefs.store.unregisterOnSharedPreferenceChangeListener(this)
        main.removeCallbacksAndMessages(null)
        sound.release()
        clips.clear()
        super.onDestroy()
    }

    override fun onCreateInputView(): View {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        val bar = ToolbarView(this) { onTool(it) }
        bar.onPick = { pickSuggestion(it) }
        val deck = KeyboardView(this)
        deck.listener = this
        val emoji = EmojiPanel(this, { typeEmoji(it) }, { showDeck() }, {
            feedback(Cue.SPACE, Tap.SPACE); currentInputConnection?.commitText(" ", 1)
        }, { repeat ->
            if (repeat == 0 || repeat % 2 == 0) feedback(Cue.BACKSPACE, if (repeat == 0) Tap.HEAVY else Tap.SOFT)
            delete(DeleteRepeat.deletesWords(repeat))
        })
        val list = ListPanel(this)
        val stack = FrameLayout(this)
        stack.addView(deck, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
        stack.addView(emoji, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, deck.desiredHeight()))
        stack.addView(list, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, deck.desiredHeight()))
        emoji.visibility = View.GONE
        list.visibility = View.GONE
        root.addView(bar, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(stack, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        // If the system draws a navigation bar over the keyboard window, keep keys above it.
        root.setOnApplyWindowInsetsListener { _, insets ->
            @Suppress("DEPRECATION")
            stack.setPadding(0, 0, 0, insets.systemWindowInsetBottom)
            insets
        }
        stack.setBackgroundColor(prefs.theme.bgBottom)
        keys = deck; toolbar = bar; emojiPanel = emoji; listPanel = list
        offers = null
        refreshLayout()
        return root
    }

    /** Keep the host app visible in landscape instead of the full-screen text box. */
    override fun onEvaluateFullscreenMode() = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        selStart = info.initialSelStart
        selEnd = info.initialSelEnd
        if (!restarting) shift.reset()
        val kind = info.inputType and InputType.TYPE_MASK_CLASS
        page = if (kind == InputType.TYPE_CLASS_NUMBER || kind == InputType.TYPE_CLASS_PHONE || kind == InputType.TYPE_CLASS_DATETIME)
            Page.SYMBOLS_1 else Page.LETTERS
        sound.prepare()
        showDeck()
        keys?.setEnterLabel(info.actionLabel?.toString() ?: EnterRules.label(info.imeOptions))
        refreshLayout()
        updateAutoShift()
        autoSpace = false
        loadDictionary(prefs.currentLanguage)
        updateSuggestions()
    }

    override fun onWindowShown() {
        super.onWindowShown()
        if (prefs.openSound) sound.play(Cue.OPEN)
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        keys?.cancelTouches()
        emojiPanel?.stop()
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int,
                                   candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selStart = newSelStart
        selEnd = newSelEnd
        updateAutoShift()
        updateSuggestions()
    }

    override fun onSharedPreferenceChanged(store: SharedPreferences?, key: String?) {
        if (key == Prefs.RECENT || key == Prefs.CURRENT_LANGUAGE) return
        if (key == Prefs.STYLE) sound.prepare()
        if (key == Prefs.SUGGESTIONS) { loadDictionary(prefs.currentLanguage); updateSuggestions() }
        if (key == Prefs.THEME || key == Prefs.KEY_SIZE || key == Prefs.HEIGHT || key == Prefs.SPACING ||
            key == Prefs.BACKGROUND || key == Prefs.LANGUAGES || key == Prefs.CAPITAL_LEGENDS || key == Prefs.NUMBER_HINTS) {
            if (keys == null) return
            // Rebuild the whole input view so every part picks up the new size and colours.
            setInputView(onCreateInputView())
        }
    }

    // ---------------------------------------------------------------- key events

    private fun feedback(cue: Cue, tap: Tap) { sound.play(cue); haptics.tap(tap) }

    override fun onKeyDown(key: KeyDef) {
        when (key.type) {
            KeyType.CHAR -> feedback(if (key.label.length == 1 && key.label[0].isDigit()) Cue.NUMBER else Cue.KEY, Tap.KEY)
            KeyType.SPACE -> feedback(Cue.SPACE, Tap.SPACE)
            KeyType.SHIFT, KeyType.SYMBOLS, KeyType.SYMBOLS2, KeyType.LETTERS, KeyType.LANGUAGE -> feedback(Cue.SHIFT, Tap.SOFT)
            KeyType.BACKSPACE -> feedback(Cue.BACKSPACE, Tap.HEAVY)
            KeyType.ENTER -> feedback(Cue.ENTER, Tap.CARRIAGE)
            KeyType.EMOJI -> feedback(Cue.EMOJI, Tap.SOFT)
            KeyType.MIC -> feedback(Cue.EMOJI, Tap.SOFT)
            KeyType.GAP -> Unit
        }
    }

    override fun onKeyReleased(key: KeyDef) {
        if (key.type == KeyType.CHAR || key.type == KeyType.SPACE) sound.play(Cue.RELEASE)
    }

    override fun onKey(key: KeyDef, text: String) {
        when (key.type) {
            KeyType.CHAR -> typeCharacter(if (page == Page.LETTERS && shift.upper) TextRules.upper(text) else text)
            KeyType.SPACE -> typeSpace()
            KeyType.BACKSPACE -> delete(false)
            KeyType.ENTER -> enter()
            KeyType.SYMBOLS -> switchPage(Page.SYMBOLS_1)
            KeyType.SYMBOLS2 -> switchPage(Page.SYMBOLS_2)
            KeyType.LETTERS -> switchPage(Page.LETTERS)
            KeyType.EMOJI -> showEmoji()
            KeyType.LANGUAGE -> nextLanguage()
            KeyType.MIC -> startVoiceTyping()
            KeyType.SHIFT, KeyType.GAP -> Unit
        }
    }

    override fun onShiftDown() { shift.press(android.os.SystemClock.uptimeMillis()); showShift() }
    override fun onShiftUp() { shift.release(); showShift() }

    override fun onDeleteRepeat(repeat: Int) {
        if (repeat % 2 == 0) feedback(Cue.BACKSPACE, Tap.SOFT)
        delete(DeleteRepeat.deletesWords(repeat))
    }

    override fun onCursorMove(steps: Int) {
        val code = if (steps < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        // Stop at the ends of the text, otherwise the arrow key can move focus to another field.
        val ic = currentInputConnection ?: return
        for (i in 0 until Math.min(Math.abs(steps), 12)) {
            val neighbour = if (steps < 0) ic.getTextBeforeCursor(1, 0) else ic.getTextAfterCursor(1, 0)
            if (neighbour.isNullOrEmpty() && selStart == selEnd) break
            sendDownUpKeyEvents(code)
        }
        haptics.tap(Tap.SOFT)
        afterEdit()
    }

    override fun onLanguageHold() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    // ---------------------------------------------------------------- editing

    private fun isRawField(): Boolean = (currentInputEditorInfo?.inputType ?: 0) == InputType.TYPE_NULL

    private fun typeCharacter(value: String) {
        val ic = currentInputConnection ?: return
        // "word " + "," becomes "word, " after a tapped suggestion.
        if (autoSpace && value.length == 1 && value[0] in ".,!?;:" && selStart == selEnd &&
            ic.getTextBeforeCursor(1, 0)?.toString() == " ") {
            autoSpace = false
            ic.beginBatchEdit()
            ic.deleteSurroundingText(1, 0)
            ic.commitText("$value ", 1)
            ic.endBatchEdit()
            shift.onCharacterTyped()
            updateAutoShift()
            afterEdit()
            return
        }
        autoSpace = false
        if (isRawField() && value.length == 1 && value[0] < 128.toChar()) sendKeyChar(value[0]) else ic.commitText(value, 1)
        shift.onCharacterTyped()
        showShift()
        updateAutoShift()
        afterEdit()
    }

    private fun allowsPeriodShortcut(): Boolean {
        val type = currentInputEditorInfo?.inputType ?: return false
        if (type and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        val variation = type and InputType.TYPE_MASK_VARIATION
        return variation != InputType.TYPE_TEXT_VARIATION_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_URI &&
            variation != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS &&
            variation != InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
    }

    private fun typeSpace() {
        val ic = currentInputConnection ?: return
        // The suggestion already added this space.
        if (autoSpace && ic.getTextBeforeCursor(1, 0)?.toString() == " ") { autoSpace = false; return }
        autoSpace = false
        val before = if (prefs.doubleSpacePeriod && selStart == selEnd && allowsPeriodShortcut()) ic.getTextBeforeCursor(2, 0) else null
        if (before != null && TextRules.doubleSpaceMakesPeriod(before)) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
            ic.endBatchEdit()
        } else ic.commitText(" ", 1)
        shift.onCharacterTyped()
        showShift()
        updateAutoShift()
        afterEdit()
        // After punctuation or a space on the symbol page, go back to letters.
        if (page != Page.LETTERS && (currentInputEditorInfo?.inputType ?: 0) and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT) {
            switchPage(Page.LETTERS)
        }
    }

    private fun delete(wholeWord: Boolean) {
        autoSpace = false
        val ic = currentInputConnection ?: return
        if (isRawField()) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL); return }
        if (!ic.getSelectedText(0).isNullOrEmpty() || selStart != selEnd) { ic.commitText("", 1); afterEdit(); return }
        val before = ic.getTextBeforeCursor(64, 0)
        if (before.isNullOrEmpty()) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL); afterEdit(); return }
        val length = if (wholeWord) TextRules.lastWordLength(before) else TextRules.lastClusterLength(before)
        ic.deleteSurroundingText(Math.max(1, length), 0)
        updateAutoShift()
        afterEdit()
    }

    private fun enter() {
        autoSpace = false
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val options = info?.imeOptions ?: 0
        if (info?.actionLabel != null && info.actionId != 0) ic.performEditorAction(info.actionId)
        else if (EnterRules.kind(options) == EnterKind.ACTION) ic.performEditorAction(options and EditorInfo.IME_MASK_ACTION)
        else sendKeyChar('\n')
        shift.onCharacterTyped()
        updateAutoShift()
        afterEdit()
    }

    /** Refresh suggestions right after we edit the text, without waiting for the app to report the new cursor. */
    private fun afterEdit() {
        updateSuggestions()
        main.removeCallbacks(recheck)
        main.postDelayed(recheck, 90)
    }

    private fun showShift() { keys?.setShift(if (page == Page.LETTERS) shift.mode else ShiftMode.OFF) }

    private fun updateAutoShift() {
        if (keys == null) return
        val type = currentInputEditorInfo?.inputType ?: 0
        val caps = prefs.autoCaps && page == Page.LETTERS && type != InputType.TYPE_NULL &&
            (currentInputConnection?.getCursorCapsMode(type) ?: 0) != 0
        shift.setAuto(caps)
        showShift()
    }

    private fun switchPage(target: Page) { page = target; refreshLayout(); updateAutoShift(); updateSuggestions() }

    private fun refreshLayout() {
        keys?.setLayout(Layouts.build(page, prefs.currentLanguage))
        showShift()
        keys?.setEnterLabel(currentInputEditorInfo?.actionLabel?.toString() ?: EnterRules.label(currentInputEditorInfo?.imeOptions ?: 0))
    }

    // ---------------------------------------------------------------- suggestions

    /**
     * Off for passwords, email fields and non-text fields. Search boxes (URI/web-edit variations) and
     * fields that set NO_SUGGESTIONS (Instagram, Google) still get the word bar: it only shows taps,
     * it never underlines or auto-corrects anything in the app.
     */
    private fun allowsSuggestions(): Boolean {
        if (!prefs.suggestions) return false
        val type = currentInputEditorInfo?.inputType ?: return false
        if (type and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        val variation = type and InputType.TYPE_MASK_VARIATION
        return variation != InputType.TYPE_TEXT_VARIATION_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS &&
            variation != InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
    }

    /** Reads the word list off the main thread so the keyboard never stalls while opening. */
    private fun loadDictionary(language: Language) {
        if (!prefs.suggestions || language in dictionaries || language in loading) return
        loading.add(language)
        Thread {
            val code = TextRules.lower(language.code)
            try {
                val words = assets.open("dict/$code.txt").bufferedReader(Charsets.UTF_8).use { it.readLines() }
                val built = Suggester(words.filter { it.isNotEmpty() }, language == Language.ENGLISH)
                main.post { loading.remove(language); dictionaries[language] = built; updateSuggestions() }
            } catch (e: Exception) {
                main.post { loading.remove(language) }
            }
        }.start()
    }

    private fun wordAtCursor(): String {
        val ic = currentInputConnection ?: return ""
        if (!ic.getSelectedText(0).isNullOrEmpty()) return ""
        val word = Suggester.currentWord(ic.getTextBeforeCursor(48, 0) ?: return "")
        // Don't offer replacements while the cursor sits inside a word.
        return if (word.isEmpty() || Suggester.startsWithLetter(ic.getTextAfterCursor(1, 0))) "" else word
    }

    private fun updateSuggestions() {
        val bar = toolbar ?: return
        val dictionary = dictionaries[prefs.currentLanguage]
        val word = if (page == Page.LETTERS && allowsSuggestions() && dictionary != null) wordAtCursor() else ""
        if (word == (offers?.literal ?: "") && word.isNotEmpty()) return
        offers = if (word.isEmpty()) null else dictionary?.suggest(word)
        bar.setSuggestions(offers)
    }

    private fun pickSuggestion(index: Int) {
        val current = offers ?: return
        val ic = currentInputConnection ?: return
        val choice = if (index == 0) current.literal else current.words.getOrNull(index - 1) ?: return
        if (wordAtCursor() != current.literal) { updateSuggestions(); return }
        feedback(Cue.EMOJI, Tap.SOFT)
        ic.beginBatchEdit()
        ic.deleteSurroundingText(current.literal.length, 0)
        ic.commitText("$choice ", 1)
        ic.endBatchEdit()
        autoSpace = true
        offers = null
        toolbar?.setSuggestions(null)
        shift.onCharacterTyped()
        updateAutoShift()
    }

    // ---------------------------------------------------------------- voice typing

    /** Hands over to an installed voice-typing keyboard. This app never touches the microphone. */
    private fun startVoiceTyping() {
        if (!allowsVoice()) { toast("Voice typing is off in password fields"); return }
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        for (method in manager.enabledInputMethodList) {
            if (method.packageName == packageName) continue
            val voice = manager.getEnabledInputMethodSubtypeList(method, true).firstOrNull { it.mode == "voice" } ?: continue
            keys?.cancelTouches()
            if (Build.VERSION.SDK_INT >= 28) {
                InputMethodService::class.java.getMethod("switchInputMethod", String::class.java, InputMethodSubtype::class.java)
                    .invoke(this, method.id, voice)
            } else {
                val token = window?.window?.attributes?.token ?: return
                @Suppress("DEPRECATION")
                manager.setInputMethodAndSubtype(token, method.id, voice)
            }
            return
        }
        toast("No voice typing found. Turn on Google voice typing in Android's keyboard settings.")
    }

    private fun allowsVoice(): Boolean {
        val type = currentInputEditorInfo?.inputType ?: return true
        val variation = type and InputType.TYPE_MASK_VARIATION
        if (type and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_NUMBER)
            return variation != InputType.TYPE_NUMBER_VARIATION_PASSWORD
        return variation != InputType.TYPE_TEXT_VARIATION_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD &&
            variation != InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    // ---------------------------------------------------------------- language switching

    private fun nextLanguage() {
        val enabled = prefs.enabledLanguages
        if (enabled.size > 1) {
            prefs.currentLanguage = enabled[(enabled.indexOf(prefs.currentLanguage) + 1) % enabled.size]
            page = Page.LETTERS
            refreshLayout()
            updateAutoShift()
            offers = null
            toolbar?.setSuggestions(null)
            loadDictionary(prefs.currentLanguage)
            updateSuggestions()
        } else switchKeyboard()
    }

    /** Only one layout enabled: hop to the user's next keyboard app, or show the system picker. */
    private fun switchKeyboard() {
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        var switched = false
        if (Build.VERSION.SDK_INT >= 28) {
            val method = InputMethodService::class.java.getMethod("switchToNextInputMethod", Boolean::class.javaPrimitiveType)
            switched = method.invoke(this, false) == true
        } else {
            val token: IBinder? = window?.window?.attributes?.token
            @Suppress("DEPRECATION")
            if (token != null) switched = manager.switchToNextInputMethod(token, false)
        }
        if (!switched) manager.showInputMethodPicker()
    }

    // ---------------------------------------------------------------- toolbar and panels

    private fun showDeck() {
        emojiPanel?.stop()
        emojiPanel?.visibility = View.GONE
        listPanel?.visibility = View.GONE
        keys?.visibility = View.VISIBLE
        toolbar?.active = null
    }

    private fun showPanel(panel: View?, tool: Tool) {
        showDeck()
        keys?.cancelTouches()
        keys?.visibility = View.INVISIBLE
        panel?.visibility = View.VISIBLE
        toolbar?.active = tool
    }

    private fun showEmoji() {
        emojiPanel?.refresh()
        showPanel(emojiPanel, Tool.EMOJI)
    }

    private fun typeEmoji(emoji: String) {
        autoSpace = false
        feedback(Cue.EMOJI, Tap.SOFT)
        currentInputConnection?.commitText(emoji, 1)
        afterEdit()
    }

    private fun onTool(tool: Tool) {
        feedback(Cue.EMOJI, Tap.SOFT)
        if (toolbar?.active == tool) { showDeck(); return }
        when (tool) {
            Tool.FORMAT -> changeCase()
            Tool.CLIPBOARD -> showClipboard()
            Tool.EMOJI -> showEmoji()
            Tool.THEME -> {
                listPanel?.show(prefs.theme, "Choose a finish", KeyboardTheme.ALL.mapIndexed { i, theme ->
                    val mark = if (i == prefs.int(Prefs.THEME, Prefs.DEFAULT_THEME)) "●  " else ""
                    Pair(mark + theme.title, { prefs.put(Prefs.THEME, i) })
                })
                showPanel(listPanel, Tool.THEME)
            }
            Tool.SETTINGS -> startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Tool.HIDE -> requestHideSelf(0)
        }
    }

    private fun changeCase() {
        val ic: InputConnection = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (selected.isNullOrEmpty()) {
            Toast.makeText(this, "Select some text, then tap Aa", Toast.LENGTH_SHORT).show()
            return
        }
        val replaced = TextRules.cycleCase(selected.toString())
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        val start = if (extracted != null) extracted.startOffset + Math.min(extracted.selectionStart, extracted.selectionEnd)
            else Math.min(selStart, selEnd)
        ic.beginBatchEdit()
        ic.commitText(replaced, 1)
        if (start >= 0) ic.setSelection(start, start + replaced.length)   // keep it selected to cycle again
        ic.endBatchEdit()
    }

    /** Password managers flag secrets; those are never listed. getExtras() is API 24+. */
    private fun isSensitive(description: android.content.ClipDescription): Boolean {
        if (Build.VERSION.SDK_INT < 24) return false
        val extras = description.javaClass.getMethod("getExtras").invoke(description) as? android.os.PersistableBundle
        return extras?.getBoolean("android.content.extra.IS_SENSITIVE") == true
    }

    /** The clipboard is read only when this panel is opened. */
    private fun showClipboard() {
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val data = manager.primaryClip
        val sensitive = data?.description?.let { isSensitive(it) } ?: false
        val current = if (data != null && data.itemCount > 0) data.getItemAt(0).coerceToText(this)?.toString() else null
        if (!current.isNullOrEmpty() && !sensitive) {
            clips.remove(current)
            clips.add(0, current)
            while (clips.size > 8) clips.removeAt(clips.size - 1)
        }
        val items = ArrayList<Pair<String, () -> Unit>>()
        for (clip in clips) items.add(Pair(clip.take(160), { currentInputConnection?.commitText(clip, 1); showDeck() }))
        if (clips.isNotEmpty()) items.add(Pair("Clear this list", { clips.clear(); showDeck() }))
        items.add(Pair("Back to keys", { showDeck() }))
        listPanel?.show(prefs.theme, if (clips.isEmpty()) "Nothing copied yet" else "Tap to paste · kept in memory only", items)
        showPanel(listPanel, Tool.CLIPBOARD)
    }
}
