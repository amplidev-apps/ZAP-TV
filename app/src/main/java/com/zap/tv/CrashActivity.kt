package com.zap.tv

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

class CrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val error = intent.getStringExtra("error") ?: "Erro desconhecido"
        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            setPadding(32,32,32,32)
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24,24,24,24)
        }
        val title = TextView(this).apply {
            text = "ZAP-TV — Erro fatal"
            textSize = 22f
            setTextColor(0xFFFF5252.toInt())
        }
        val msg = TextView(this).apply {
            text = error
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0,16,0,16)
        }
        val hint = TextView(this).apply {
            text = "Fotografe esta tela e envie. Depois desinstale e reinstale o app."
            textSize = 13f
            setTextColor(0xFFBDBDBD.toInt())
        }
        layout.addView(title)
        layout.addView(msg)
        layout.addView(hint)
        scroll.addView(layout)
        setContentView(scroll)
    }
}
