package com.lizafy.studio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class LauncherActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)
    private var drawer by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LizafyTheme { HomeRoot(tick, drawer) { drawer = it } } }
    }

    override fun onResume() {
        super.onResume()
        tick++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        drawer = false
    }
}

data class LApp(val label: String, val pkg: String, val comp: ComponentName, val doodle: String?, val icon: ImageBitmap?)

private fun doodleMap(ctx: Context): Map<String, String> {
    val m = mutableMapOf("com.lizafy.studio" to "fallback")
    val arr = JSONObject(ctx.assets.open("icon_manifest.json").bufferedReader().use { it.readText() }).getJSONArray("icons")
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        val cat = o.getString("category")
        if (cat == "folder" || cat == "fallback") continue
        val pk = o.getJSONArray("packages")
        for (j in 0 until pk.length()) m.putIfAbsent(pk.getString(j), o.getString("id"))
    }
    return m
}

private fun loadApps(ctx: Context, map: Map<String, String>): List<LApp> {
    val pm = ctx.packageManager
    val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(q, 0).map { ri ->
        val pkg = ri.activityInfo.packageName
        val id = map[pkg]
        val icon = if (id == null) try { ri.loadIcon(pm).toBitmap(128, 128).asImageBitmap() } catch (e: Exception) { null } else null
        LApp(ri.loadLabel(pm).toString(), pkg, ComponentName(pkg, ri.activityInfo.name), id, icon)
    }.sortedBy { it.label.lowercase() }
}

private fun launch(ctx: Context, app: LApp) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(app.comp).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        toast(ctx, "Could not open ${app.label}")
    }
}

private val DOCK_IDS = listOf("phone", "messages", "camera", "gallery")

@Composable
fun HomeRoot(tick: Int, drawerOpen: Boolean, setDrawer: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val th = if (isSystemInDarkTheme()) "dark" else "light"
    val map = remember { doodleMap(ctx) }
    val apps by produceState(emptyList<LApp>(), tick) { value = withContext(Dispatchers.IO) { loadApps(ctx, map) } }
    val prefs = remember { ctx.getSharedPreferences("lizafy", Context.MODE_PRIVATE) }
    var pins by remember { mutableStateOf(prefs.getString("pins", null)?.split("|")?.filter { it.isNotEmpty() }) }
    var name by remember { mutableStateOf(prefs.getString("name", "") ?: "") }
    var menu by remember { mutableStateOf<LApp?>(null) }
    var editName by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(15000)
        }
    }
    val dock = DOCK_IDS.mapNotNull { id -> apps.firstOrNull { it.doodle == id } }
    val pinnedPkgs = pins ?: apps.filter { it.doodle != null && it.doodle !in DOCK_IDS && it.doodle != "fallback" }.take(12).map { it.pkg }
    val pinned = pinnedPkgs.mapNotNull { p -> apps.firstOrNull { it.pkg == p } }
    fun savePins(l: List<String>) {
        pins = l
        prefs.edit().putString("pins", l.joinToString("|")).apply()
    }

    val panel = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)
    val hour = now.hour
    val hello = when {
        hour < 5 -> "Good night"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        hour < 21 -> "Good evening"
        else -> "Good night"
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)
                .pointerInput(Unit) {
                    var acc = 0f
                    detectVerticalDragGestures(
                        onDragStart = { acc = 0f },
                        onDragEnd = { if (acc < -120f) setDrawer(true) },
                        onVerticalDrag = { _, dy -> acc += dy },
                    )
                },
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(shape = RoundedCornerShape(28.dp), color = panel, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (name.isBlank()) hello else "$hello, $name", fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.combinedClickableLong { editName = true },
                    )
                    Text(now.format(DateTimeFormatter.ofPattern("h:mm")), fontSize = 64.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), fontSize = 16.sp)
                }
            }
            Surface(shape = RoundedCornerShape(28.dp), color = panel, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (pinned.isEmpty()) {
                        Text("Swipe up for all apps. Long-press an app to pin it here.", modifier = Modifier.padding(12.dp), textAlign = TextAlign.Center)
                    }
                    pinned.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { a ->
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    AppIcon(a, th, true, { launch(ctx, a) }, { menu = a })
                                }
                            }
                            repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { setDrawer(true) }) { Text("All apps", color = MaterialTheme.colorScheme.primary) }
                    Surface(shape = RoundedCornerShape(30.dp), color = panel, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            dock.forEach { a -> AppIcon(a, th, false, { launch(ctx, a) }, { menu = a }) }
                        }
                    }
                }
            }
        }

        if (drawerOpen) {
            BackHandler { setDrawer(false) }
            var q by remember { mutableStateOf("") }
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f), modifier = Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp)) {
                    OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp), singleLine = true, label = { Text("Search apps") })
                    LazyVerticalGrid(GridCells.Fixed(4), contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(apps.filter { it.label.contains(q.trim(), true) }) { a ->
                            AppIcon(a, th, true, { launch(ctx, a); setDrawer(false) }, { menu = a })
                        }
                    }
                }
            }
        }
    }

    menu?.let { a ->
        val isPinned = a.pkg in pinnedPkgs
        AlertDialog(
            onDismissRequest = { menu = null },
            title = { Text(a.label) },
            confirmButton = {
                TextButton(onClick = {
                    savePins(if (isPinned) pinnedPkgs - a.pkg else pinnedPkgs + a.pkg)
                    menu = null
                }) { Text(if (isPinned) "Remove from home" else "Pin to home") }
            },
            dismissButton = {
                TextButton(onClick = {
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${a.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    menu = null
                }) { Text("App info") }
            },
        )
    }
    if (editName) {
        var draft by remember { mutableStateOf(name) }
        AlertDialog(
            onDismissRequest = { editName = false },
            title = { Text("Your name") },
            text = { OutlinedTextField(draft, { draft = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    name = draft.trim()
                    prefs.edit().putString("name", name).apply()
                    editName = false
                }) { Text("Save") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableLong(onLong: () -> Unit): Modifier = this.combinedClickable(onClick = {}, onLongClick = onLong)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(app: LApp, th: String, showLabel: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    Column(
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLong).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (app.doodle != null) {
            AssetImage("icons/$th/${app.doodle}.webp", Modifier.size(58.dp), sample = 2)
        } else {
            Box(
                Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                app.icon?.let { Image(it, null, Modifier.size(40.dp)) }
            }
        }
        if (showLabel) Text(app.label, fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 2.dp))
    }
}
