package com.zlitch.clackwell

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View

/** The whole key deck as one custom view: no per-key child views, multi-touch with rollover. */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        /** Finger went down: fire sound and haptics now. */
        fun onKeyDown(key: KeyDef)
        fun onKeyReleased(key: KeyDef)
        /** The key's action should happen (text insert, page switch ...). */
        fun onKey(key: KeyDef, text: String)
        fun onShiftDown()
        fun onShiftUp()
        fun onDeleteRepeat(repeat: Int)
        fun onCursorMove(steps: Int)
        fun onLanguageHold()
    }

    private class Slot(val def: KeyDef) {
        val cap = RectF()
        var cx = 0f
        var cy = 0f
        var row = 0
        var cellLeft = 0f
        var cellRight = 0f
        val spring = KeySpring()
    }

    private class Touch(var slot: Slot, val startX: Float) {
        var committed = false
        var cancelled = false
        var cursorMode = false
        var cursorMoved = false
        var lastX = startX
        var popup: List<String>? = null
        var popupIndex = 0
        var longPress: Runnable? = null
        var repeat: Runnable? = null
    }

    private companion object {
        const val HEAD = 0.122f       // rod comb above the first row
        const val PITCH = 0.1478f     // one key row
        const val STRIP_TOP = 0.868f  // globe / full-stop strip
        const val DECK_ASPECT = 0.761f  // deck height as a share of its width
        // Left and right edges of the bottom-row caps, as shares of the width.
        val BOTTOM_SPANS = arrayOf(floatArrayOf(0.020f, 0.146f), floatArrayOf(0.163f, 0.250f),
            floatArrayOf(0.269f, 0.731f), floatArrayOf(0.753f, 0.978f))
    }

    var listener: Listener? = null
    private val prefs = Prefs(context)
    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val theme = prefs.theme
    private val artist = KeyArtist(theme, density)
    private var rows: List<List<KeyDef>> = Layouts.build(Page.LETTERS, Language.ENGLISH)
    private var slots: List<Slot> = emptyList()
    private var machine: Bitmap? = null
    private var linkwork: Bitmap? = null
    private val centres = FloatArray(4)
    private val struckAt = LongArray(KeyArtist.BARS)
    private val struckForce = FloatArray(KeyArtist.BARS)
    private val barThrow = FloatArray(KeyArtist.BARS)
    private var slideAt = 0L
    private var slideBy = 0f
    private var slideLength = 1L
    private val touches = LinkedHashMap<Int, Touch>()
    private val moving = HashSet<Slot>()
    private val timers = Handler(Looper.getMainLooper())
    private var lastFrame = 0L

    private var shiftMode = ShiftMode.OFF
    private var enterLabel = "↵"

    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint()
    private val rect = RectF()
    private val letterFace = Fonts.serif(context)
    private val smallFace = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

    fun setLayout(newRows: List<List<KeyDef>>) {
        cancelTouches()
        rows = newRows
        rebuild()
    }

    fun setShift(mode: ShiftMode) { if (mode != shiftMode) { shiftMode = mode; invalidate() } }
    fun setEnterLabel(label: String) { if (label != enterLabel) { enterLabel = label; invalidate() } }

    fun desiredHeight(): Int {
        val screen = resources.displayMetrics.heightPixels.toFloat()
        val landscape = resources.displayMetrics.widthPixels > screen
        val wide = resources.displayMetrics.widthPixels.toFloat()
        val wanted = (if (landscape) dp(196f) else wide * DECK_ASPECT) * prefs.heightScale
        return Math.min(wanted, screen * (if (landscape) 0.56f else 0.46f)).toInt()
    }

    /** Row bands as fractions of the height: [top, bottom] for four key rows and the strip. */
    private fun bandTop(r: Int) = height * (if (r < 4) HEAD + r * PITCH else STRIP_TOP)
    private fun bandHeight(r: Int) = height * (if (r < 4) PITCH else 1f - STRIP_TOP)
    fun rowCentre(r: Int) = bandTop(r) + bandHeight(r) / 2f

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), desiredHeight())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { rebuild() }

    private fun rebuild() {
        moving.clear()
        machine?.recycle(); machine = null
        linkwork?.recycle(); linkwork = null
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) { slots = emptyList(); return }
        val side = dp(5f)
        val rowHeight = h * PITCH
        val gap = dp(prefs.spacing.toFloat())
        val built = ArrayList<Slot>()
        rows.forEachIndexed { r, row ->
            val total = row.fold(0f) { sum, k -> sum + k.weight }
            val unit = (w - side * 2) / total
            var x = side
            val cy = rowCentre(r)
            if (r < 4) centres[r] = cy
            for (def in row) {
                val cell = unit * def.weight
                if (def.type != KeyType.GAP) {
                    val s = Slot(def)
                    s.row = r; s.cellLeft = x; s.cellRight = x + cell
                    s.cx = x + cell / 2f; s.cy = cy
                    val maxH = rowHeight - gap - dp(3f)
                    val round = Math.min(Math.min(unit - gap, maxH) * prefs.keySize, Math.min(unit - dp(1f), rowHeight - dp(5f)))
                    val isRound = def.type == KeyType.CHAR || def.type == KeyType.LANGUAGE || def.type == KeyType.EMOJI
                    val capW = if (isRound) round else cell - gap - dp(1f)
                    val capH = if (isRound) round else round * 1.02f
                    var left = Math.round(s.cx - capW / 2f).toFloat()
                    var right = left + Math.round(capW)
                    val landscape = w > h * 1.9f
                    if (!landscape && r == 3 && row.size == BOTTOM_SPANS.size) {
                        val span = BOTTOM_SPANS[row.indexOf(def)]
                        if (!isRound) { left = Math.round(w * span[0]).toFloat(); right = Math.round(w * span[1]).toFloat() }
                        else {
                            s.cx = w * (span[0] + span[1]) / 2f
                            left = Math.round(s.cx - capW / 2f).toFloat(); right = left + Math.round(capW)
                        }
                    } else if (!landscape && r == 2 && !isRound) {
                        val first = def === row.first()
                        left = Math.round(w * (if (first) 0.020f else 0.853f)).toFloat()
                        right = Math.round(w * (if (first) 0.147f else 0.978f)).toFloat()
                    }
                    val top = Math.round(cy - capH / 2f - dp(1.5f)).toFloat()
                    s.cap.set(left, top, right, top + Math.round(capH))
                    built.add(s)
                }
                x += cell
            }
        }
        // Re-centre touch cells on the caps where they were placed by measurement.
        val bottom = built.filter { it.row == 3 }
        if (w <= h * 1.9f) bottom.forEachIndexed { i, s ->
            s.cx = s.cap.centerX()
            s.cellLeft = if (i == 0) 0f else (bottom[i - 1].cap.right + s.cap.left) / 2f
            s.cellRight = if (i == bottom.size - 1) w else (s.cap.right + bottom[i + 1].cap.left) / 2f
        }
        slots = built
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val stems = built.filter { it.row < 3 }.map { floatArrayOf(it.cx, it.cy) }
        artist.drawMachine(Canvas(bitmap), w, h, centres, prefs.backgroundIntensity)
        machine = bitmap
        val front = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        artist.drawLinkwork(Canvas(front), w, centres, stems, prefs.backgroundIntensity)
        linkwork = front
        invalidate()
    }

    // ---------------------------------------------------------------- drawing

    override fun onDraw(canvas: Canvas) {
        machine?.let { canvas.drawBitmap(it, 0f, 0f, bitmapPaint) }
        val now = SystemClock.uptimeMillis()
        val barsMoving = advanceBars(now)
        if (slots.isNotEmpty()) artist.drawTypeBars(canvas, width.toFloat(), centres, barThrow, slideOffset(now), prefs.backgroundIntensity)
        linkwork?.let { canvas.drawBitmap(it, 0f, 0f, bitmapPaint) }
        advanceSprings()
        val stemLength = height * PITCH * 0.62f
        for (s in moving) if (s.row < 4) artist.drawStem(canvas, s.cx, s.cy, stemLength, s.spring.position, prefs.backgroundIntensity)
        for (s in slots) {
            val travel = s.spring.position
            if (s.row == 4) {
                if (travel > 0.05f) {
                    line.style = Paint.Style.FILL; line.color = ((travel.coerceIn(0f, 1f) * 50).toInt() shl 24)
                    canvas.drawCircle(s.cx, s.cy, bandHeight(4) * 0.42f, line)
                }
                drawStripLegend(canvas, s)
                continue
            }
            val kind = when (s.def.type) {
                KeyType.BACKSPACE -> CapKind.LACQUER_RED
                KeyType.SPACE -> CapKind.SPACE_BAR
                else -> CapKind.NORMAL
            }
            artist.drawCap(canvas, s.cap, kind, travel, s.def.type == KeyType.SHIFT && shiftMode == ShiftMode.CAPS)
            drawLegend(canvas, s, travel * dp(KeyArtist.TRAVEL_DP))
        }
        for (t in touches.values) if (t.popup != null) drawPopup(canvas, t)
        if (moving.isNotEmpty() || barsMoving || now - slideAt < slideLength) postInvalidateOnAnimation()
    }

    // ---------------------------------------------------------------- what the machine does on a keystroke

    private fun strikeBar(index: Int, at: Long, force: Float) {
        if (index in 0 until KeyArtist.BARS) { struckAt[index] = at; struckForce[index] = force }
    }

    private fun slide(distanceDp: Float, lengthMs: Long) {
        slideAt = SystemClock.uptimeMillis(); slideBy = dp(distanceDp); slideLength = lengthMs
    }

    private fun fireMechanism(slot: Slot) {
        val now = SystemClock.uptimeMillis()
        when (slot.def.type) {
            KeyType.CHAR -> {
                // Each key works its own bar: the one nearest its position across the deck.
                var nearest = 0
                for (i in 1 until KeyArtist.BARS) {
                    if (Math.abs(artist.barX(width.toFloat(), i) - slot.cx) < Math.abs(artist.barX(width.toFloat(), nearest) - slot.cx)) nearest = i
                }
                strikeBar(nearest, now, 1f)
                strikeBar(nearest - 1, now, 0.22f)
                strikeBar(nearest + 1, now, 0.22f)
                slide(-1.2f, 90L)       // the escapement steps one notch
            }
            KeyType.SPACE -> slide(-3.5f, 130L)
            KeyType.BACKSPACE -> slide(4f, 140L)
            KeyType.SHIFT -> for (i in 0 until KeyArtist.BARS) strikeBar(i, now, 0.2f)
            KeyType.ENTER -> {          // carriage return: a ripple runs across the comb
                for (i in 0 until KeyArtist.BARS) strikeBar(i, now + i * 15L, 0.65f)
                slide(9f, 360L)
            }
            else -> Unit
        }
    }

    /** Updates barThrow; true while any bar is still moving or waiting to move. */
    private fun advanceBars(now: Long): Boolean {
        var busy = false
        for (i in 0 until KeyArtist.BARS) {
            val age = now - struckAt[i]
            val live = struckAt[i] != 0L && age < StrikeMotion.LENGTH_MS
            barThrow[i] = if (live) StrikeMotion.at(age) * struckForce[i] else 0f
            if (live) busy = true
        }
        return busy
    }

    private fun slideOffset(now: Long): Float {
        val u = (now - slideAt).toFloat() / slideLength
        return if (u <= 0f || u >= 1f) 0f else slideBy * Math.sin(Math.PI * Math.sqrt(u.toDouble())).toFloat()
    }

    private fun advanceSprings() {
        if (moving.isEmpty()) { lastFrame = 0L; return }
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0.008f else (now - lastFrame) / 1000f
        lastFrame = now
        val iterator = moving.iterator()
        while (iterator.hasNext()) if (!iterator.next().spring.step(dt)) iterator.remove()
    }

    private fun write(canvas: Canvas, label: String, cx: Float, cy: Float, size: Float, face: Typeface, color: Int, maxWidth: Float) {
        text.typeface = face; text.textSize = size; text.color = color
        if (face === letterFace) { text.style = Paint.Style.FILL_AND_STROKE; text.strokeWidth = size * 0.012f }
        else text.style = Paint.Style.FILL
        val measured = text.measureText(label)
        if (measured > maxWidth && measured > 0f) text.textSize = size * maxWidth / measured
        canvas.drawText(label, cx, cy - (text.ascent() + text.descent()) / 2f, text)
    }

    private fun drawLegend(canvas: Canvas, s: Slot, down: Float) {
        val cx = s.cap.centerX()
        val cy = s.cap.centerY() + down
        val h = s.cap.height()
        val ink = if (s.def.type == KeyType.BACKSPACE) -0xc5eff0 else theme.ink
        val room = s.cap.width() * 0.70f
        when (s.def.type) {
            KeyType.CHAR -> {
                // Real typewriter caps always show capitals; the setting switches to case-following legends.
                val capital = s.def.label.length == 1 && Character.isLetter(s.def.label[0]) && s.def.label != "ß" &&
                    (shiftMode != ShiftMode.OFF || prefs.capitalLegends)
                val shown = if (capital) TextRules.upper(s.def.label) else s.def.label
                write(canvas, shown, cx, cy, h * (if (capital) 0.62f else 0.66f), letterFace, ink, s.cap.width() * 0.74f)
            }
            KeyType.SPACE -> if (s.def.label.isNotEmpty() && prefs.enabledLanguages.size > 1) write(canvas, s.def.label, cx, cy, h * 0.30f, smallFace, alphaInk(0.45f), room)
            KeyType.SYMBOLS, KeyType.SYMBOLS2, KeyType.LETTERS -> write(canvas, s.def.label, cx, cy, h * 0.50f, letterFace, ink, s.cap.width() * 0.72f)
            KeyType.ENTER -> if (enterLabel == "↵") Icons.enter(canvas, line, cx, cy, h * 0.50f, ink)
                else write(canvas, enterLabel, cx, cy, h * 0.38f, letterFace, ink, s.cap.width() * 0.74f)
            KeyType.SHIFT -> Icons.shift(canvas, line, cx, cy, h * 0.46f, ink, shiftMode)
            KeyType.BACKSPACE -> Icons.backspace(canvas, line, cx, cy, h * 0.46f, ink)
            KeyType.EMOJI -> Icons.smile(canvas, line, cx, cy, h * 0.46f, ink)
            KeyType.LANGUAGE -> Icons.globe(canvas, line, cx, cy, h * 0.46f, ink)
            KeyType.MIC, KeyType.GAP -> Unit
        }
        if (s.def.type == KeyType.CHAR && s.row == 0 && s.def.alternates.isNotEmpty() && prefs.numberHints) drawHint(canvas, s)
    }

    /** A small enamel tag on the rim, so the digit stays readable over the metalwork. */
    private fun drawHint(canvas: Canvas, s: Slot) {
        val x = s.cap.right - dp(4f)
        val y = s.cap.top + dp(1f)
        line.style = Paint.Style.FILL; line.color = theme.faceLight
        canvas.drawCircle(x, y, dp(5.5f), line)
        line.style = Paint.Style.STROKE; line.strokeWidth = dp(0.8f); line.color = theme.rimDark
        canvas.drawCircle(x, y, dp(5.5f), line)
        write(canvas, s.def.alternates[0], x, y, dp(8.5f), smallFace, theme.ink, dp(10f))
    }

    private fun drawStripLegend(canvas: Canvas, s: Slot) {
        val ink = if (theme.dark) -0x1a1d24 else theme.ink
        if (s.def.type == KeyType.LANGUAGE) Icons.globe(canvas, line, s.cx, s.cy, width * 0.056f, ink)
        else if (s.def.type == KeyType.MIC) Icons.mic(canvas, line, s.cx, s.cy, width * 0.060f, ink)
        else write(canvas, s.def.label, s.cx, s.cy - (if (s.def.label == "." || s.def.label == ",") dp(5f) else 0f),
            dp(34f), letterFace, ink, s.cellRight - s.cellLeft)
    }

    private fun alphaInk(a: Float) = ((a * 255).toInt() shl 24) or (theme.ink and 0x00FFFFFF)

    private fun popupCell(t: Touch, i: Int, out: RectF) {
        val count = t.popup!!.size
        val cell = Math.min(dp(38f), (width - dp(4f)) / count)
        val total = cell * count
        val left = (t.slot.cx - cell / 2f).coerceIn(dp(2f), Math.max(dp(2f), width - total - dp(2f)))
        val top = Math.max(dp(1f), t.slot.cap.top - dp(48f))
        out.set(left + cell * i, top, left + cell * (i + 1), top + dp(42f))
    }

    private fun drawPopup(canvas: Canvas, t: Touch) {
        val options = t.popup ?: return
        for (i in options.indices) {
            popupCell(t, i, rect)
            line.style = Paint.Style.FILL; line.color = if (i == t.popupIndex) theme.accent else theme.faceLight
            canvas.drawRoundRect(rect, dp(7f), dp(7f), line)
            line.style = Paint.Style.STROKE; line.strokeWidth = dp(1f); line.color = theme.rimDark
            canvas.drawRoundRect(rect, dp(7f), dp(7f), line)
            val label = if (shiftMode != ShiftMode.OFF) TextRules.upper(options[i]) else options[i]
            write(canvas, label, rect.centerX(), rect.centerY(), dp(21f), letterFace,
                if (i == t.popupIndex) theme.faceDark else theme.ink, rect.width() * 0.8f)
        }
    }

    // ---------------------------------------------------------------- touch

    /** Every point maps to a key (nearest in its row), so there are no dead zones. */
    private fun slotAt(x: Float, y: Float): Slot? {
        if (slots.isEmpty()) return null
        val row = if (y >= height * STRIP_TOP) 4 else ((y - height * HEAD) / (height * PITCH)).toInt().coerceIn(0, 3)
        var best: Slot? = null
        var distance = Float.MAX_VALUE
        for (s in slots) {
            if (s.row != row) continue
            val d = if (x < s.cellLeft) s.cellLeft - x else if (x > s.cellRight) x - s.cellRight else 0f
            if (d < distance) { distance = d; best = s }
        }
        return best
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val index = event.actionIndex
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                down(event.getPointerId(index), event.getX(index), event.getY(index))
            MotionEvent.ACTION_MOVE ->
                for (i in 0 until event.pointerCount) move(event.getPointerId(i), event.getX(i), event.getY(i))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> up(event.getPointerId(index))
            MotionEvent.ACTION_CANCEL -> cancelTouches()
        }
        return true
    }

    private fun animate(slot: Slot, pressed: Boolean) {
        if (pressed) slot.spring.press() else slot.spring.release()
        moving.add(slot)
        invalidate()
    }

    private fun commitsOnRelease(type: KeyType) = type != KeyType.SHIFT && type != KeyType.BACKSPACE

    private fun down(id: Int, x: Float, y: Float) {
        // Rollover: a new finger finishes earlier keys first, so fast typing keeps its order.
        for (other in ArrayList(touches.values)) {
            if (!other.committed && !other.cancelled && !other.cursorMoved && commitsOnRelease(other.slot.def.type)) commit(other)
        }
        val slot = slotAt(x, y) ?: return
        val touch = Touch(slot, x)
        touches[id] = touch
        animate(slot, true)
        fireMechanism(slot)
        listener?.onKeyDown(slot.def)
        when (slot.def.type) {
            KeyType.SHIFT -> listener?.onShiftDown()
            KeyType.BACKSPACE -> startRepeat(touch)
            else -> startLongPress(touch)
        }
    }

    private fun startRepeat(touch: Touch) {
        touch.committed = true
        listener?.onKey(touch.slot.def, "")
        var count = 0
        val runner = object : Runnable {
            override fun run() {
                count++
                listener?.onDeleteRepeat(count)
                if (count % 2 == 0) { fireMechanism(touch.slot); invalidate() }
                timers.postDelayed(this, DeleteRepeat.delayAfter(count))
            }
        }
        touch.repeat = runner
        timers.postDelayed(runner, DeleteRepeat.FIRST_DELAY_MS)
    }

    private fun startLongPress(touch: Touch) {
        val def = touch.slot.def
        val wanted = def.alternates.isNotEmpty() || def.type == KeyType.SPACE || def.type == KeyType.LANGUAGE
        if (!wanted) return
        val runner = Runnable {
            if (touch.committed || touch.cancelled) return@Runnable
            when (def.type) {
                KeyType.SPACE -> { touch.cursorMode = true; touch.lastX = touch.startX }
                KeyType.LANGUAGE -> { touch.cancelled = true; animate(touch.slot, false); listener?.onLanguageHold() }
                else -> { touch.popup = def.alternates; touch.popupIndex = 0 }
            }
            invalidate()
        }
        touch.longPress = runner
        timers.postDelayed(runner, 360L)
    }

    private fun move(id: Int, x: Float, y: Float) {
        val touch = touches[id] ?: return
        if (touch.cancelled) return
        val type = touch.slot.def.type
        if (type == KeyType.SPACE) {
            // Hold (or swipe far) on the space bar to steer the cursor.
            if (!touch.cursorMode && !touch.committed && Math.abs(x - touch.startX) > dp(28f)) {
                touch.cursorMode = true; touch.lastX = x
            }
            if (touch.cursorMode) {
                val steps = ((x - touch.lastX) / dp(11f)).toInt()
                if (steps != 0) { touch.lastX += steps * dp(11f); touch.cursorMoved = true; listener?.onCursorMove(steps) }
            }
            return
        }
        val options = touch.popup
        if (options != null) {
            popupCell(touch, 0, rect)
            val choice = ((x - rect.left) / rect.width()).toInt().coerceIn(0, options.size - 1)
            if (choice != touch.popupIndex) { touch.popupIndex = choice; invalidate() }
            return
        }
        if (type != KeyType.CHAR || touch.committed) return
        val next = slotAt(x, y) ?: return
        val slack = dp(7f)
        val left = x < touch.slot.cellLeft - slack || x > touch.slot.cellRight + slack
        val vertical = Math.abs(y - touch.slot.cy) > height * PITCH * 0.7f
        if (next !== touch.slot && next.def.type == KeyType.CHAR && (left || vertical)) {
            touch.longPress?.let { timers.removeCallbacks(it) }
            animate(touch.slot, false)
            touch.slot = next
            animate(next, true)
            startLongPress(touch)
        }
    }

    private fun commit(touch: Touch) {
        touch.committed = true
        touch.longPress?.let { timers.removeCallbacks(it) }
        val options = touch.popup
        val label = if (options != null) options[touch.popupIndex] else touch.slot.def.label
        touch.popup = null
        listener?.onKey(touch.slot.def, label)
    }

    private fun up(id: Int) {
        val touch = touches.remove(id) ?: return
        stopTimers(touch)
        if (!touch.cancelled) {
            animate(touch.slot, false)
            listener?.onKeyReleased(touch.slot.def)
            if (touch.slot.def.type == KeyType.SHIFT) listener?.onShiftUp()
            else if (!touch.committed && !touch.cursorMoved) commit(touch)   // may swap the layout
        }
        invalidate()
    }

    private fun stopTimers(touch: Touch) {
        touch.longPress?.let { timers.removeCallbacks(it) }
        touch.repeat?.let { timers.removeCallbacks(it) }
    }

    fun cancelTouches() {
        for (t in touches.values) {
            stopTimers(t)
            t.cancelled = true
            if (t.slot.def.type == KeyType.SHIFT) listener?.onShiftUp()
            t.slot.spring.release()
            moving.add(t.slot)
        }
        touches.clear()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        cancelTouches()
        timers.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }
}
