// Pure Kotlin (no Android imports). Offline word completion and simple spelling help.
package com.zlitch.clackwell

/** literal = exactly what was typed (shown in quotes); words = up to two other offers. */
class Suggestions(val literal: String, val words: List<String>)

class Suggester(wordsByFrequency: List<String>, private val english: Boolean = false) {
    private val sorted: Array<String> = wordsByFrequency.toTypedArray()
    private val rank: IntArray

    init {
        val position = HashMap<String, Int>(sorted.size * 2)
        wordsByFrequency.forEachIndexed { i, w -> if (!position.containsKey(w)) position[w] = i }
        java.util.Arrays.sort(sorted)
        rank = IntArray(sorted.size) { position[sorted[it]] ?: sorted.size }
    }

    private fun lowerBound(key: String): Int {
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid] < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun rankOf(word: String): Int {
        val i = lowerBound(word)
        return if (i < sorted.size && sorted[i] == word) rank[i] else -1
    }

    /** Most frequent words that start with prefix (the prefix itself excluded). */
    private fun completions(prefix: String, count: Int): List<String> {
        val best = ArrayList<Int>(count + 1)
        var i = lowerBound(prefix)
        while (i < sorted.size && sorted[i].startsWith(prefix)) {
            if (sorted[i] != prefix && (best.size < count || rank[i] < rank[best[best.size - 1]])) {
                var at = best.size
                while (at > 0 && rank[best[at - 1]] > rank[i]) at--
                best.add(at, i)
                if (best.size > count) best.removeAt(best.size - 1)
            }
            i++
        }
        return best.map { sorted[it] }
    }

    /** Known words one edit away (swap, drop, add or change one letter), most frequent first. */
    private fun corrections(word: String, count: Int): List<String> {
        if (word.length < 3 || word.length > 20) return emptyList()
        val found = HashMap<String, Int>()
        fun tryWord(candidate: String) {
            if (candidate == word || found.containsKey(candidate)) return
            val r = rankOf(candidate)
            if (r >= 0) found[candidate] = r
        }
        for (i in 0..word.length) {
            val head = word.substring(0, i)
            val tail = word.substring(i)
            if (tail.isNotEmpty()) tryWord(head + tail.substring(1))
            if (tail.length > 1) tryWord(head + tail[1] + tail[0] + tail.substring(2))
            for (c in ALPHABET) {
                tryWord(head + c + tail)
                if (tail.isNotEmpty()) tryWord(head + c + tail.substring(1))
            }
        }
        return found.keys.sortedBy { found[it] }.take(count)
    }

    fun suggest(typed: String): Suggestions {
        if (typed.isEmpty()) return Suggestions("", emptyList())
        val key = TextRules.lower(typed)
        val offers = ArrayList<String>()
        if (english) CONTRACTIONS[key]?.let { offers.add(it) }
        // A word the list doesn't know (and that isn't the start of one) is probably a typo.
        val own = rankOf(key)
        val complete = completions(key, 3)
        if (own >= 0) offers.addAll(complete)
        else {
            // Unknown so far: it is either half a word or a typo. A fix goes first when it is far
            // more common than the best way of finishing what was typed.
            val fixes = corrections(key, 2)
            val finishRank = if (complete.isEmpty()) Int.MAX_VALUE else rankOf(complete[0])
            if (fixes.isNotEmpty() && rankOf(fixes[0]) < finishRank / 3) {
                offers.add(fixes[0])
                offers.addAll(complete)
                offers.addAll(fixes.drop(1))
            } else {
                offers.addAll(complete)
                offers.addAll(fixes)
            }
        }
        return Suggestions(typed, offers.map { matchCase(typed, it) }.filter { it != typed }.distinct().take(2))
    }

    private fun matchCase(typed: String, word: String): String {
        val fixed = if (english && (word == "i" || word.startsWith("i'"))) "I" + word.substring(1) else word
        return when {
            typed.length > 1 && typed == TextRules.upper(typed) -> TextRules.upper(fixed)
            Character.isUpperCase(typed[0]) -> TextRules.upper(fixed.substring(0, 1)) + fixed.substring(1)
            else -> fixed
        }
    }

    companion object {
        private const val ALPHABET = "abcdefghijklmnopqrstuvwxyzàâäçéèêëîïíñóôöùûüúß'"
        private val CONTRACTIONS = mapOf("i" to "I", "im" to "I'm", "ive" to "I've", "dont" to "don't",
            "cant" to "can't", "wont" to "won't", "isnt" to "isn't", "didnt" to "didn't", "doesnt" to "doesn't",
            "youre" to "you're", "thats" to "that's", "whats" to "what's", "theyre" to "they're")

        private fun isWordChar(c: Char) = Character.isLetter(c) || c == '\'' || c == '’' ||
            Character.getType(c) == Character.NON_SPACING_MARK.toInt()

        /** The word that ends at the cursor, or "" if the text doesn't end in one. */
        fun currentWord(before: CharSequence): String {
            var i = before.length
            while (i > 0 && isWordChar(before[i - 1])) i--
            // A word glued to digits or symbols (user@host, abc123) is not offered for replacement.
            if (i > 0 && !before[i - 1].isWhitespace() && before[i - 1] !in "([{\"“‘-—.,!?;:\n") return ""
            var word = before.substring(i)
            while (word.startsWith("'")) word = word.substring(1)
            return if (word.length > 24) "" else word
        }

        fun startsWithLetter(after: CharSequence?) = after != null && after.isNotEmpty() && Character.isLetterOrDigit(after[0])
    }
}
