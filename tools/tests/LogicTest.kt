import com.zlitch.clackwell.*

var failures = 0
var checks = 0
fun check(name: String, got: Any?, want: Any?) { checks++; if (got != want) { failures++; println("FAIL $name: got <$got> want <$want>") } }

fun logic() {
    val s = ShiftState()
    s.press(1000); s.release(); check("shift on", s.mode, ShiftMode.ON)
    s.onCharacterTyped(); check("one capital", s.mode, ShiftMode.OFF)
    s.press(3000); s.release(); s.press(3150); s.release(); check("double tap caps", s.mode, ShiftMode.CAPS)
    s.onCharacterTyped(); s.setAuto(false); check("caps sticks", s.mode, ShiftMode.CAPS)
    s.press(6000); s.release(); check("caps off", s.mode, ShiftMode.OFF)
    s.press(9000); s.release(); s.press(10000); s.release(); check("slow taps toggle", s.mode, ShiftMode.OFF)
    s.reset(); s.press(100); s.onCharacterTyped(); s.onCharacterTyped(); check("held keeps shift", s.mode, ShiftMode.ON); s.release(); check("chord release", s.mode, ShiftMode.OFF)
    s.reset(); s.setAuto(true); check("auto caps", s.mode, ShiftMode.ON); s.onCharacterTyped(); check("auto consumed", s.mode, ShiftMode.OFF)
    check("del ascii", TextRules.lastClusterLength("abc"), 1)
    check("del emoji", TextRules.lastClusterLength("a😀"), 2)
    check("del skin tone", TextRules.lastClusterLength("a👍🏽"), 4)
    check("del family", TextRules.lastClusterLength("a👨‍👩‍👧"), 8)
    check("del one flag of two", TextRules.lastClusterLength("🇮🇳🇮🇳"), 4)
    check("del heart VS16", TextRules.lastClusterLength("a❤️"), 2)
    check("del combining", TextRules.lastClusterLength("aé"), 2)
    check("del empty", TextRules.lastClusterLength(""), 0)
    check("del word", TextRules.lastWordLength("hello world  "), 7)
    check("case up", TextRules.cycleCase("hi there"), "HI THERE")
    check("case title", TextRules.cycleCase("HI THERE"), "Hi There")
    check("case low", TextRules.cycleCase("Hi There"), "hi there")
    check("case single letter", TextRules.cycleCase("A"), "a")
    check("dbl space", TextRules.doubleSpaceMakesPeriod("d "), true)
    check("no dbl after dot", TextRules.doubleSpaceMakesPeriod(". "), false)
    check("enter search", EnterRules.kind(3), EnterKind.ACTION); check("label", EnterRules.label(4), "SEND")
    check("enter multiline", EnterRules.kind(4 or 0x40000000), EnterKind.NEWLINE); check("enter none", EnterRules.kind(1), EnterKind.NEWLINE)
    check("repeat speeds up", DeleteRepeat.delayAfter(1) > DeleteRepeat.delayAfter(10), true)
    val k = KeySpring(); k.press(); check("moves at once", k.position >= 0.7f, true)
    repeat(30) { k.step(0.016f) }; check("down", Math.abs(k.position - 1f) < 0.01f, true)
    k.release(); var low = 1f; var frames = 0; while (k.step(0.016f) && frames < 200) { frames++; low = Math.min(low, k.position) }
    println("spring: release settles in ${frames * 16} ms, overshoot ${-low}")
    check("settles", k.position, 0f); check("subtle overshoot", low > -0.15f, true)
    check("bar rests before and after", StrikeMotion.at(0) + StrikeMotion.at(StrikeMotion.LENGTH_MS), 0f)
    check("bar fully thrown at the hit", StrikeMotion.at(StrikeMotion.ATTACK_MS) > 0.99f, true)
    check("bar rebounds slightly", (30L..200L).fold(0f) { low, t -> Math.min(low, StrikeMotion.at(t)) } in -0.2f..-0.01f, true)
    val quick = KeySpring(); quick.press(); quick.release(); quick.step(0.016f)
    check("quick tap still visibly goes down", quick.position > 0.7f, true)
    var n = 0; while (quick.step(0.016f) && n < 100) n++
    check("quick tap comes back up", quick.position, 0f)
    val v = SoundVariator(java.util.Random(7)); var prev = -1; var repeats = 0
    repeat(2000) { val n = v.take("k", 6); if (n == prev) repeats++; prev = n }
    check("no back-to-back takes", repeats, 0)
    check("rate bounds", (0 until 2000).all { v.rate(1f) in 0.954f..1.046f }, true)
    for (lang in Language.values()) for (page in Page.values()) for (row in Layouts.build(page, lang))
        check("row width $lang $page", row.fold(0f) { a, d -> a + d.weight } <= 10.01f, true)
    val all = (Layouts.build(Page.SYMBOLS_1, Language.ENGLISH) + Layouts.build(Page.SYMBOLS_2, Language.ENGLISH)).flatten().map { it.label }
    check("digits", "0123456789".all { it.toString() in all }, true)
}

const val DICT = "app/src/main/assets/dict/"

fun suggestions() {
    fun load(code: String) = Suggester(java.io.File(DICT + code + ".txt").readLines().filter { it.isNotEmpty() }, code == "en")
    val t0 = System.nanoTime(); val en = load("en"); println("english list loads in ${(System.nanoTime() - t0) / 1000000} ms (desktop)")
    for (typed in listOf("bars", "hel", "teh", "recieve", "dont", "i", "Hel", "HEL", "keybaord", "t", "xqzv"))
        println("  $typed -> ${en.suggest(typed).words}")
    check("literal kept", en.suggest("bars").literal, "bars")
    check("completes", en.suggest("keyboar").words.contains("keyboard"), true)
    check("fixes swap", en.suggest("teh").words.first(), "the")
    check("fixes ie", en.suggest("recieve").words.first(), "receive")
    check("fixes typo", en.suggest("keybaord").words.contains("keyboard"), true)
    check("contraction", en.suggest("dont").words.first(), "don't")
    check("capital I", en.suggest("i").words.first(), "I")
    check("title case", en.suggest("Hel").words.all { it[0] == 'H' }, true)
    check("upper case", en.suggest("HEL").words.all { it == TextRules.upper(it) }, true)
    check("empty", en.suggest("").words.size, 0)
    check("gibberish", en.suggest("xqzvwk").words.size, 0)
    check("max two", en.suggest("a").words.size <= 2, true)
    check("no offensive offers", listOf("fuc", "shi", "bitc").none { p -> en.suggest(p).words.any { it.startsWith("fuck") || it.startsWith("shit") || it.startsWith("bitch") } }, true)
    for ((code, typed) in listOf("fr" to "bonjou", "de" to "nich", "es" to "graci")) check("$code list works", load(code).suggest(typed).words.isNotEmpty(), true)
    var worst = 0L
    for (typed in listOf("a", "s", "co", "internationalizatio", "zzzzzzzzzzzzzzzz", "recieve")) repeat(20) {
        val start = System.nanoTime(); en.suggest(typed); if (it > 5) worst = Math.max(worst, System.nanoTime() - start) }
    println("slowest lookup ${worst / 1000000.0} ms (desktop)"); check("lookup fast", worst < 40_000_000L, true)
    check("current word", Suggester.currentWord("hello wor"), "wor")
    check("apostrophe word", Suggester.currentWord("I don'"), "don'")
    check("after space", Suggester.currentWord("hello "), "")
    check("email ignored", Suggester.currentWord("me@gmai"), "")
    check("digits ignored", Suggester.currentWord("abc123def"), "")
    check("after punctuation", Suggester.currentWord("ok.Th"), "Th")
}
fun main() { logic(); suggestions(); println("$checks checks, $failures failures"); if (failures > 0) kotlin.system.exitProcess(1) }
