package com.lizafy.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import org.json.JSONObject

data class IconApp(val id: String, val pkg: String, val label: String)

/** Apps installed on this phone that have a doodle icon in the manifest. */
fun installedIconApps(ctx: Context): List<IconApp> {
    val arr = JSONObject(ctx.assets.open("icon_manifest.json").bufferedReader().use { it.readText() }).getJSONArray("icons")
    val pm = ctx.packageManager
    val out = mutableListOf<IconApp>()
    for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        val cat = o.getString("category")
        if (cat == "folder" || cat == "fallback") continue
        val pk = o.getJSONArray("packages")
        var found: String? = null
        for (j in 0 until pk.length()) {
            val p = pk.getString(j)
            if (pm.getLaunchIntentForPackage(p) != null) { found = p; break }
        }
        val pkg = found ?: continue
        if (out.any { it.pkg == pkg }) continue
        val label = try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            o.getString("label")
        }
        out.add(IconApp(o.getString("id"), pkg, label))
    }
    return out
}

/** Asks the launcher to pin a doodle-icon shortcut that opens the real app. The user confirms in a system dialog. */
fun pinIcon(ctx: Context, app: IconApp, theme: String): Boolean = try {
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) false else {
        val intent = ctx.packageManager.getLaunchIntentForPackage(app.pkg)!!
        val bmp = ctx.assets.open("icons/$theme/${app.id}.webp").use { BitmapFactory.decodeStream(it) }
        val icon = IconCompat.createWithBitmap(Bitmap.createScaledBitmap(bmp, 192, 192, true))
        val info = ShortcutInfoCompat.Builder(ctx, "lizafy_${app.pkg}")
            .setShortLabel(app.label).setIcon(icon).setIntent(intent).build()
        ShortcutManagerCompat.requestPinShortcut(ctx, info, null)
    }
} catch (e: Exception) {
    false
}
