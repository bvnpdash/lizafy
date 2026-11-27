package com.zlitch.clackwell

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.Vibrator
import android.provider.Settings

/**
 * millis/weight: a single pulse. beats: the same keystroke as a little sequence of
 * (silence ms, pulse ms, strength) steps, used where the motor can vary its strength.
 */
enum class Tap(val millis: Long, val weight: Float, val beats: FloatArray) {
    // light tick as the lever gives way, then the type bar hitting the platen
    KEY(9L, 0.62f, floatArrayOf(0f, 5f, 0.30f, 6f, 9f, 0.80f)),
    SPACE(13L, 0.75f, floatArrayOf(0f, 6f, 0.28f, 5f, 15f, 0.68f)),
    SOFT(7L, 0.45f, floatArrayOf(0f, 7f, 0.42f)),
    HEAVY(18L, 1.0f, floatArrayOf(0f, 7f, 0.50f, 12f, 16f, 1.0f)),
    // lever, ratcheting carriage, end stop
    CARRIAGE(40L, 1.0f, floatArrayOf(0f, 12f, 0.75f, 52f, 5f, 0.30f, 38f, 5f, 0.32f, 32f, 5f, 0.34f,
        27f, 5f, 0.36f, 22f, 5f, 0.38f, 20f, 5f, 0.40f, 30f, 22f, 1.0f))
}

/** Short one-shot pulses. Amplitude control where the hardware has it, duration scaling otherwise. */
class Haptics(context: Context) {
    private val app = context.applicationContext
    private val prefs = Prefs(app)
    @Suppress("DEPRECATION")
    private val vibrator = app.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private val touchUsage = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

    // VibrationEffect is API 26+. Reflection keeps this buildable against older SDK stubs too.
    private val effectClass: Class<*>? =
        if (Build.VERSION.SDK_INT >= 26) Class.forName("android.os.VibrationEffect") else null
    private val oneShot = effectClass?.getMethod("createOneShot", Long::class.javaPrimitiveType, Int::class.javaPrimitiveType)
    private val vibrateEffect = effectClass?.let { Vibrator::class.java.getMethod("vibrate", it, AudioAttributes::class.java) }
    private val waveform = effectClass?.getMethod("createWaveform", LongArray::class.java, IntArray::class.java, Int::class.javaPrimitiveType)
    private val hasAmplitude: Boolean =
        Build.VERSION.SDK_INT >= 26 && Vibrator::class.java.getMethod("hasAmplitudeControl").invoke(vibrator) == true

    private fun systemAllows(): Boolean =
        Settings.System.getInt(app.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0

    @Suppress("DEPRECATION")
    fun tap(kind: Tap) {
        val strength = prefs.hapticStrength
        if (!prefs.hapticsOn || strength <= 0f || !vibrator.hasVibrator() || !systemAllows()) return
        val make = oneShot
        val run = vibrateEffect
        val wave = waveform
        if (wave != null && run != null && hasAmplitude) {
            val steps = kind.beats.size / 3
            val timings = LongArray(steps * 2)
            val amplitudes = IntArray(steps * 2)
            for (i in 0 until steps) {
                timings[i * 2] = kind.beats[i * 3].toLong()
                timings[i * 2 + 1] = kind.beats[i * 3 + 1].toLong()
                amplitudes[i * 2 + 1] = (255 * strength * kind.beats[i * 3 + 2]).toInt().coerceIn(1, 255)
            }
            run.invoke(vibrator, wave.invoke(null, timings, amplitudes, -1), touchUsage)
        } else if (make != null && run != null) {
            val amplitude = if (hasAmplitude) (255 * strength * kind.weight).toInt().coerceIn(1, 255) else -1
            val millis = if (hasAmplitude) kind.millis else Math.max(4L, (kind.millis * (0.4f + strength)).toLong())
            run.invoke(vibrator, make.invoke(null, millis, amplitude), touchUsage)
        } else {
            vibrator.vibrate(Math.max(4L, (kind.millis * (0.4f + strength)).toLong()), touchUsage)
        }
    }
}
