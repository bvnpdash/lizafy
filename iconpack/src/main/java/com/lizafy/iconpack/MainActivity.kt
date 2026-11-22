package com.lizafy.iconpack

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val t = TextView(this)
        t.text = "Lizafy icon pack\n\nOpen your launcher settings (in Nova: Settings, Look and feel, Icon style) and choose this pack."
        t.textSize = 18f
        t.setPadding(48, 96, 48, 48)
        setContentView(t)
    }
}
