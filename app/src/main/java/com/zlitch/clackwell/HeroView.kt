package com.zlitch.clackwell

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View

/** The app's masthead: the name spelled in live typewriter keys (tap them), signed "by Zlitch". */
class HeroView(context: Context, private val onStrike: () -> Unit) : View(context) {
    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private val theme = KeyboardTheme.ALL[Prefs.DEFAULT_THEME]
    private val artist = KeyArtist(theme, density)
    private val letters = "CLACKWELL"
    private val caps = Array(letters.length) { RectF() }
    private val springs = Array(letters.length) { KeySpring() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val card = RectF()
    private val rows = FloatArray(4)
    private val barThrow = FloatArray(KeyArtist.BARS)
    private var struckBar = -1
    private var struckAt = 0L
    private var held = -1
    private var lastFrame = 0L

    init { contentDescription = "Clackwell, by Zlitch" }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), dp(184f).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val side = dp(8f)
        val cell = (w - side * 2) / letters.length
        val size = Math.min(cell - dp(2f), dp(42f))
        for (i in letters.indices) {
            val cx = w / 2f + (i - (letters.length - 1) / 2f) * cell
            // A gentle arc, like the key bank of a real machine.
            val lift = Math.sin(Math.PI * i / (letters.length - 1)).toFloat() * dp(7f)
            val top = Math.round(dp(76f) - lift - size / 2f).toFloat()
            val left = Math.round(cx - size / 2f).toFloat()
            caps[i].set(left, top, left + Math.round(size), top + Math.round(size))
        }
        for (r in 0..3) rows[r] = dp(60f) + r * dp(36f)
    }

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        paint.style = Paint.Style.FILL
        card.set(0f, 0f, width.toFloat(), height.toFloat())
        paint.color = theme.bgTop
        canvas.drawRoundRect(card, dp(22f), dp(22f), paint)

        for (i in barThrow.indices) barThrow[i] = if (i == struckBar) StrikeMotion.at(now - struckAt) else 0f
        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), dp(120f))
        artist.drawTypeBars(canvas, width.toFloat(), rows, barThrow, 0f, 1f)
        canvas.restore()
        // A chrome rail that the type bars are mounted on.
        card.set(width * 0.17f, dp(114f), width * 0.83f, dp(120f))
        paint.color = theme.rimDark; canvas.drawRoundRect(card, dp(3f), dp(3f), paint)
        card.inset(dp(1f), dp(1.2f))
        paint.color = theme.rimLight; canvas.drawRoundRect(card, dp(2f), dp(2f), paint)

        val dt = if (lastFrame == 0L) 0.008f else (now - lastFrame) / 1000f
        lastFrame = now
        var busy = now - struckAt < StrikeMotion.LENGTH_MS
        for (i in letters.indices) {
            if (springs[i].step(dt)) busy = true
            val travel = springs[i].position
            artist.drawStem(canvas, caps[i].centerX(), caps[i].centerY(), dp(34f), Math.max(travel, 0.02f), 1f)
            artist.drawCap(canvas, caps[i], if (i == 0) CapKind.LACQUER_RED else CapKind.NORMAL, travel, false)
            paint.style = Paint.Style.FILL; paint.color = if (i == 0) -0x1 else theme.ink
            paint.typeface = Fonts.serif(context)
            paint.textAlign = Paint.Align.CENTER; paint.textSize = caps[i].height() * 0.55f
            canvas.drawText(letters.substring(i, i + 1), caps[i].centerX(),
                caps[i].centerY() + paint.textSize * 0.35f + travel * dp(KeyArtist.TRAVEL_DP), paint)
        }
        paint.color = theme.ink; paint.typeface = Fonts.italic(context)
        paint.textSize = dp(21f)
        canvas.drawText("by Zlitch", width / 2f, dp(143f), paint)
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL); paint.textSize = dp(11f)
        paint.color = theme.ring
        canvas.drawText("THE MECHANICAL TYPEWRITER KEYBOARD", width / 2f, dp(166f), paint)
        if (busy) postInvalidateOnAnimation() else lastFrame = 0L
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                held = caps.indexOfFirst { event.x >= it.left && event.x <= it.right && event.y >= it.top - dp(12f) && event.y <= it.bottom + dp(12f) }
                if (held < 0) return false
                springs[held].press()
                struckBar = held * (KeyArtist.BARS - 1) / (letters.length - 1)
                struckAt = SystemClock.uptimeMillis()
                onStrike()
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (held >= 0) springs[held].release()
                held = -1
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
