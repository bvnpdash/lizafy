package com.lizafy.studio

import android.app.WallpaperManager
import android.content.ContentValues
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LizafyTheme { LizafyApp() } }
    }
}

// ---------- theme ----------
private val Cocoa = Color(0xFF6C5145)
private val Cream = Color(0xFFFFF5E8)
private val Ivory = Color(0xFFFFF9F0)
private val Butter = Color(0xFFF6D98B)
private val Sage = Color(0xFFB8CBAA)
private val Charcoal = Color(0xFF35312F)

@Composable
fun LizafyTheme(darkOverride: Boolean? = null, content: @Composable () -> Unit) {
    val scheme = if (darkOverride ?: isSystemInDarkTheme()) {
        darkColorScheme(
            primary = Color(0xFFF3E6D3), onPrimary = Color(0xFF0C0B0A),
            background = Color(0xFF0C0B0A), surface = Color(0xFF161312),
            onBackground = Color(0xFFF3E6D3), onSurface = Color(0xFFF3E6D3),
            primaryContainer = Color(0xFF8F7D4A), onPrimaryContainer = Color(0xFFF3E6D3),
            secondaryContainer = Color(0xFF2A2522), onSecondaryContainer = Color(0xFFF3E6D3),
            surfaceVariant = Color(0xFF2A2522), onSurfaceVariant = Color(0xFFD8C8B4),
        )
    } else {
        lightColorScheme(
            primary = Cocoa, onPrimary = Cream,
            background = Cream, surface = Ivory,
            onBackground = Charcoal, onSurface = Charcoal,
            primaryContainer = Butter, onPrimaryContainer = Charcoal,
            secondaryContainer = Sage, onSecondaryContainer = Charcoal,
            surfaceVariant = Color(0xFFF1E4D2), onSurfaceVariant = Cocoa,
        )
    }
    MaterialTheme(colorScheme = scheme, typography = fontedTypography(), content = content)
}

// ---------- asset + system helpers ----------
fun Context.assetList(dir: String): List<String> = assets.list(dir)?.sorted() ?: emptyList()

fun pretty(file: String): String {
    val raw = file.substringBeforeLast('.')
    val special = mapOf("chatgpt" to "ChatGPT", "whatsapp" to "WhatsApp", "youtube" to "YouTube", "playstore" to "Play Store", "food" to "Food delivery")
    return special[raw] ?: raw.replace("pct", "%").replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun decode(ctx: Context, path: String, sample: Int): ImageBitmap? = try {
    val o = BitmapFactory.Options().apply { inSampleSize = sample }
    ctx.assets.open(path).use { BitmapFactory.decodeStream(it, null, o) }?.asImageBitmap()
} catch (e: Exception) {
    null
}

@Composable
fun AssetImage(
    path: String,
    modifier: Modifier = Modifier,
    sample: Int = 1,
    scale: ContentScale = ContentScale.Fit,
) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, path, sample) {
        value = withContext(Dispatchers.IO) { decode(ctx, path, sample) }
    }
    val b = bmp
    if (b != null) Image(b, null, modifier, contentScale = scale) else Box(modifier)
}

fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

fun saveToPictures(ctx: Context, path: String): Boolean = try {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, path.replace('/', '_'))
        put(MediaStore.Images.Media.MIME_TYPE, "image/webp")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Lizafy")
    }
    val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
    ctx.contentResolver.openOutputStream(uri)!!.use { out -> ctx.assets.open(path).use { it.copyTo(out) } }
    true
} catch (e: Exception) {
    false
}

fun setWallpaper(ctx: Context, path: String, flags: Int): Boolean = try {
    val wm = WallpaperManager.getInstance(ctx)
    ctx.assets.open(path).use { wm.setStream(it, null, true, flags) }
    true
} catch (e: Exception) {
    false
}

const val HOME_FLAG = WallpaperManager.FLAG_SYSTEM
const val LOCK_FLAG = WallpaperManager.FLAG_LOCK

fun runSet(ctx: Context, scope: CoroutineScope, path: String, flags: Int, label: String) {
    scope.launch {
        val ok = withContext(Dispatchers.IO) { setWallpaper(ctx, path, flags) }
        toast(ctx, if (ok) "$label set" else "Could not set $label")
    }
}

fun runSave(ctx: Context, scope: CoroutineScope, paths: List<String>) {
    scope.launch {
        val ok = withContext(Dispatchers.IO) { paths.count { saveToPictures(ctx, it) } }
        toast(ctx, if (ok == paths.size) "Saved $ok to Pictures/Lizafy" else "Saved $ok of ${paths.size}")
    }
}

// ---------- shell ----------
enum class Tab(val label: String, val icon: String) {
    Home("Home", "fallback"), Icons("Icons", "calendar"), Walls("Wallpapers", "gallery"),
    Cards("Flip Cards", "camera"), Moods("Moods", "weather"), Device("Device", "settings")
}

@Composable
fun LizafyApp() {
    var tab by remember { mutableStateOf(Tab.Home) }
    val th = if (isSystemInDarkTheme()) "dark" else "light"
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.values().forEach { t ->
                    NavigationBarItem(
                        selected = tab == t, onClick = { tab = t },
                        icon = { AssetImage("icons/$th/${t.icon}.webp", Modifier.size(30.dp), sample = 2) },
                        label = { Text(t.label, fontSize = 10.sp, maxLines = 1) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (tab) {
                Tab.Home -> HomeScreen()
                Tab.Icons -> IconsScreen()
                Tab.Walls -> WallpapersScreen()
                Tab.Cards -> FlipCardsScreen()
                Tab.Moods -> MoodsScreen()
                Tab.Device -> DeviceScreen()
            }
        }
    }
}

@Composable
fun Heading(text: String) =
    Text(text, fontSize = 28.sp, fontFamily = Fonts.heading, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)

// ---------- Home: looks ----------
data class Look(val name: String, val home: String, val lock: String)

val LOOKS = listOf(
    Look("Minimal Cream", "wallpapers/home/minimal_cream.webp", "wallpapers/home/minimal_cream.webp"),
    Look("Cozy Garden", "wallpapers/home/cozy_garden.webp", "wallpapers/lock/cozy_garden.webp"),
    Look("Little Cloud World", "wallpapers/home/little_cloud_world.webp", "wallpapers/lock/little_cloud.webp"),
    Look("Cat Notebook", "wallpapers/home/cat_notebook.webp", "wallpapers/lock/cat_notebook.webp"),
    Look("Midnight Doodles", "wallpapers/home/midnight_doodles.webp", "wallpapers/lock/midnight_doodles.webp"),
)

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Heading("Lizafy")
                Text("Your Vivo. Your Little Illustrated World.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { Text("Looks", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
        items(LOOKS.size) { i ->
            val look = LOOKS[i]
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AssetImage(look.home, Modifier.width(64.dp).heightIn(max = 140.dp).clip(RoundedCornerShape(10.dp)), sample = 4)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(look.name, fontWeight = FontWeight.SemiBold)
                        Text("Sets wallpapers and saves matching Flip Cards", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = {
                        runSet(ctx, scope, look.home, HOME_FLAG, "Home wallpaper")
                        runSet(ctx, scope, look.lock, LOCK_FLAG, "Lock wallpaper")
                        val cardDir = "flipcards/" + if (look.name == "Midnight Doodles") "dark" else "light"
                        runSave(ctx, scope, ctx.assetList(cardDir).map { "$cardDir/$it" })
                    }) { Text("Apply") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Finish setup", fontWeight = FontWeight.SemiBold)
                    Text("After Apply: add the doodle icons in the Icons tab, then switch dark mode to match the look.", fontSize = 13.sp)
                    OutlinedButton(onClick = {
                        ctx.startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text("Open display settings") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("What this app can and cannot do", fontWeight = FontWeight.SemiBold)
                    Text("It sets wallpapers directly. Flip Cards and icons are saved to Pictures/Lizafy for you to pick in the system settings. It does not change app icons, the status bar or the Origin Island. See the Device tab.", fontSize = 13.sp)
                }
            }
        }
    }
}

// ---------- Icons ----------
@Composable
fun IconsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dark by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }
    val apps = remember { installedIconApps(ctx) }
    var next by remember { mutableStateOf(0) }
    val th = if (dark) "dark" else "light"
    val files = remember(th) { ctx.assetList("icons/$th") }.filter { it.contains(q.trim().lowercase().replace(' ', '_')) }
    LazyVerticalGrid(
        GridCells.Adaptive(88.dp), Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Heading("Icons")
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Put doodle icons on your home screen", fontWeight = FontWeight.SemiBold)
                        Text("${apps.size} of your installed apps have a doodle icon. Android asks you to confirm each one. Your original icons stay until you remove them.", fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(enabled = next < apps.size, onClick = {
                                val ok = pinIcon(ctx, apps[next], th)
                                if (ok) next++ else toast(ctx, "Your launcher did not accept this shortcut")
                            }) { Text(if (next < apps.size) "Add next: ${apps[next].label} (${apps.size - next} left)" else "All added", maxLines = 1) }
                            TextButton(onClick = { next = 0 }) { Text("Start over") }
                        }
                        OutlinedButton(onClick = {
                            val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                            val list = ctx.packageManager.queryIntentActivities(q, 0).joinToString("\n") {
                                it.activityInfo.packageName + "/" + it.activityInfo.name + " | " + it.loadLabel(ctx.packageManager)
                            }
                            (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                .setPrimaryClip(ClipData.newPlainText("Lizafy apps", list))
                            toast(ctx, "App list copied")
                        }) { Text("Copy my app list (for icon pack)", fontSize = 12.sp) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !dark, onClick = { dark = false }, label = { Text("Light") })
                    FilterChip(selected = dark, onClick = { dark = true }, label = { Text("Dark") })
                }
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search icons") })
                Text("${files.size} icons. Tap one to save it.", fontSize = 12.sp)
            }
        }
        items(files) { f ->
            Column(
                Modifier.clip(RoundedCornerShape(12.dp)).clickable { open = "icons/$th/$f" }.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AssetImage("icons/$th/$f", Modifier.size(72.dp), sample = 2)
                Text(pretty(f), fontSize = 11.sp, maxLines = 1)
            }
        }
    }
    open?.let { PreviewDialog(it, wallpaper = false, onDismiss = { open = null }) }
}

// ---------- Wallpapers ----------
val WALL_SETS = listOf(
    "Home" to "wallpapers/home", "Lock" to "wallpapers/lock",
    "Charging light" to "wallpapers/charging_light", "Charging dark" to "wallpapers/charging_dark",
)

@Composable
fun WallpapersScreen() {
    val ctx = LocalContext.current
    var set by remember { mutableStateOf(0) }
    var open by remember { mutableStateOf<String?>(null) }
    val dir = WALL_SETS[set].second
    val files = remember(dir) { ctx.assetList(dir) }
    LazyVerticalGrid(
        GridCells.Fixed(2), Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Heading("Wallpapers")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WALL_SETS.forEachIndexed { i, s ->
                        FilterChip(selected = set == i, onClick = { set = i }, label = { Text(s.first, fontSize = 11.sp, maxLines = 1) })
                    }
                }
            }
        }
        items(files) { f ->
            Column(Modifier.clickable { open = "$dir/$f" }) {
                AssetImage("$dir/$f", Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)), sample = 4)
                Text(pretty(f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
    open?.let { PreviewDialog(it, wallpaper = true, onDismiss = { open = null }) }
}

// ---------- Flip Cards ----------
@Composable
fun FlipCardsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var dark by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf<String?>(null) }
    val dir = "flipcards/" + if (dark) "dark" else "light"
    val files = remember(dir) { ctx.assetList(dir) }
    LazyVerticalGrid(
        GridCells.Fixed(2), Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Heading("Flip Cards")
                Text("Save the cards, then use them where your phone allows. Vivo OriginOS has a Flip Cards setting (Themes). On other phones, use them as lock screen or always-on display pictures.", fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !dark, onClick = { dark = false }, label = { Text("Light") })
                    FilterChip(selected = dark, onClick = { dark = true }, label = { Text("Dark") })
                }
                OutlinedButton(onClick = { runSave(ctx, scope, files.map { "$dir/$it" }) }) { Text("Save all ${files.size} to Pictures/Lizafy") }
            }
        }
        items(files) { f ->
            Column(Modifier.clickable { open = "$dir/$f" }) {
                AssetImage("$dir/$f", Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)), sample = 2)
                Text(pretty(f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
    open?.let { PreviewDialog(it, wallpaper = false, onDismiss = { open = null }) }
}

// ---------- Moods: live battery + weather art ----------
fun batteryInfo(ctx: Context): Pair<Int, Boolean> {
    val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val pct = if (level < 0 || scale <= 0) 0 else level * 100 / scale
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    return Pair(pct, charging)
}

fun moodFor(p: Int): String = when {
    p <= 15 -> "sleepy_cloud"
    p <= 40 -> "sprout_flower"
    p <= 75 -> "happy_sun"
    p < 100 -> "excited_blob"
    else -> "full_bloom"
}

@Composable
fun MoodsScreen() {
    val ctx = LocalContext.current
    val th = if (isSystemInDarkTheme()) "dark" else "light"
    var info by remember { mutableStateOf(batteryInfo(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            info = batteryInfo(ctx)
            delay(5000)
        }
    }
    val states = remember(th) { ctx.assetList("weather/$th") }
    LazyVerticalGrid(
        GridCells.Adaptive(96.dp), Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Heading("Moods")
                AssetImage("charging/$th/${moodFor(info.first)}.webp", Modifier.size(180.dp))
                Text("${info.first}%" + if (info.second) " · charging" else "", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(pretty(moodFor(info.first) + ".webp"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { Text("Weather art", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
        items(states) { f ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AssetImage("weather/$th/$f", Modifier.size(84.dp), sample = 2)
                Text(pretty(f), fontSize = 11.sp)
            }
        }
    }
}

// ---------- Device compatibility ----------
@Composable
fun DeviceScreen() {
    val ctx = LocalContext.current
    val wm = WallpaperManager.getInstance(ctx)
    val dm = ctx.resources.displayMetrics
    val isVivo = Build.MANUFACTURER.equals("vivo", true) || Build.BRAND.equals("vivo", true)
    val rows = listOf(
        "Manufacturer" to Build.MANUFACTURER, "Model" to Build.MODEL,
        "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "System build" to Build.DISPLAY,
        "Screen" to "${dm.widthPixels} x ${dm.heightPixels} px, ${dm.densityDpi} dpi",
        "Vivo device" to if (isVivo) "Yes" else "No",
    )
    val live = listOf(
        "Set wallpaper" to (wm.isWallpaperSupported && wm.isSetWallpaperAllowed),
        "Save images to Pictures" to true,
    )
    val partial = listOf(
        "Flip Cards: save images here, then import in system settings",
        "Lock screen styles: wallpaper only, clock and widgets come from the system",
    )
    val unsupported = listOf(
        "Icon packs on the native launcher (no public method found for global OriginOS 6)",
        "Status bar, notification shade, quick settings, navigation bar",
        "Origin Island and the charging screen",
        "Always-on display styling",
        "Keyboard theme (needs a separate keyboard app, not included)",
    )
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Heading("Device") }
        items(rows.size) { i ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(rows[i].first, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(rows[i].second, fontWeight = FontWeight.SemiBold)
            }
        }
        item { Text("Supported (checked on this phone just now)", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)) }
        items(live.size) { i -> Text((if (live[i].second) "Yes: " else "No: ") + live[i].first) }
        item { Text("Partially supported (manual steps, from public documentation)", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)) }
        items(partial.size) { i -> Text(partial[i]) }
        item { Text("Not supported through this app", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp)) }
        items(unsupported.size) { i -> Text(unsupported[i]) }
    }
}

// ---------- preview dialog ----------
@Composable
fun PreviewDialog(path: String, wallpaper: Boolean, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AssetImage(path, Modifier.fillMaxWidth().heightIn(max = 400.dp).clip(RoundedCornerShape(14.dp)), sample = if (wallpaper) 2 else 1)
                Text(pretty(path.substringAfterLast('/')), fontWeight = FontWeight.Bold)
                if (wallpaper) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = { runSet(ctx, scope, path, HOME_FLAG, "Home wallpaper") }, modifier = Modifier.weight(1f)) { Text("Home", fontSize = 12.sp) }
                        Button(onClick = { runSet(ctx, scope, path, LOCK_FLAG, "Lock wallpaper") }, modifier = Modifier.weight(1f)) { Text("Lock", fontSize = 12.sp) }
                        Button(onClick = { runSet(ctx, scope, path, HOME_FLAG or LOCK_FLAG, "Wallpaper") }, modifier = Modifier.weight(1f)) { Text("Both", fontSize = 12.sp) }
                    }
                }
                OutlinedButton(onClick = { runSave(ctx, scope, listOf(path)) }, modifier = Modifier.fillMaxWidth()) { Text("Save to Pictures/Lizafy") }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Close") }
            }
        }
    }
}
