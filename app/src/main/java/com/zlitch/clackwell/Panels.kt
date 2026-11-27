package com.zlitch.clackwell

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** One emoji plus its five skin-tone versions (empty when it has none). */
class EmojiItem(val base: String, private val tones: List<String>) {
    fun pick(tone: Int) = if (tone in 1..5 && tones.size == 5) tones[tone - 1] else base
}

class EmojiCategory(val icon: String, val items: List<EmojiItem>)

/** Reads the full emoji list from assets/emoji/emoji.txt (group, emoji, optional 5 skin tones; tab separated). */
object EmojiData {
    private var cache: List<EmojiCategory>? = null
    private val icons = linkedMapOf(
        "smileys-emotion" to "\uD83D\uDE00", "people-body" to "\uD83D\uDC4B", "animals-nature" to "\uD83D\uDC36",
        "food-drink" to "\uD83C\uDF4E", "travel-places" to "\uD83D\uDE97", "activities" to "\u26BD",
        "objects" to "\uD83D\uDCA1", "symbols" to "\u2764\uFE0F", "flags" to "\uD83C\uDFC1")

    fun load(context: Context): List<EmojiCategory> = cache ?: build(context).also { cache = it }

    private fun build(context: Context): List<EmojiCategory> {
        val groups = LinkedHashMap<String, MutableList<EmojiItem>>()
        icons.keys.forEach { groups[it] = mutableListOf() }
        context.assets.open("emoji/emoji.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                val parts = line.split("\t")
                if (parts.size < 2) continue
                groups[parts[0]]?.add(EmojiItem(parts[1], parts.drop(2)))
            }
        }
        // First tab is "recent"; the panel fills it from preferences.
        return listOf(EmojiCategory("\uD83D\uDD58", emptyList())) + icons.map { (key, icon) -> EmojiCategory(icon, groups[key] ?: emptyList()) }
    }
}

/** Shared look for the panels that replace the key deck. */
object PanelStyle {
    fun ink(theme: KeyboardTheme) = if (theme.dark) -0x1a1d24 else theme.ink

    fun chip(context: Context, label: String, theme: KeyboardTheme, size: Float): TextView {
        val d = context.resources.displayMetrics.density
        val view = TextView(context)
        view.text = label; view.textSize = size; view.gravity = Gravity.CENTER
        view.setTextColor(theme.ink)
        val shape = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(theme.faceLight, theme.faceDark))
        shape.setCornerRadius(18 * d)
        shape.setStroke((1.5f * d).toInt(), theme.rimDark)
        view.background = shape
        return view
    }
}

class EmojiPanel(context: Context, private val onEmoji: (String) -> Unit, private val onBack: () -> Unit,
                 private val onSpace: () -> Unit, private val onDelete: (Int) -> Unit) : LinearLayout(context) {
    private val prefs = Prefs(context)
    private val d = resources.displayMetrics.density
    private val probe = Paint().also { it.typeface = Fonts.emoji(context) }
    private val typeface = Fonts.emoji(context)
    private val categories = EmojiData.load(context)
    private var selected = if (prefs.recentEmoji.isEmpty()) 1 else 0
    private var shown: List<String> = emptyList()
    private val tones = listOf("\uD83D\uDC4D", "\uD83D\uDC4D\uD83C\uDFFB", "\uD83D\uDC4D\uD83C\uDFFC", "\uD83D\uDC4D\uD83C\uDFFD", "\uD83D\uDC4D\uD83C\uDFFE", "\uD83D\uDC4D\uD83C\uDFFF")
    private var theme = prefs.theme
    private val tabs = LinearLayout(context)
    private val grid = GridView(context)
    private val footer = LinearLayout(context)
    private val timers = Handler(Looper.getMainLooper())

    private val adapter = object : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int) = shown[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convert: View?, parent: ViewGroup?): View {
            val view = (convert as? TextView) ?: TextView(context).apply {
                textSize = 25f; gravity = Gravity.CENTER; typeface = this@EmojiPanel.typeface
                setTextColor(-0x1000000)
                layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (42 * d).toInt())
            }
            view.text = shown[position]
            return view
        }
    }

    init {
        orientation = VERTICAL
        addView(tabs, LayoutParams(LayoutParams.MATCH_PARENT, (36 * d).toInt()))
        grid.numColumns = GridView.AUTO_FIT
        grid.columnWidth = (42 * d).toInt()
        grid.stretchMode = GridView.STRETCH_COLUMN_WIDTH
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, position, _ ->
            val emoji = shown[position]
            prefs.recentEmoji = listOf(emoji) + prefs.recentEmoji.filter { it != emoji }
            onEmoji(emoji)
        }
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(footer, LayoutParams(LayoutParams.MATCH_PARENT, (42 * d).toInt()))
    }

    @SuppressLint("ClickableViewAccessibility")
    fun refresh() {
        theme = prefs.theme
        setBackgroundColor(theme.bgTop)
        tabs.removeAllViews()
        categories.forEachIndexed { i, pair ->
            val tab = TextView(context)
            tab.text = pair.icon; tab.textSize = 18f; tab.gravity = Gravity.CENTER; tab.typeface = typeface
            tab.setTextColor(PanelStyle.ink(theme))
            tab.alpha = if (i == selected) 1f else 0.45f
            tab.setOnClickListener { selected = i; refresh() }
            tabs.addView(tab, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        val tone = prefs.emojiTone
        val source = if (selected == 0) prefs.recentEmoji else categories[selected].items.map { it.pick(tone) }
        shown = source.filter { it.isNotEmpty() && probe.hasGlyph(it) }
        adapter.notifyDataSetChanged()
        grid.setSelection(0)

        footer.removeAllViews()
        fun add(view: View, weight: Float) {
            val p = LayoutParams(0, LayoutParams.MATCH_PARENT, weight)
            p.setMargins((4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt(), (4 * d).toInt())
            footer.addView(view, p)
        }
        add(PanelStyle.chip(context, "ABC", theme, 14f).apply { setOnClickListener { onBack() } }, 1.4f)
        add(PanelStyle.chip(context, tones[prefs.emojiTone], theme, 18f).apply {
            typeface = this@EmojiPanel.typeface
            setOnClickListener { prefs.emojiTone = (prefs.emojiTone + 1) % 6; refresh() }
        }, 1f)
        add(PanelStyle.chip(context, "space", theme, 14f).apply { setOnClickListener { onSpace() } }, 3f)
        val delete = PanelStyle.chip(context, "⌫", theme, 18f)
        delete.setTextColor(-1)
        (delete.background as GradientDrawable).setColors(intArrayOf(-0x28c7cd, -0x72e5e6))
        var count = 0
        val repeat = object : Runnable {
            override fun run() { count++; onDelete(count); timers.postDelayed(this, DeleteRepeat.delayAfter(count)) }
        }
        delete.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                count = 0; onDelete(0); timers.postDelayed(repeat, DeleteRepeat.FIRST_DELAY_MS)
            } else if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                timers.removeCallbacks(repeat)
            }
            true
        }
        add(delete, 1.4f)
    }

    fun stop() = timers.removeCallbacksAndMessages(null)

    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }
}

/** A simple list panel, used for clipboard entries and the theme picker. */
class ListPanel(context: Context) : ScrollView(context) {
    private val d = resources.displayMetrics.density
    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    init { addView(list) }

    fun show(theme: KeyboardTheme, title: String, items: List<Pair<String, () -> Unit>>) {
        setBackgroundColor(theme.bgTop)
        list.removeAllViews()
        val pad = (10 * d).toInt()
        list.setPadding(pad, pad / 2, pad, pad)
        val heading = TextView(context)
        heading.text = title; heading.textSize = 13f; heading.setTextColor(PanelStyle.ink(theme))
        heading.setPadding(0, 0, 0, pad / 2)
        list.addView(heading)
        for (item in items) {
            val chip = PanelStyle.chip(context, item.first, theme, 15f)
            chip.maxLines = 2; chip.ellipsize = TextUtils.TruncateAt.END
            chip.setPadding(pad * 2, pad, pad * 2, pad)
            chip.setOnClickListener { item.second() }
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.bottomMargin = pad / 2
            list.addView(chip, p)
        }
        scrollTo(0, 0)
    }
}
