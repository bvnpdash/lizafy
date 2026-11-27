package com.zlitch.clackwell

import android.content.Context
import android.content.SharedPreferences

enum class SoundStyle(val id: String, val title: String) {
    CLASSIC("classic", "Classic Typewriter"), SOFT("soft", "Soft Mechanical"),
    HEAVY("heavy", "Heavy Typewriter"), MINIMAL("minimal", "Minimal")
}

/** All settings stay in this app's private storage. Typed text is never written here. */
class Prefs(context: Context) {
    val store: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun bool(key: String, default: Boolean) = store.getBoolean(key, default)
    fun int(key: String, default: Int) = store.getInt(key, default)
    fun put(key: String, value: Boolean) = store.edit().putBoolean(key, value).apply()
    fun put(key: String, value: Int) = store.edit().putInt(key, value).apply()
    fun put(key: String, value: String) = store.edit().putString(key, value).apply()

    val soundOn get() = bool(SOUND, true)
    val volume get() = int(VOLUME, 70) / 100f
    val variation get() = int(VARIATION, 60) / 100f
    val specialSounds get() = bool(SPECIAL, true)
    val openSound get() = bool(OPEN_SOUND, false)
    val soundStyle get() = SoundStyle.values()[int(STYLE, 0).coerceIn(0, SoundStyle.values().size - 1)]
    val hapticsOn get() = bool(HAPTICS, true)
    val hapticStrength get() = int(HAPTIC_STRENGTH, 45) / 100f
    val theme get() = KeyboardTheme.ALL[int(THEME, DEFAULT_THEME).coerceIn(0, KeyboardTheme.ALL.size - 1)]
    val keySize get() = int(KEY_SIZE, 100) / 100f
    val heightScale get() = int(HEIGHT, 100) / 100f
    val spacing get() = int(SPACING, 3)
    val backgroundIntensity get() = int(BACKGROUND, 100) / 100f
    val capitalLegends get() = bool(CAPITAL_LEGENDS, true)
    val suggestions get() = bool(SUGGESTIONS, true)
    val numberHints get() = bool(NUMBER_HINTS, false)
    val autoCaps get() = bool(AUTO_CAPS, true)
    val doubleSpacePeriod get() = bool(DOUBLE_SPACE, true)

    var enabledLanguages: List<Language>
        get() {
            val saved = (store.getString(LANGUAGES, "ENGLISH") ?: "ENGLISH").split(",")
            val found = Language.values().filter { it.name in saved }
            return if (found.isEmpty()) listOf(Language.ENGLISH) else found
        }
        set(value) = put(LANGUAGES, value.joinToString(",") { it.name })

    var currentLanguage: Language
        get() {
            val name = store.getString(CURRENT_LANGUAGE, "")
            return enabledLanguages.firstOrNull { it.name == name } ?: enabledLanguages[0]
        }
        set(value) = put(CURRENT_LANGUAGE, value.name)

    var recentEmoji: List<String>
        get() = (store.getString(RECENT, "") ?: "").split(" ").filter { it.isNotEmpty() }
        set(value) = put(RECENT, value.take(32).joinToString(" "))

    var emojiTone: Int
        get() = int(EMOJI_TONE, 0).coerceIn(0, 5)
        set(value) = put(EMOJI_TONE, value)

    companion object {
        const val EMOJI_TONE = "emoji_tone"
        const val DEFAULT_THEME = 1
        const val SOUND = "sound"
        const val VOLUME = "volume"
        const val VARIATION = "variation"
        const val SPECIAL = "special"
        const val OPEN_SOUND = "open_sound"
        const val STYLE = "style"
        const val HAPTICS = "haptics"
        const val HAPTIC_STRENGTH = "haptic_strength"
        const val THEME = "theme"
        const val KEY_SIZE = "key_size"
        const val HEIGHT = "height"
        const val SPACING = "spacing"
        const val BACKGROUND = "background"
        const val CAPITAL_LEGENDS = "capital_legends"
        const val SUGGESTIONS = "suggestions"
        const val NUMBER_HINTS = "number_hints"
        const val AUTO_CAPS = "auto_caps"
        const val DOUBLE_SPACE = "double_space"
        const val LANGUAGES = "languages"
        const val CURRENT_LANGUAGE = "current_language"
        const val RECENT = "recent_emoji"
    }
}
