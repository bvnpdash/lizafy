package com.zlitch.clackwell

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient

/** Which material a cap is made of. */
enum class CapKind { NORMAL, LACQUER_RED, SPACE_BAR }

/**
 * Draws the machine: chassis, rods, springs and chrome-rimmed caps. Static art is rendered once
 * into bitmaps; per frame only the moving parts are composited.
 */
class KeyArtist(private val theme: KeyboardTheme, private val density: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private val caps = HashMap<String, Bitmap>()
    private var shadow: Bitmap? = null

    companion object {
        const val BARS = 17
        const val TRAVEL_DP = 3f
        const val WALL_DP = 2.4f   // visible side wall under a raised cap
    }
    private fun dp(v: Float) = v * density

    private fun alpha(color: Int, a: Float): Int =
        ((a.coerceIn(0f, 1f) * 255).toInt() shl 24) or (color and 0x00FFFFFF)

    private fun fill(color: Int) { paint.shader = null; paint.style = Paint.Style.FILL; paint.color = color }
    private fun stroke(color: Int, width: Float) {
        paint.shader = null; paint.style = Paint.Style.STROKE; paint.strokeWidth = width; paint.color = color
    }
    private fun gradient(shader: Shader) {
        paint.style = Paint.Style.FILL; paint.color = -0x1000000; paint.shader = shader
    }
    private fun vertical(top: Float, bottom: Float, a: Int, b: Int) =
        LinearGradient(0f, top, 0f, bottom, a, b, Shader.TileMode.CLAMP)

    // ---------------------------------------------------------------- chassis and mechanism

    /** The still backdrop. rowCentres: y of the four key rows. */
    fun drawMachine(canvas: Canvas, w: Float, h: Float, rowCentres: FloatArray, intensity: Float) {
        gradient(vertical(0f, h, theme.bgTop, theme.bgBottom))
        canvas.drawRect(0f, 0f, w, h, paint)
        if (intensity <= 0f) return
        val k = intensity.coerceIn(0f, 1f)
        val pitch = rowCentres[1] - rowCentres[0]
        val unit = (w - dp(10f)) / 10f

        // Shaded cavity behind the works gives the mechanism its depth.
        canvas.save()
        canvas.scale(1f, 0.42f, w / 2f, rowCentres[1] + pitch * 0.4f)
        gradient(RadialGradient(w / 2f, rowCentres[1] + pitch * 0.4f, w * 0.50f,
            intArrayOf(alpha(theme.rod, 0.50f * k), alpha(theme.rod, 0.30f * k), alpha(theme.rod, 0f)),
            floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP))
        canvas.drawCircle(w / 2f, rowCentres[1] + pitch * 0.4f, w * 0.50f, paint)
        canvas.restore()

        // A second rank of linkage rods set further back, between the front bars.
        val gap = (barX(w, 1) - barX(w, 0))
        for (i in 0 until BARS - 1) {
            val x = barX(w, i) + gap * 0.55f
            val top = rowCentres[0]
            val bottom = rowCentres[2] + pitch * (if (i % 2 == 0) 0.55f else 0.25f)
            fill(alpha(theme.rod, 0.38f * k)); box.set(x - dp(1.5f), top, x + dp(1.5f), bottom)
            canvas.drawRoundRect(box, dp(1.5f), dp(1.5f), paint)
            fill(alpha(theme.rodShine, 0.35f * k)); canvas.drawRect(x - dp(0.9f), top + dp(2f), x - dp(0.2f), bottom - dp(2f), paint)
        }
    }

    /** Fixed linkwork that sits in front of the type bars. Drawn on a transparent layer. */
    fun drawLinkwork(canvas: Canvas, w: Float, rowCentres: FloatArray, stems: List<FloatArray>, intensity: Float) {
        if (intensity <= 0f) return
        val k = intensity.coerceIn(0f, 1f)
        val pitch = rowCentres[1] - rowCentres[0]
        val unit = (w - dp(10f)) / 10f

        // Side linkages with pivot pins.
        for (x in floatArrayOf(dp(5f) + unit * 0.62f, w - dp(5f) - unit * 0.62f)) {
            bar(canvas, x, rowCentres[1] - pitch * 0.2f, rowCentres[3], dp(4f), k)
            bar(canvas, x + dp(4.5f), rowCentres[1] + pitch * 0.2f, rowCentres[2] + pitch * 0.9f, dp(2.4f), k)
            for (y in floatArrayOf(rowCentres[1] - pitch * 0.12f, rowCentres[2] + pitch * 0.5f)) {
                collar(canvas, x + dp(1f), y, dp(11f), dp(4.5f), k)
                pin(canvas, x - dp(3.5f), y, dp(3.4f), k)
            }
        }

        // One lever under every letter key.
        for (s in stems) {
            bar(canvas, s[0], s[1], s[1] + pitch * 0.74f, dp(3.4f), k)
            collar(canvas, s[0], s[1] + pitch * 0.46f, dp(6f), dp(3.2f), k)
        }

        // Mounting plates and hanging pins below the space bar.
        val under = rowCentres[3] + pitch * 0.36f
        for (x in floatArrayOf(0.31f, 0.425f, 0.605f, 0.682f)) {
            bar(canvas, w * x, rowCentres[3], under + pitch * 0.52f, dp(4.6f), k)
        }
        for (x in floatArrayOf(0.345f, 0.638f)) {
            box.set(w * x - dp(14f), under, w * x + dp(14f), under + dp(10f))
            metal(box.left, box.right, k)
            canvas.drawRoundRect(box, dp(2f), dp(2f), paint)
            stroke(alpha(theme.rimDark, k), dp(0.8f)); canvas.drawRoundRect(box, dp(2f), dp(2f), paint)
            pin(canvas, w * x - dp(8f), under + dp(5f), dp(2.6f), k)
            pin(canvas, w * x + dp(8f), under + dp(5f), dp(2.6f), k)
            // Hinge barrel between the two screws.
            for (j in -1..1) collar(canvas, w * x + dp(3.4f) * j, under + dp(5f), dp(2.6f), dp(9f), k)
            bar(canvas, w * x, under + dp(9f), under + dp(15f), dp(5f), k)
        }
    }

    // ---------------------------------------------------------------- the type bars (they move)

    fun barX(w: Float, index: Int): Float = w * 0.204f + (w * 0.787f - w * 0.204f) / (BARS - 1) * index

    private val barSprites = HashMap<Int, Bitmap>()

    private fun barSprite(w: Float, length: Float, k: Float): Bitmap {
        val key = length.toInt()
        barSprites[key]?.let { return it }
        val width = Math.min((barX(w, 1) - barX(w, 0)) * 0.40f, dp(5.4f))
        val head = width * 1.42f
        val bitmap = Bitmap.createBitmap((head + dp(12f)).toInt(), (length + dp(8f)).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val left = dp(1f)
        val x = left + width / 2f
        bar(c, x, width * 0.8f, length, width, k)
        // Rounded bend, then the short leg of the hook coming back down.
        box.set(left, 0f, left + head, width * 3.9f)
        metal(box.left, box.right, k)
        c.drawRoundRect(box, head / 2f, head / 2f, paint)
        stroke(alpha(theme.rod, 0.75f * k), dp(0.6f))
        c.drawRoundRect(box, head / 2f, head / 2f, paint)
        fill(alpha(mix(theme.rod, -0x1000000, 0.45f), 0.95f * k))
        box.set(left + head * 0.46f, width * 0.80f, left + head * 0.73f, width * 4.3f)
        c.drawRoundRect(box, head * 0.135f, head * 0.135f, paint)
        stroke(alpha(-1, 0.55f * k), dp(0.7f))
        box.set(left + head * 0.14f, dp(0.8f), left + head * 0.86f, width * 2.0f)
        c.drawArc(box, 190f, 135f, false, paint)
        barSprites[key] = bitmap
        return bitmap
    }

    /**
     * throwOf[i]: 0 at rest, 1 fully struck (dips below 0 on rebound). A struck bar is yanked down,
     * leans in toward the printing point and catches the light. slide moves the whole comb sideways.
     */
    fun drawTypeBars(canvas: Canvas, w: Float, rowCentres: FloatArray, throwOf: FloatArray, slide: Float, intensity: Float) {
        if (intensity <= 0f) return
        val k = intensity.coerceIn(0f, 1f)
        val pitch = rowCentres[1] - rowCentres[0]
        val top = rowCentres[0] - pitch * 1.15f
        val half = Math.min((barX(w, 1) - barX(w, 0)) * 0.40f, dp(5.4f)) / 2f
        for (i in 0 until BARS) {
            val x = barX(w, i) + slide
            val bottom = rowCentres[3] - (if (i % 3 == 1) pitch * 0.35f else 0f)
            val sprite = barSprite(w, bottom - top, k)
            val hit = throwOf[i]
            canvas.save()
            canvas.translate(0f, hit * dp(11f))
            val towardCentre = ((BARS - 1) / 2f - i) / ((BARS - 1) / 2f)
            canvas.rotate(towardCentre * 4.5f * hit, x, bottom)
            paint.shader = null; paint.color = -1; paint.style = Paint.Style.FILL
            canvas.drawBitmap(sprite, x - half - dp(1f), top, paint)
            if (hit > 0.25f) {
                fill(alpha(-1, (hit - 0.25f) * 1.1f * k))
                box.set(x - half * 0.75f, top + dp(1f), x - half * 0.05f, top + dp(22f))
                canvas.drawRoundRect(box, half * 0.35f, half * 0.35f, paint)
            }
            canvas.restore()
        }
    }

    private fun metal(left: Float, right: Float, k: Float) {
        gradient(LinearGradient(left, 0f, Math.max(right, left + 1f), 0f,
            intArrayOf(alpha(theme.rod, k), alpha(theme.rodShine, k), alpha(theme.rod, k)),
            floatArrayOf(0f, 0.36f, 1f), Shader.TileMode.CLAMP))
    }

    /** A round-ended steel rod that casts a soft shadow onto the deck. */
    private fun bar(canvas: Canvas, x: Float, top: Float, bottom: Float, width: Float, k: Float) {
        val half = width / 2f
        for (pass in 1..3) {
            fill(alpha(0, 0.05f * k))
            box.set(x - half + dp(1.4f) * pass, top + dp(2f) * pass, x + half + dp(1.4f) * pass, bottom + dp(2f) * pass)
            canvas.drawRoundRect(box, half, half, paint)
        }
        box.set(x - half, top, x + half, bottom)
        metal(box.left, box.right, k)
        canvas.drawRoundRect(box, half, half, paint)
    }

    private fun collar(canvas: Canvas, x: Float, y: Float, width: Float, height: Float, k: Float) {
        box.set(x - width / 2f, y - height / 2f, x + width / 2f, y + height / 2f)
        metal(box.left, box.right, k)
        canvas.drawRoundRect(box, dp(1f), dp(1f), paint)
        stroke(alpha(theme.rod, 0.85f * k), dp(0.6f)); canvas.drawRoundRect(box, dp(1f), dp(1f), paint)
    }

    private fun pin(canvas: Canvas, x: Float, y: Float, radius: Float, k: Float) {
        fill(alpha(theme.rod, k)); canvas.drawCircle(x, y, radius, paint)
        fill(alpha(theme.rodShine, k)); canvas.drawCircle(x - radius * 0.2f, y - radius * 0.2f, radius * 0.55f, paint)
    }

    // ---------------------------------------------------------------- moving parts

    /** The lever under a key; follows the cap down. */
    fun drawStem(canvas: Canvas, cx: Float, cy: Float, length: Float, travel: Float, intensity: Float) {
        if (travel <= 0.01f || intensity <= 0f) return
        paint.strokeCap = Paint.Cap.ROUND
        val push = travel * dp(5f)
        stroke(alpha(theme.rod, 0.75f), dp(3.6f)); canvas.drawLine(cx, cy + push, cx, cy + length + push, paint)
        stroke(alpha(theme.rodShine, 0.95f), dp(1.2f)); canvas.drawLine(cx - dp(0.8f), cy + push, cx - dp(0.8f), cy + length + push, paint)
        paint.strokeCap = Paint.Cap.BUTT
    }

    private fun shadowBitmap(): Bitmap {
        shadow?.let { return it }
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        gradient(RadialGradient(size / 2f, size / 2f, size / 2f,
            intArrayOf(0x90000000.toInt(), 0x70000000, 0), floatArrayOf(0f, 0.68f, 1f), Shader.TileMode.CLAMP))
        c.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        shadow = bitmap
        return bitmap
    }

    /**
     * Draws one cap inside [r]. travel: 0 raised .. 1 down (may briefly dip below 0 on rebound).
     * lit: shift active.
     */
    fun drawCap(canvas: Canvas, r: RectF, kind: CapKind, travel: Float, lit: Boolean) {
        val depth = dp(TRAVEL_DP)
        val down = travel * depth
        val t = travel.coerceIn(0f, 1f)

        // Shadow tightens and darkens as the key nears the deck.
        val grow = dp(3.6f) * (1f - 0.68f * t)
        val drop = dp(5.6f) * (1f - 0.74f * t)
        box.set(r.left - grow, r.top - grow + drop, r.right + grow, r.bottom + grow + drop)
        paint.shader = null; paint.color = alpha(0, 0.82f + 0.18f * t); paint.style = Paint.Style.FILL
        canvas.drawBitmap(shadowBitmap(), null, box, paint)

        // Metal collar below the cap: gives the raised, physical side wall.
        val radius = r.height() / 2f
        fill(theme.rimDark)
        box.set(r.left + dp(0.7f), r.top + dp(WALL_DP), r.right - dp(0.7f), r.bottom + dp(WALL_DP) * 0.8f)
        canvas.drawRoundRect(box, radius, radius, paint)

        val cap = capBitmap(r.width().toInt(), r.height().toInt(), kind)
        val squash = dp(0.9f) * t
        val sink = dp(0.5f) * t
        box.set(r.left + sink, r.top + down + squash, r.right - sink, r.bottom + down)
        paint.shader = null; paint.color = -1
        canvas.drawBitmap(cap, null, box, paint)

        if (t > 0.01f) {
            fill(alpha(0, 0.13f * t))
            canvas.drawRoundRect(box, radius, radius, paint)
        }
        if (lit) {
            stroke(theme.accent, dp(2.2f))
            box.inset(dp(1.2f), dp(1.2f))
            canvas.drawRoundRect(box, radius, radius, paint)
        }
    }

    private fun capBitmap(w: Int, h: Int, kind: CapKind): Bitmap {
        val key = "$w/$h/$kind"
        caps[key]?.let { return it }
        val bitmap = Bitmap.createBitmap(Math.max(w, 1), Math.max(h, 1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val outer = RectF(0f, 0f, w.toFloat(), h.toFloat())
        fun pill(inset: Float) {
            val q = RectF(outer); q.inset(inset, inset)
            c.drawRoundRect(q, q.height() / 2f, q.height() / 2f, paint)
        }
        val rim = Math.max(dp(2.5f), Math.min(w, h) * 0.088f)

        // Dark outline, then a broad polished ring whose bands read as reflections.
        fill(mix(theme.rimDark, -0x1000000, 0.25f)); pill(0f)
        if (Math.abs(w - h) <= 2) {
            gradient(SweepGradient(w / 2f, h / 2f,
                intArrayOf(theme.rimMid, theme.rimDark, mix(theme.rimDark, theme.rimMid, 0.5f), theme.rimMid,
                    theme.rimLight, theme.rimMid, mix(theme.rimDark, theme.rimMid, 0.6f), theme.rimLight, theme.rimMid),
                floatArrayOf(0f, 0.14f, 0.27f, 0.45f, 0.62f, 0.74f, 0.84f, 0.93f, 1f)))
        } else {
            gradient(LinearGradient(0f, 0f, w * 0.06f, h.toFloat(),
                intArrayOf(theme.rimLight, theme.rimMid, mix(theme.rimMid, theme.rimDark, 0.7f), theme.rimMid),
                floatArrayOf(0f, 0.30f, 0.78f, 1f), Shader.TileMode.CLAMP))
        }
        pill(dp(0.8f))
        // Bright crest running round the top of the ring.
        stroke(alpha(-1, if (theme.dark) 0.35f else 0.62f), dp(0.8f))
        val crest = RectF(outer); crest.inset(dp(1.6f), dp(1.6f))
        c.drawArc(crest, 188f, 128f, false, paint)

        // Groove between ring and face: shadowed above, catching light below.
        gradient(vertical(0f, h.toFloat(), mix(theme.rimDark, -0x1000000, 0.2f), theme.rimLight)); pill(rim * 0.74f)

        val red = kind == CapKind.LACQUER_RED
        fill(if (red) -0x77e2de else theme.ring); pill(rim)
        val faceTop = if (red) -0x1fc5c0 else theme.faceLight
        val faceBottom = if (red) -0x39d5cf else theme.faceDark
        gradient(vertical(rim, h - rim, mix(faceTop, theme.rimDark, 0.16f), faceBottom)); pill(rim + dp(0.5f))
        gradient(vertical(rim, h - rim, faceTop, faceBottom)); pill(rim + dp(1.0f))

        // A faint sheen along the top of the face.
        val inset = rim + dp(2.2f)
        val gloss = RectF(inset + w * 0.03f, inset, w - inset - w * 0.03f, h * 0.46f)
        if (gloss.width() > 0 && gloss.height() > 0) {
            val strength = if (theme.dark && !red) 0.10f else if (red) 0.16f else 0.38f
            gradient(vertical(gloss.top, gloss.bottom, alpha(-1, strength), alpha(-1, 0f)))
            c.drawRoundRect(gloss, gloss.height() / 2f, gloss.height() / 2f, paint)
        }
        paint.shader = null
        caps[key] = bitmap
        return bitmap
    }

    private fun mix(a: Int, b: Int, f: Float): Int {
        fun ch(s: Int) = (((a shr s) and 255) * (1 - f) + ((b shr s) and 255) * f).toInt() shl s
        return -0x1000000 or ch(16) or ch(8) or ch(0)
    }

    fun release() {
        for (b in barSprites.values) b.recycle()
        barSprites.clear()
        for (b in caps.values) b.recycle()
        caps.clear()
        shadow?.recycle(); shadow = null
    }
}
