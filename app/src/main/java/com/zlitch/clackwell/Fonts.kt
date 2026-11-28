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

    fun serif(context: Context): Typeface =
        upright ?: Typeface.createFromAsset(context.assets, "fonts/NotoSerif-Regular.ttf").also { upright = it }

    fun italic(context: Context): Typeface =
        slanted ?: Typeface.createFromAsset(context.assets, "fonts/NotoSerif-Italic.ttf").also { slanted = it }

    /** Emoji use the phone's own emoji font, so a system emoji module (e.g. an iOS emoji Magisk module) shows up here too. */
    fun emoji(@Suppress("UNUSED_PARAMETER") context: Context): Typeface = Typeface.DEFAULT
}
