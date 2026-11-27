// Pure Kotlin (no Android imports) so it can be unit-tested on any JVM.
package com.zlitch.clackwell

enum class ShiftMode { OFF, ON, CAPS }

/** Tap = one capital, double-tap = caps lock. Also tracks auto-capitalisation and hold-to-chord. */
class ShiftState(private val doubleTapMs: Long = 320L) {
    var mode = ShiftMode.OFF
        private set
    private var lastTap = -100000L
    private var manual = false
    private var held = false
    private var chorded = false

    val upper: Boolean get() = mode != ShiftMode.OFF

    fun press(now: Long) {
        val doubleTap = now - lastTap <= doubleTapMs
        mode = when {
            mode == ShiftMode.CAPS -> ShiftMode.OFF
            doubleTap -> ShiftMode.CAPS
            mode == ShiftMode.ON -> ShiftMode.OFF
            else -> ShiftMode.ON
        }
        lastTap = if (doubleTap) -100000L else now
        manual = true
        held = true
        chorded = false
    }

    fun release() {
        held = false
        if (chorded && mode == ShiftMode.ON) { mode = ShiftMode.OFF; manual = false }
        chorded = false
    }

    fun onCharacterTyped() {
        lastTap = -100000L
        if (held) { chorded = true; return }
        if (mode == ShiftMode.ON) mode = ShiftMode.OFF
        manual = false
    }

    fun setAuto(capitalise: Boolean) {
        if (mode == ShiftMode.CAPS || manual || held) return
        mode = if (capitalise) ShiftMode.ON else ShiftMode.OFF
    }

    fun reset() { mode = ShiftMode.OFF; manual = false; held = false; chorded = false; lastTap = -100000L }
}

object TextRules {
    fun upper(s: String): String = String(s.toCharArray().map { Character.toUpperCase(it) }.toCharArray())
    fun lower(s: String): String = String(s.toCharArray().map { Character.toLowerCase(it) }.toCharArray())

    /** lower -> UPPER -> Title -> lower, used by the toolbar "Aa" tool. */
    fun cycleCase(s: String): String {
        val up = upper(s)
        val low = lower(s)
        if (s == low) return up
        if (s == up) {
            var start = true
            val out = StringBuilder()
            for (c in low) {
                out.append(if (start) Character.toUpperCase(c) else c)
                start = c.isWhitespace()
            }
            if (out.toString() != s) return out.toString()
        }
        return low
    }

    /** True when a second space should turn "word " into "word. ". */
    fun doubleSpaceMakesPeriod(before: CharSequence): Boolean =
        before.length >= 2 && before[before.length - 1] == ' ' && Character.isLetterOrDigit(before[before.length - 2])

    private fun isExtender(cp: Int): Boolean {
        val type = Character.getType(cp)
        return type == Character.NON_SPACING_MARK.toInt() || type == Character.ENCLOSING_MARK.toInt() ||
            type == Character.COMBINING_SPACING_MARK.toInt() || cp in 0xFE00..0xFE0F ||
            cp in 0x1F3FB..0x1F3FF || cp in 0xE0020..0xE007F
    }

    private fun isFlagLetter(cp: Int) = cp in 0x1F1E6..0x1F1FF

    /** Number of UTF-16 units making up the last visible character (emoji-safe backspace). */
    fun lastClusterLength(text: CharSequence): Int {
        var i = text.length
        if (i == 0) return 0
        if (i >= 2 && text[i - 2] == '\r' && text[i - 1] == '\n') return 2
        while (i > 0) {
            var cp = Character.codePointBefore(text, i)
            i -= Character.charCount(cp)
            if (isExtender(cp)) continue
            if (isFlagLetter(cp)) {
                var run = 0
                var j = i
                while (j > 0 && isFlagLetter(Character.codePointBefore(text, j))) {
                    j -= Character.charCount(Character.codePointBefore(text, j)); run++
                }
                if (run % 2 == 1) i -= 2
            }
            if (i > 0) {
                cp = Character.codePointBefore(text, i)
                if (cp == 0x200D) { i -= 1; continue }   // zero-width joiner: keep eating
            }
            break
        }
        return text.length - i
    }

    /** Length of the previous word plus trailing spaces, for accelerated delete. */
    fun lastWordLength(text: CharSequence): Int {
        var i = text.length
        while (i > 0 && text[i - 1].isWhitespace()) i--
        while (i > 0 && !text[i - 1].isWhitespace()) i--
        return text.length - i
    }
}

/** Timing for held backspace: a pause, then steadily faster, finally word by word. */
object DeleteRepeat {
    const val FIRST_DELAY_MS = 380L
    fun delayAfter(repeat: Int): Long = if (repeat >= WORD_AFTER) 140L else Math.max(34L, 105L - repeat * 5L)
    const val WORD_AFTER = 28
    fun deletesWords(repeat: Int) = repeat >= WORD_AFTER
}

enum class EnterKind { NEWLINE, ACTION }

/** Mirrors the android.view.inputmethod.EditorInfo imeOptions bits. */
object EnterRules {
    const val MASK_ACTION = 0xff
    const val ACTION_UNSPECIFIED = 0
    const val ACTION_NONE = 1
    const val FLAG_NO_ENTER_ACTION = 0x40000000

    fun kind(imeOptions: Int): EnterKind {
        val action = imeOptions and MASK_ACTION
        if (imeOptions and FLAG_NO_ENTER_ACTION != 0) return EnterKind.NEWLINE
        return if (action == ACTION_NONE || action == ACTION_UNSPECIFIED) EnterKind.NEWLINE else EnterKind.ACTION
    }

    fun label(imeOptions: Int): String {
        if (kind(imeOptions) == EnterKind.NEWLINE) return "↵"
        return when (imeOptions and MASK_ACTION) {
            2 -> "GO"
            3 -> "SEARCH"
            4 -> "SEND"
            5 -> "NEXT"
            6 -> "DONE"
            7 -> "PREV"
            else -> "↵"
        }
    }
}

/** Damped spring for key travel. 0 = raised, 1 = fully down. */
class KeySpring {
    var position = 0f
        private set
    private var velocity = 0f
    private var target = 0f

    private var age = 0f
    private var releasePending = false

    fun press() { age = 0f; releasePending = false; target = 1f; position = Math.max(position, 0.72f); velocity = 0f }
    /** Even the quickest tap stays down long enough for the eye to see the key travel. */
    fun release() { if (age < MIN_DOWN) releasePending = true else target = 0f }
    fun reset() { position = 0f; velocity = 0f; target = 0f; releasePending = false }

    /** Advances by dt seconds; returns true while still moving. */
    fun step(dt: Float): Boolean {
        var left = Math.min(dt, 0.05f)
        age += left
        if (releasePending && age >= MIN_DOWN) { releasePending = false; target = 0f }
        while (left > 0f) {
            val h = Math.min(left, 0.004f)
            val stiffness = if (target > 0f) 2600f else 1100f
            val damping = if (target > 0f) 90f else 43f   // ratio ~0.65 on release: tiny overshoot
            velocity += (stiffness * (target - position) - damping * velocity) * h
            position += velocity * h
            left -= h
        }
        if (!releasePending && Math.abs(target - position) < 0.004f && Math.abs(velocity) < 0.08f) {
            position = target; velocity = 0f
            return false
        }
        return true
    }

    private companion object { const val MIN_DOWN = 0.055f }
}

/** How far a struck bar is thrown, 0..1, at a time in milliseconds after the key goes down. */
object StrikeMotion {
    const val ATTACK_MS = 22L
    const val LENGTH_MS = 210L
    fun at(ms: Long): Float {
        if (ms <= 0L || ms >= LENGTH_MS) return 0f
        if (ms < ATTACK_MS) { val u = ms / ATTACK_MS.toFloat(); return u * u }   // accelerates into the hit
        val t = (ms - ATTACK_MS).toFloat()
        val fade = 1f - t / (LENGTH_MS - ATTACK_MS)
        return (Math.exp(-t / 55.0) * Math.cos(t / 30.0)).toFloat() * fade       // snaps back, one small rebound
    }
}

/** Picks sample takes without back-to-back repeats and applies small pitch / volume drift. */
class SoundVariator(private val random: java.util.Random = java.util.Random()) {
    private val bags = HashMap<String, MutableList<Int>>()
    private val last = HashMap<String, Int>()

    fun take(group: String, count: Int): Int {
        if (count <= 1) return 0
        var bag = bags[group]
        if (bag == null || bag.isEmpty()) {
            bag = (0 until count).toMutableList()
            java.util.Collections.shuffle(bag, random)
            bags[group] = bag
        }
        var index = bag.size - 1
        if (bag[index] == last[group] && bag.size > 1) index--
        val pick = bag.removeAt(index)
        last[group] = pick
        return pick
    }

    /** amount is 0..1 from settings. Max drift is +/-4.5% pitch. */
    fun rate(amount: Float): Float = 1f + (random.nextFloat() * 2f - 1f) * 0.045f * amount

    /** Never louder than 1; up to 18% quieter. Fast runs are ducked so they don't smear into noise. */
    fun gain(amount: Float, sinceLastMs: Long): Float {
        val drift = 1f - random.nextFloat() * 0.18f * amount
        val duck = if (sinceLastMs < 70) 0.76f else if (sinceLastMs < 130) 0.88f else 1f
        return drift * duck
    }

    fun chance(p: Float) = random.nextFloat() < p
}
