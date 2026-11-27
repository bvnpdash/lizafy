package com.zlitch.clackwell

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** The app's home and settings, styled after the keyboard: steel, ivory and one red key. */
class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var sound: SoundEngine
    private lateinit var haptics: Haptics
    private lateinit var page: LinearLayout
    private lateinit var column: LinearLayout
    private val timers = Handler(Looper.getMainLooper())
    private var enableStep: TextView? = null
    private var chooseStep: TextView? = null
    private var status: TextView? = null

    private val deck = 0xFFD3D5DA.toInt()
    private val paper = 0xFFF4F5F7.toInt()
    private val ink = 0xFF232428.toInt()
    private val faded = 0xFF5C5F66.toInt()
    private val steel = 0xFF8E9197.toInt()
    private val red = 0xFFC7363C.toInt()
    private val green = 0xFF3D7A4E.toInt()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        sound = SoundEngine(this)
        haptics = Haptics(this)
        sound.prepare()
        page = LinearLayout(this)
        page.orientation = LinearLayout.VERTICAL
        page.setPadding(dp(14), dp(14), dp(14), dp(28))
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(deck)
        scroll.addView(page)
        scroll.fitsSystemWindows = true
        setContentView(scroll)
        build()
    }

    override fun onResume() { super.onResume(); refreshSetup() }

    /** The keyboard picker is a dialog, so focus returning is the moment to re-check. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshSetup()
    }

    override fun onDestroy() {
        timers.removeCallbacksAndMessages(null)
        sound.release()
        super.onDestroy()
    }

    private fun build() {
        page.addView(HeroView(this) { sound.play(Cue.KEY); haptics.tap(Tap.KEY) },
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        card("Set up")
        status = note("")
        enableStep = pill("", true) { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        chooseStep = pill("", true) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }

        card("Try the machine")
        val test = EditText(this)
        test.hint = "Tap here and start typing…"
        test.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        test.minLines = 3
        test.gravity = Gravity.TOP or Gravity.START
        test.typeface = Typeface.MONOSPACE
        test.setTextColor(ink); test.setHintTextColor(steel)
        test.background = shape(-1, 12, steel)
        test.setPadding(dp(14), dp(12), dp(14), dp(12))
        add(test, 8)

        card("Sound")
        toggle("Typewriter sound", Prefs.SOUND, true)
        label("Sound style")
        choices(SoundStyle.values().map { it.title }, Prefs.STYLE) { sound.prepare(); timers.postDelayed({ preview() }, 250) }
        slider("Volume", Prefs.VOLUME, 70, 0, 100, "%")
        slider("Sound variation", Prefs.VARIATION, 60, 0, 100, "%")
        toggle("Special key sounds", Prefs.SPECIAL, true)
        toggle("Sound when keyboard opens", Prefs.OPEN_SOUND, false)
        pill("▶  Preview sound", false) { preview() }
        note("Muted in silent or vibrate mode and during calls. Loudness also follows your phone's system sound volume.")

        card("Touch")
        toggle("Haptic feedback", Prefs.HAPTICS, true)
        slider("Intensity", Prefs.HAPTIC_STRENGTH, 45, 5, 100, "%")
        pill("Test a keystroke", false) { haptics.tap(Tap.KEY) }
        note("Also needs touch vibration turned on in Android's settings.")

        card("Finish")
        themePicker()
        slider("Key size", Prefs.KEY_SIZE, 100, 80, 112, "%")
        slider("Keyboard height", Prefs.HEIGHT, 100, 82, 125, "%")
        slider("Key spacing", Prefs.SPACING, 3, 1, 8, " dp")
        slider("Mechanism intensity", Prefs.BACKGROUND, 100, 0, 100, "%")
        toggle("Always show capital letters on keys", Prefs.CAPITAL_LEGENDS, true)
        toggle("Number hints on the top row", Prefs.NUMBER_HINTS, false)

        card("Typing")
        toggle("Word suggestions", Prefs.SUGGESTIONS, true)
        toggle("Auto-capitalisation", Prefs.AUTO_CAPS, true)
        toggle("Double-space for full stop", Prefs.DOUBLE_SPACE, true)
        label("Layouts")
        note("The globe cycles through ticked layouts. Hold it to pick another keyboard.")
        for (language in Language.values()) {
            val box = CheckBox(this)
            box.text = language.title
            box.setTextColor(ink)
            box.buttonTintList = tint(red, steel)
            box.isChecked = language in prefs.enabledLanguages
            box.setOnCheckedChangeListener { view, checked ->
                val chosen = Language.values().filter { if (it == language) checked else it in prefs.enabledLanguages }
                if (chosen.isEmpty()) view.isChecked = true else prefs.enabledLanguages = chosen
            }
            add(box, 2)
        }

        card("Privacy")
        note("• No internet permission: Android will not let this app open a network connection.\n" +
            "• No analytics, accounts or cloud processing. What you type goes only to the app you are typing in.\n" +
            "• Only permission: Vibrate, used for key feedback.\n" +
            "• Word suggestions come from lists stored inside the app. It does not learn or save what you type.\n" +
            "• The microphone key switches to your phone's voice-typing keyboard. This app never records audio; " +
            "that other app handles your speech under its own privacy policy and may use the internet.\n" +
            "• Text around the cursor is read briefly for delete, capitals, suggestions, case change and cursor control, and is never saved.\n" +
            "• The clipboard is read only when you open the clipboard panel. That list stays in memory and is never saved.\n" +
            "• Saved on this phone: your settings and recently used emoji. Backup is turned off.")
        pill("Clear recent emoji", false) { prefs.recentEmoji = emptyList(); toast("Recent emoji cleared") }

        val signature = text("Clackwell by Zlitch", 17f, faded)
        signature.typeface = Fonts.italic(this)
        signature.gravity = Gravity.CENTER
        signature.maxLines = 1
        signature.setPadding(0, dp(22), 0, dp(6))
        page.addView(signature, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        refreshSetup()
    }

    private fun refreshSetup() {
        val first = enableStep ?: return
        val second = chooseStep ?: return
        val manager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = manager.enabledInputMethodList.any { it.packageName == packageName }
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: ""
        val chosen = current.startsWith("$packageName/")
        paintStep(first, if (enabled) "✓  Clackwell is turned on" else "1.  Turn on Clackwell", enabled)
        paintStep(second, if (chosen) "✓  Clackwell is your keyboard" else "2.  Choose Clackwell as your keyboard", chosen)
        status?.text = if (chosen) "All set. Your typewriter is ready in every app."
            else "Two quick steps. Android will warn that any third-party keyboard can see what you type; that is its standard message."
    }

    private fun paintStep(view: TextView, label: String, done: Boolean) {
        view.text = label
        view.background = if (done) shape(green, 24, 0) else shape(red, 24, 0)
        view.setTextColor(-1)
    }

    private fun preview() {
        if (!prefs.soundOn) { toast("Turn on Typewriter sound first"); return }
        if (sound.isMutedBySystem()) { toast("Your phone is in silent or vibrate mode, so key sounds are muted"); return }
        sound.prepare()
        timers.removeCallbacksAndMessages(null)
        val script = listOf(Cue.KEY to 100L, Cue.KEY to 330L, Cue.KEY to 550L, Cue.KEY to 660L, Cue.KEY to 750L,
            Cue.SPACE to 930L, Cue.SHIFT to 1230L, Cue.KEY to 1400L, Cue.NUMBER to 1600L, Cue.BACKSPACE to 1950L, Cue.ENTER to 2400L)
        for ((cue, at) in script) timers.postDelayed({ sound.play(cue) }, at)
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    // ---------------------------------------------------------------- building blocks

    private fun shape(fill: Int, radius: Int, border: Int): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.setColor(fill)
        drawable.setCornerRadius(dp(radius).toFloat())
        if (border != 0) drawable.setStroke(dp(1), border)
        return drawable
    }

    /** A chrome-edged pill, echoing the Shift and Enter keys. */
    private fun chrome(): GradientDrawable {
        val drawable = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(-1, 0xFFE0E2E7.toInt()))
        drawable.setCornerRadius(dp(24).toFloat())
        drawable.setStroke(dp(2), steel)
        return drawable
    }

    private fun tint(on: Int, off: Int) = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(on, off))

    private fun card(title: String) {
        column = LinearLayout(this)
        column.orientation = LinearLayout.VERTICAL
        column.background = shape(paper, 18, 0xFFFFFFFF.toInt())
        column.elevation = dp(2).toFloat()
        column.setPadding(dp(18), dp(14), dp(18), dp(18))
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.topMargin = dp(14)
        page.addView(column, params)
        val heading = text(title, 20f, ink)
        heading.typeface = Fonts.serif(this)
        add(heading, 0)
        val rule = View(this)
        rule.setBackgroundColor(red)
        val line = LinearLayout.LayoutParams(dp(28), dp(3))
        line.topMargin = dp(6); line.bottomMargin = dp(4)
        column.addView(rule, line)
    }

    private fun add(view: View, gap: Int) {
        val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.topMargin = dp(gap)
        column.addView(view, params)
    }

    private fun text(value: String, size: Float, color: Int): TextView {
        val view = TextView(this)
        view.text = value; view.textSize = size; view.setTextColor(color)
        return view
    }

    private fun label(value: String) = add(text(value, 15f, ink), 14)

    private fun note(value: String): TextView {
        val view = text(value, 13f, faded)
        view.setLineSpacing(0f, 1.15f)
        add(view, 8)
        return view
    }

    private fun pill(value: String, strong: Boolean, action: () -> Unit): TextView {
        val view = text(value, 15f, if (strong) -1 else ink)
        view.gravity = Gravity.CENTER
        view.minHeight = dp(48)
        view.setPadding(dp(16), dp(10), dp(16), dp(10))
        view.background = if (strong) shape(red, 24, 0) else chrome()
        view.isClickable = true
        view.isFocusable = true
        view.setOnClickListener { action() }
        add(view, 12)
        return view
    }

    private fun toggle(title: String, key: String, default: Boolean) {
        val view = Switch(this)
        view.text = title; view.textSize = 15f; view.setTextColor(ink)
        view.isChecked = prefs.bool(key, default)
        view.thumbTintList = tint(red, 0xFFF8F8F8.toInt())
        view.trackTintList = tint(0x80C7363C.toInt(), 0x66000000)
        view.minHeight = dp(46)
        view.setOnCheckedChangeListener { _, checked -> prefs.put(key, checked) }
        add(view, 4)
    }

    private fun slider(title: String, key: String, default: Int, min: Int, max: Int, unit: String) {
        val caption = text("$title  ·  ${prefs.int(key, default)}$unit", 15f, ink)
        add(caption, 14)
        val bar = SeekBar(this)
        bar.max = max - min
        bar.progress = prefs.int(key, default) - min
        bar.progressDrawable.setColorFilter(red, PorterDuff.Mode.SRC_IN)
        bar.thumb.setColorFilter(red, PorterDuff.Mode.SRC_IN)
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                caption.text = "$title  ·  ${progress + min}$unit"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            // Saved on release so the keyboard is not rebuilt on every pixel of a drag.
            override fun onStopTrackingTouch(seekBar: SeekBar?) { prefs.put(key, (seekBar?.progress ?: 0) + min) }
        })
        add(bar, 6)
    }

    /** Two-column grid of pill choices. */
    private fun choices(options: List<String>, key: String, changed: () -> Unit) {
        val views = ArrayList<TextView>()
        fun repaint() {
            val selected = prefs.int(key, 0)
            views.forEachIndexed { i, v ->
                v.background = if (i == selected) shape(ink, 22, 0) else chrome()
                v.setTextColor(if (i == selected) -1 else ink)
                v.isSelected = i == selected
            }
        }
        var row: LinearLayout? = null
        options.forEachIndexed { i, name ->
            if (i % 2 == 0) {
                row = LinearLayout(this)
                add(row!!, 8)
            }
            val view = text(name, 13f, ink)
            view.gravity = Gravity.CENTER
            view.minHeight = dp(44)
            view.isClickable = true; view.isFocusable = true
            view.setOnClickListener { prefs.put(key, i); repaint(); changed() }
            val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            params.setMargins(dp(3), 0, dp(3), 0)
            row?.addView(view, params)
            views.add(view)
        }
        repaint()
    }

    /** One swatch per finish: a miniature deck with an enamel key. */
    private fun themePicker() {
        val strip = LinearLayout(this)
        val swatches = ArrayList<TextView>()
        val name = text("", 14f, faded)
        fun repaint() {
            val selected = prefs.int(Prefs.THEME, Prefs.DEFAULT_THEME)
            KeyboardTheme.ALL.forEachIndexed { i, theme ->
                val tile = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(theme.bgTop, theme.bgBottom))
                tile.setCornerRadius(dp(12).toFloat())
                tile.setStroke(dp(if (i == selected) 3 else 1), if (i == selected) red else steel)
                swatches[i].background = tile
                swatches[i].isSelected = i == selected
            }
            name.text = KeyboardTheme.ALL[selected.coerceIn(0, KeyboardTheme.ALL.size - 1)].title
        }
        KeyboardTheme.ALL.forEachIndexed { i, theme ->
            val holder = TextView(this)
            holder.gravity = Gravity.CENTER
            holder.text = "◉"
            holder.textSize = 26f
            holder.setTextColor(theme.rimMid)
            holder.contentDescription = theme.title
            holder.isClickable = true; holder.isFocusable = true
            holder.setOnClickListener { prefs.put(Prefs.THEME, i); repaint() }
            val params = LinearLayout.LayoutParams(0, dp(58), 1f)
            params.setMargins(dp(3), 0, dp(3), 0)
            strip.addView(holder, params)
            swatches.add(holder)
        }
        add(strip, 8)
        add(name, 6)
        repaint()
    }
}
