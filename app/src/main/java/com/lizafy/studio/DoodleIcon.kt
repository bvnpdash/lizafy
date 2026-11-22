package com.lizafy.studio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

object Fonts {
    val heading = FontFamily(Font(R.font.fredoka_regular, FontWeight.Normal), Font(R.font.fredoka_semibold, FontWeight.SemiBold), Font(R.font.fredoka_semibold, FontWeight.Bold))
    val body = FontFamily(Font(R.font.nunito_regular, FontWeight.Normal), Font(R.font.nunito_bold, FontWeight.Bold), Font(R.font.nunito_bold, FontWeight.SemiBold))
    val hand = FontFamily(Font(R.font.patrickhand_regular))
    val deco = FontFamily(Font(R.font.caveat_bold))
}

fun fontedTypography(): Typography {
    val t = Typography()
    return t.copy(
        displayLarge = t.displayLarge.copy(fontFamily = Fonts.heading),
        displayMedium = t.displayMedium.copy(fontFamily = Fonts.heading),
        displaySmall = t.displaySmall.copy(fontFamily = Fonts.heading),
        headlineLarge = t.headlineLarge.copy(fontFamily = Fonts.heading),
        headlineMedium = t.headlineMedium.copy(fontFamily = Fonts.heading),
        headlineSmall = t.headlineSmall.copy(fontFamily = Fonts.heading),
        titleLarge = t.titleLarge.copy(fontFamily = Fonts.heading),
        titleMedium = t.titleMedium.copy(fontFamily = Fonts.heading),
        titleSmall = t.titleSmall.copy(fontFamily = Fonts.heading),
        bodyLarge = t.bodyLarge.copy(fontFamily = Fonts.body),
        bodyMedium = t.bodyMedium.copy(fontFamily = Fonts.body),
        bodySmall = t.bodySmall.copy(fontFamily = Fonts.body),
        labelLarge = t.labelLarge.copy(fontFamily = Fonts.body),
        labelMedium = t.labelMedium.copy(fontFamily = Fonts.body),
        labelSmall = t.labelSmall.copy(fontFamily = Fonts.body),
    )
}

private val GUESS = listOf(
    "camera" to "camera", "gallery" to "gallery", "photos" to "gallery", "album" to "gallery", "dialer" to "phone", "phone" to "phone",
    "contacts" to "contacts", "people" to "contacts", "messages" to "messages", "messaging" to "messages", "sms" to "messages",
    "calendar" to "calendar", "clock" to "clock", "calculator" to "calculator", "files" to "files", "file manager" to "files",
    "browser" to "browser", "internet" to "browser", "download" to "downloads", "music" to "music", "video" to "video",
    "settings" to "settings", "notes" to "notes", "note" to "notes", "weather" to "weather", "play store" to "playstore",
    "maps" to "maps", "gmail" to "gmail", "mail" to "gmail", "game" to "games", "wallet" to "banking", "pay" to "banking",
    "bank" to "banking", "shop" to "shopping", "fitness" to "fitness", "health" to "fitness", "travel" to "travel",
)

/** Picks a drawn glyph from the app name when the package is unknown, so system apps on any phone still get one. */
fun guessId(label: String): String? {
    val l = label.lowercase()
    return GUESS.firstOrNull { l.contains(it.first) }?.second
}

private fun blob(w: Float, seed: Int): Path {
    val rnd = Random(seed)
    val n = 16
    val pts = List(n) { i ->
        val t = 2 * PI * i / n
        val c = cos(t).toFloat(); val s = sin(t).toFloat()
        val r = w * 0.40f
        Offset(
            w / 2 + r * sign(c) * abs(c).pow(0.5f) + (rnd.nextFloat() - .5f) * w * 0.03f,
            w / 2 + r * sign(s) * abs(s).pow(0.5f) + (rnd.nextFloat() - .5f) * w * 0.03f,
        )
    }
    val p = Path()
    p.moveTo(pts[0].x, pts[0].y)
    for (i in 0 until n) {
        val p0 = pts[(i - 1 + n) % n]; val p1 = pts[i]; val p2 = pts[(i + 1) % n]; val p3 = pts[(i + 2) % n]
        p.cubicTo(
            p1.x + (p2.x - p0.x) / 6, p1.y + (p2.y - p0.y) / 6,
            p2.x - (p3.x - p1.x) / 6, p2.y - (p3.y - p1.y) / 6,
            p2.x, p2.y,
        )
    }
    p.close()
    return p
}

/** Hand-drawn pastel blob with the app's first letter, used for every app without a drawn glyph. */
@Composable
fun DoodleLetterIcon(label: String, pkg: String, dark: Boolean, size: Dp = 58.dp) {
    val ink = if (dark) Color(0xFFF3E6D3) else Color(0xFF6C5145)
    val fills = if (dark) listOf(0xFF8F7D4A, 0xFF6B8062, 0xFFA87B6C, 0xFF69869A) else listOf(0xFFF6D98B, 0xFFB8CBAA, 0xFFEFB9A7, 0xFFBFD8E8)
    val seed = pkg.hashCode()
    val fill = Color(fills[abs(seed) % 4])
    val letter = label.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val path = blob(w, seed)
            translate(w * 0.015f, w * 0.015f) { drawPath(path, fill) }
            drawPath(path, ink, style = Stroke(width = w * 0.05f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val sx = w * 0.80f; val sy = w * 0.20f; val k = w * 0.05f
            drawLine(ink, Offset(sx, sy - k), Offset(sx, sy + k), strokeWidth = w * 0.03f, cap = StrokeCap.Round)
            drawLine(ink, Offset(sx - k, sy), Offset(sx + k, sy), strokeWidth = w * 0.03f, cap = StrokeCap.Round)
        }
        Text(letter, fontFamily = Fonts.heading, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.46f).sp, color = ink)
    }
}
