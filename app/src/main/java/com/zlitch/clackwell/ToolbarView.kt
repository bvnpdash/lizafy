package com.zlitch.clackwell

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View

enum class Tool(val title: String) {
    FORMAT("Change case of selected text"), CLIPBOARD("Clipboard"), EMOJI("Emoji"),
    THEME("Theme"), SETTINGS("Settings"), HIDE("Hide keyboard")
}

class ToolbarView(context: Context, private val onTool: (Tool) -> Unit) : View(context) {
    private val density = resources.displayMetrics.density
    private val prefs = Prefs(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pressed = -1
    private val panel = android.graphics.RectF()
    var active: Tool? = null
        set(value) { field = value; invalidate() }

    /** Set by the keyboard when a suggestion is tapped. */
    var onPick: (Int) -> Unit = {}
    private var offers: Suggestions? = null
    private var showTools = false

    /** null, or an empty word, brings the tools back. */
    fun setSuggestions(value: Suggestions?) {
        val next = if (value == null || value.literal.isEmpty()) null else value
        if (next == null) showTools = false
        offers = next
        invalidate()
    }

    private val suggesting get() = offers != null && !showTools && active == null
    private fun gridWidth() = height * 1.15f

    /** -1 is the grid button; 0..2 are the three slots. Slot 0 is the word as typed. */
    private fun slotAt(x: Float): Int =
        if (x < gridWidth()) -1 else ((x - gridWidth()) / ((width - gridWidth()) / 3f)).toInt().coerceIn(0, 2)

    private fun drawSuggestions(canvas: Canvas, theme: KeyboardTheme, current: Suggestions) {
        val ink = if (theme.dark) -0x1a1d24 else theme.ink
        val cy = height * 0.62f
        val grid = gridWidth()
        val cell = (width - grid) / 3f
        if (pressed >= 0) {
            val at = slotAt(pressedX)
            paint.style = Paint.Style.FILL; paint.color = (theme.accent and 0x00FFFFFF) or 0x26000000
            canvas.drawRect(if (at < 0) 0f else grid + cell * at, 0f, if (at < 0) grid else grid + cell * (at + 1), height.toFloat(), paint)
        }
        Icons.grid(canvas, paint, grid / 2f, cy, 13f * density, (ink and 0x00FFFFFF) or 0xA0000000.toInt())
        paint.style = Paint.Style.FILL
        paint.color = if (theme.dark) 0x30FFFFFF else 0x24000000
        for (i in 0..2) canvas.drawRect(grid + cell * i, height * 0.40f, grid + cell * i + density, height * 0.84f, paint)
        val labels = listOf("“" + current.literal + "”") + current.words
        paint.color = ink; paint.textAlign = Paint.Align.CENTER; paint.typeface = Typeface.DEFAULT
        labels.forEachIndexed { i, label ->
            paint.textSize = 15f * density
            val room = cell - 12f * density
            val measured = paint.measureText(label)
            if (measured > room) paint.textSize = paint.textSize * room / measured
            canvas.drawText(label, grid + cell * (i + 0.5f), cy + paint.textSize * 0.34f, paint)
        }
    }

    private var pressedX = 0f

    init { contentDescription = "Keyboard toolbar" }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val landscape = resources.displayMetrics.widthPixels > resources.displayMetrics.heightPixels
        val width = MeasureSpec.getSize(widthSpec)
        val tall = if (landscape) 34 * density else (width * 0.128f).coerceIn(42 * density, 54 * density)
        setMeasuredDimension(width, tall.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val theme = prefs.theme
        // The deck has rounded top corners; the app being typed in shows through them.
        val corner = 18f * density
        paint.shader = null; paint.style = Paint.Style.FILL; paint.color = theme.bgTop
        panel.set(0f, 0f, width.toFloat(), height + corner)
        canvas.drawRoundRect(panel, corner, corner, paint)
        val current = offers
        if (suggesting && current != null) { drawSuggestions(canvas, theme, current); return }
        val cell = width.toFloat() / Tool.values().size
        val cy = height * 0.60f
        val size = 17f * density
        Tool.values().forEachIndexed { i, tool ->
            val cx = cell * (i + 0.5f)
            if (i == pressed || tool == active) {
                paint.style = Paint.Style.FILL; paint.color = (theme.accent and 0x00FFFFFF) or 0x33000000
                canvas.drawCircle(cx, cy, 16f * density, paint)
            }
            val ink = if (theme.dark) -0x1a1d24 else theme.ink
            when (tool) {
                Tool.FORMAT -> {
                    paint.style = Paint.Style.FILL; paint.color = ink; paint.textAlign = Paint.Align.CENTER
                    paint.typeface = Fonts.serif(context); paint.textSize = size
                    canvas.drawText("Aa", cx, cy + size * 0.34f, paint)
                }
                Tool.CLIPBOARD -> Icons.clipboard(canvas, paint, cx, cy, size, ink)
                Tool.EMOJI -> Icons.smile(canvas, paint, cx, cy, size, ink)
                Tool.THEME -> Icons.theme(canvas, paint, cx, cy, size, ink)
                Tool.SETTINGS -> Icons.settings(canvas, paint, cx, cy, size, ink)
                Tool.HIDE -> Icons.hide(canvas, paint, cx, cy, size, ink)
            }
        }
        // Hairline dividers, like the strip above the keys in the design reference.
        paint.style = Paint.Style.FILL
        paint.color = if (theme.dark) 0x24FFFFFF else 0x1C000000
        for (i in 1 until Tool.values().size) canvas.drawRect(cell * i, height * 0.40f, cell * i + density, height * 0.82f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val count = Tool.values().size
        val words = suggesting
        val i = if (words) slotAt(event.x) + 1 else (event.x / (width.toFloat() / count)).toInt().coerceIn(0, count - 1)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { pressed = i; pressedX = event.x; invalidate() }
            MotionEvent.ACTION_UP -> {
                val hit = pressed == i && event.y >= 0 && event.y <= height
                pressed = -1; invalidate()
                if (hit) {
                    performClick()
                    if (!words) onTool(Tool.values()[i])
                    else if (i == 0) { showTools = true; invalidate() }
                    else onPick(i - 1)
                }
            }
            MotionEvent.ACTION_CANCEL -> { pressed = -1; invalidate() }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
