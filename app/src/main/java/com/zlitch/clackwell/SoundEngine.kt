package com.zlitch.clackwell

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock

enum class Cue(val file: String, val takes: Int) {
    KEY("key", 6), NUMBER("number", 3), RELEASE("release", 3), SPACE("space", 2), SHIFT("shift", 1),
    BACKSPACE("backspace", 1), ENTER("enter", 1), CARRIAGE("carriage", 1), EMOJI("emoji", 1), OPEN("open", 1)
}

/**
 * Low-latency playback: short PCM samples decoded once into a SoundPool and triggered from a
 * dedicated audio-priority thread, so the touch/input thread never waits on audio.
 *
 * Audio focus: key sounds use the "sonification" usage and deliberately do NOT request focus, so
 * music and podcasts keep playing. They are muted in silent/vibrate mode and during calls.
 */
class SoundEngine(context: Context) {
    private val app = context.applicationContext
    private val prefs = Prefs(app)
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val thread = HandlerThread("typewriter-audio", Process.THREAD_PRIORITY_AUDIO).apply { start() }
    private val handler = Handler(thread.looper)
    private val variator = SoundVariator()

    // Touched only on the audio thread.
    private var pool: SoundPool? = null
    private var style: SoundStyle? = null
    private val samples = HashMap<Cue, IntArray>()
    private val loaded = HashSet<Int>()
    private var lastStrike = 0L

    /** (Re)loads samples if the selected style changed. Safe to call often. */
    fun prepare() { handler.post { load(prefs.soundStyle) } }

    private fun load(wanted: SoundStyle) {
        if (wanted == style && pool != null) return
        pool?.release()
        samples.clear(); loaded.clear()
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        val created = SoundPool.Builder().setMaxStreams(10).setAudioAttributes(attributes).build()
        created.setOnLoadCompleteListener { _, id, status -> if (status == 0) handler.post { loaded.add(id) } }
        for (cue in Cue.values()) {
            samples[cue] = IntArray(cue.takes) { i ->
                // Looked up by name so any style/cue file can be swapped for a real recording.
                val res = app.resources.getIdentifier("${wanted.id}_${cue.file}_${i + 1}", "raw", app.packageName)
                if (res == 0) 0 else created.load(app, res, 1)
            }
        }
        pool = created
        style = wanted
    }

    fun isMutedBySystem(): Boolean =
        audio.ringerMode != AudioManager.RINGER_MODE_NORMAL || audio.mode != AudioManager.MODE_NORMAL

    fun play(cue: Cue) {
        if (!prefs.soundOn) return
        val actual = if (!prefs.specialSounds && cue != Cue.KEY && cue != Cue.RELEASE) {
            if (cue == Cue.OPEN || cue == Cue.CARRIAGE) return else Cue.KEY
        } else cue
        val asked = SystemClock.uptimeMillis()
        handler.post { playNow(actual, asked) }
    }

    private fun playNow(cue: Cue, asked: Long) {
        if (isMutedBySystem()) return
        // A late click is worse than none (can happen right after the thread was busy loading).
        if (SystemClock.uptimeMillis() - asked > 150) return
        load(prefs.soundStyle)
        val gap = asked - lastStrike
        if (cue == Cue.RELEASE && gap < 40) return
        if (cue != Cue.RELEASE) lastStrike = asked
        start(cue, if (cue == Cue.RELEASE) 1000L else gap)
        // Now and then the carriage is heard stepping along after a word.
        if (cue == Cue.SPACE && prefs.specialSounds && variator.chance(0.08f)) {
            handler.postDelayed({ start(Cue.CARRIAGE, 1000L) }, 55)
        }
    }

    private fun start(cue: Cue, gap: Long) {
        val ids = samples[cue] ?: return
        val id = ids[variator.take(cue.name, ids.size)]
        if (id !in loaded) return
        val amount = prefs.variation
        val volume = (prefs.volume * variator.gain(amount, gap)).coerceIn(0f, 1f)
        val priority = if (cue == Cue.ENTER) 2 else if (cue == Cue.RELEASE) 0 else 1
        pool?.play(id, volume, volume, priority, 0, variator.rate(amount))
    }

    fun release() {
        handler.post { pool?.release(); pool = null; thread.quitSafely() }
    }
}
