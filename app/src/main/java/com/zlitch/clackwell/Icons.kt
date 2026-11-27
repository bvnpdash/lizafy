package com.zlitch.clackwell

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/** Hand-drawn line icons, so legends look the same on every phone. */
object Icons {
    private val path = Path()
    private val oval = RectF()

    private fun pen(p: Paint, color: Int, size: Float) {
        p.shader = null; p.color = color; p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.088f; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
    }

    fun shift(c: Canvas, p: Paint, x: Float, y: Float, s: Float, color: Int, mode: ShiftMode) {
        pen(p, color, s)
        if (mode != ShiftMode.OFF) p.style = Paint.Style.FILL_AND_STROKE
        val lift = if (mode == ShiftMode.CAPS) s * 0.10f else 0f
        path.reset()
        path.moveTo(x, y - s * 0.52f - lift)
        path.lineTo(x + s * 0.52f, y - lift)
        path.lineTo(x + s * 0.24f, y - lift)
        path.lineTo(x + s * 0.24f, y + s * 0.40f - lift)
        path.lineTo(x - s * 0.24f, y + s * 0.40f - lift)
        path.lineTo(x - s * 0.24f, y - lift)
        path.lineTo(x - s * 0.52f, y - lift)
        path.close()
        c.drawPath(path, p)
        if (mode == ShiftMode.CAPS) c.drawLine(x - s * 0.28f, y + s * 0.52f, x + s * 0.28f, y + s * 0.52f, p)
    }

    fun backspace(c: Canvas, p: Paint, x: Float, y: Float, s: Float, color: Int) {
        pen(p, color, s)
        path.reset()
        path.moveTo(x - s * 0.75f, y)
        path.lineTo(x - s * 0.30f, y - s * 0.42f)
        path.lineTo(x + s * 0.68f, y - s * 0.42f)
        path.lineTo(x + s * 0.68f, y + s * 0.42f)
        path.lineTo(x - s * 0.30f, y + s * 0.42f)
        path.close()
        c.drawPath(path, p)
        c.drawLine(x - s * 0.05f, y - s * 0.19f, x + s * 0.35f, y + s * 0.19f, p)
        c.drawLine(x - s * 0.05f, y + s * 0.19f, x + s * 0.35f, y - s * 0.19f, p)
    }

    fun mic(c: Canvas, p: Paint, x: Float, y: Float, s: Float, color: Int) {
        pen(p, color, s * 0.85f)
        oval.set(x - s * 0.19f, y - s * 0.55f, x + s * 0.19f, y + s * 0.14f)
        c.drawRoundRect(oval, s * 0.19f, s * 0.19f, p)
        oval.set(x - s * 0.38f, y - s * 0.40f, x + s * 0.38f, y + s * 0.33f)
        c.drawArc(oval, 0f, 180f, false, p)
        c.drawLine(x, y + s * 0.33f, x, y + s * 0.54f, p)
        c.drawLine(x - s * 0.24f, y + s * 0.54f, x + s * 0.24f, y + s * 0.54f, p)
    }

    fun grid(c: Canvas, p: Paint, x: Float, y: Float, s: Float, color: Int) {
        pen(p, color, s * 0.75f)
        for (dx in floatArrayOf(-0.42f, 0.08f)) for (dy in floatArrayOf(-0.42f, 0.08f)) {
            oval.set(x + s * dx, y + s * dy, x + s * (dx + 0.34f), y + s * (dy + 0.34f))
            c.drawRoundRect(oval, s * 0.07f, s * 0.07f, p)
        }
    }

    fun enter(c: Canvas, p: Paint, x: Float, y: Float, s: Float, color: Int) {
        pen(p, color, s)
        path.reset()
        path.moveTo(x + s * 0.20f, y - s * 0.40f)
        path.lineTo(x + s * 0.62f, y - s * 0.40f)
        path.lineTo(x + s * 0.62f, y + s * 0.18f)
        path.lineTo(x - s * 0.62f, y + s * 0.18f)
        c.drawPath(path, p)
        c.drawLine(x - s * 0.62f, y + s * 0.18f, x - s * 0.34f, y - s * 0.10f, p)
        c.drawLine(x - s * 0.62f, y + s * 0.18f, x - s * 0.34f, y + s * 0.46f, p)
    }

    /** A grinning face, as on the reference key. */
    fun smile(c: Canvas, p: Paint, x: Float, y: Float, s: Float, color: Int) {
        pen(p, color, s)
        c.drawCircle(x, y, s * 0.56f, p)
        oval.set(x - s * 0.32f, y - s * 0.28f, x + s * 0.32f, y + s * 0.34f)
        p.style = Paint.Style.FILL_AND_STROKE
        c.drawArc(oval, 0f, 180f, true, p)
        p.style = Paint.Style.FILL
        c.drawCircle(x - s * 0.19f, y - s * 0.17f, s * 0.08f, p)
        c.drawCircle(x + s * 0.19f, y - s * 0.17f, s * 0.08f, p)
    }

    fun globe(c: Canvas, p: Paint, x: Float, y: Float, s: Float, color: Int) {
        pen(p, color, s * 0.82f)
        val r = s * 0.56f
        c.drawCircle(x, y, r, p)
        oval.set(x - r * 0.46f, y - r, x + r * 0.46f, y + r)
        c.drawOval(oval, p)
        c.drawLine(x, y - r, x, y + r, p)
        c.drawLine(x - r, y, x + r, y, p)
        c.drawLine(x - r * 0.84f, y - r * 0.52f, x + r * 0.84f, y - r * 0.52f, p)
        c.drawLine(x - r * 0.84f, y + r * 0.52f, x + r * 0.84f, y + r * 0.52f, p)
    }

    fun clipboard(c: Canvas, p: Paint, x: Float, y: Float, s: Float, color: Int) {
        pen(p, color, s)
        oval.set(x - s * 0.38f, y - s * 0.40f, x + s * 0.38f, y + s * 0.55f)
        c.drawRoundRect(oval, s * 0.1f, s * 0.1f, p)
        oval.set(x - s * 0.18f, y - s * 0.55f, x + s * 0.18f, y - s * 0.28f)
        c.drawRoundRect(oval, s * 0.08f, s * 0.08f, p)
        c.drawLine(x - s * 0.17f, y + s * 0.04f, x + s * 0.17f, y + s * 0.04f, p)
        c.drawLine(x - s * 0.17f, y + s * 0.27f, x + s * 0.17f, y + s * 0.27f, p)
    }

    /** Three sliders. */
    fun settings(c: Canvas, p: Paint, x: Float, y: Float, s: Float, color: Int) {
        pen(p, color, s)
        val knobs = floatArrayOf(0.18f, -0.20f, 0.05f)
        for (i in 0..2) {
            val yy = y + (i - 1) * s * 0.36f
            c.drawLine(x - s * 0.52f, yy, x + s * 0.52f, yy, p)
            p.style = Paint.Style.FILL
            c.drawCircle(x + s * knobs[i], yy, s * 0.13f, p)
            p.style = Paint.Style.STROKE
        }
    }

    /** Paint swatches for the theme button. */
    fun theme(c: Canvas, p: Paint, x: Float, y: Float, s: Float, color: Int) {
        pen(p, color, s)
        c.drawCircle(x, y, s * 0.54f, p)
        p.style = Paint.Style.FILL
        oval.set(x - s * 0.54f, y - s * 0.54f, x + s * 0.54f, y + s * 0.54f)
        c.drawArc(oval, 90f, 180f, true, p)
    }

    fun hide(c: Canvas, p: Paint, x: Float, y: Float, s: Float, color: Int) {
        pen(p, color, s)
        c.drawLine(x - s * 0.40f, y - s * 0.18f, x, y + s * 0.22f, p)
        c.drawLine(x, y + s * 0.22f, x + s * 0.40f, y - s * 0.18f, p)
    }
}
