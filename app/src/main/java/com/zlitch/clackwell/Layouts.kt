package com.zlitch.clackwell

enum class KeyType { CHAR, SHIFT, BACKSPACE, ENTER, SPACE, SYMBOLS, SYMBOLS2, LETTERS, EMOJI, LANGUAGE, MIC, GAP }

class KeyDef(val type: KeyType, val label: String = "", val weight: Float = 1f, val alternates: List<String> = emptyList())

enum class Language(val code: String, val title: String, val rows: List<String>) {
    ENGLISH("EN", "English (QWERTY)", listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")),
    FRENCH("FR", "Français (AZERTY)", listOf("azertyuiop", "qsdfghjklm", "wxcvbn'")),
    GERMAN("DE", "Deutsch (QWERTZ)", listOf("qwertzuiop", "asdfghjkl", "yxcvbnm")),
    SPANISH("ES", "Español", listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm"))
}

enum class Page { LETTERS, SYMBOLS_1, SYMBOLS_2 }

object Layouts {
    private val accents = mapOf(
        'a' to "àáâäãåæā", 'c' to "çćč", 'e' to "èéêëēę", 'i' to "ìíîïī", 'n' to "ñń",
        'o' to "òóôöõøœō", 's' to "ßśš", 'u' to "ùúûüū", 'y' to "ýÿ", 'z' to "žźż")

    private fun chars(s: String) = s.map { KeyDef(KeyType.CHAR, it.toString()) }

    private fun letters(lang: Language): List<List<KeyDef>> {
        val top = lang.rows[0].mapIndexed { i, c ->
            val digit = ((i + 1) % 10).toString()
            KeyDef(KeyType.CHAR, c.toString(), 1f, listOf(digit) + (accents[c] ?: "").map { it.toString() })
        }
        fun row(s: String) = s.map { c -> KeyDef(KeyType.CHAR, c.toString(), 1f, (accents[c] ?: "").map { it.toString() }) }
        var middle = row(lang.rows[1])
        if (middle.size < 10) {
            val pad = KeyDef(KeyType.GAP, weight = (10 - middle.size) / 2f)
            middle = listOf(pad) + middle + listOf(pad)
        }
        val third = listOf(KeyDef(KeyType.SHIFT, weight = 1.5f)) + row(lang.rows[2]) +
            listOf(KeyDef(KeyType.BACKSPACE, weight = 1.5f))
        val bottom = bottomRow(KeyDef(KeyType.SYMBOLS, "123", 1.4f), lang.code)
        val strip = listOf(
            KeyDef(KeyType.LANGUAGE, weight = 1.5f), KeyDef(KeyType.GAP, weight = 2.75f),
            KeyDef(KeyType.CHAR, ".", 1.5f, listOf(",", "?", "!", "'", "\"", "-", ":", "@")),
            KeyDef(KeyType.GAP, weight = 2.75f), KeyDef(KeyType.MIC, weight = 1.5f))
        return listOf(top, middle, third, bottom, strip)
    }

    private fun bottomRow(switch: KeyDef, spaceLabel: String) = listOf(
        switch, KeyDef(KeyType.EMOJI), KeyDef(KeyType.SPACE, spaceLabel, 5f), KeyDef(KeyType.ENTER, weight = 2.6f))

    /** The slim row under the machine: no keycaps, like the globe in the design reference. */
    private fun strip(left: KeyDef, right: KeyDef) = listOf(left, KeyDef(KeyType.GAP, weight = 7f), right)

    private fun symbolRows(left: String, right: String) = listOf(
        bottomRow(KeyDef(KeyType.LETTERS, "ABC", 1.4f), ""),
        strip(KeyDef(KeyType.CHAR, left, 1.5f), KeyDef(KeyType.CHAR, right, 1.5f)))

    private fun symbols1(): List<List<KeyDef>> = listOf(
        chars("1234567890"),
        chars("@#\$₹&-+()/"),
        listOf(KeyDef(KeyType.SYMBOLS2, "=\\<", 1.5f)) + chars("*\"':;!?") + listOf(KeyDef(KeyType.BACKSPACE, weight = 1.5f))
    ) + symbolRows(",", ".")

    private fun symbols2(): List<List<KeyDef>> = listOf(
        chars("~`|•√π÷×¶∆"),
        chars("£€¥¢^°={}\\"),
        listOf(KeyDef(KeyType.SYMBOLS, "123", 1.5f)) + chars("_%©✓[]§") + listOf(KeyDef(KeyType.BACKSPACE, weight = 1.5f))
    ) + symbolRows("<", ">")

    fun build(page: Page, lang: Language): List<List<KeyDef>> = when (page) {
        Page.LETTERS -> letters(lang)
        Page.SYMBOLS_1 -> symbols1()
        Page.SYMBOLS_2 -> symbols2()
    }
}
