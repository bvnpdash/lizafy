package com.zlitch.clackwell

import android.content.Context
import android.graphics.Typeface

/**
 * The serif ships inside the app. Some phones swap the system "serif" for the user's chosen
 * sans font, which loses the typewriter lettering.
 */
object Fonts {
    private var upright: Typeface? = null
    private var slanted: Typeface? = null
    private var color: Typeface? = null

    fun serif(context: Context): Typeface =
        upright ?: Typeface.createFromAsset(context.assets, "fonts/NotoSerif-Regular.ttf").also { upright = it }

    fun italic(context: Context): Typeface =
        slanted ?: Typeface.createFromAsset(context.assets, "fonts/NotoSerif-Italic.ttf").also { slanted = it }

    /** Bundled color emoji, so the picker looks the same on every phone and has every emoji. */
    fun emoji(context: Context): Typeface =
        color ?: Typeface.createFromAsset(context.assets, "fonts/NotoColorEmoji.ttf").also { color = it }
}
