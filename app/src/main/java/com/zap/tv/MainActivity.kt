package com.zap.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.widget.TextView
import com.zap.tv.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
        } catch (e: Exception) {
            showFallback("super.onCreate falhou: ${e::class.java.simpleName}: ${e.message}\n${e.stackTrace.take(8).joinToString("\n")}")
            return
        }
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, window.decorView).apply {
                hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } catch (_: Exception) { }
        try {
            setContent {
                com.zap.tv.ui.theme.ZapTvTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                        AppNavHost()
                    }
                }
            }
        } catch (e: Exception) {
            showFallback("setContent falhou: ${e::class.java.simpleName}: ${e.message}\n${e.stackTrace.take(10).joinToString("\n")}")
        }
    }
    private fun showFallback(msg: String) {
        try {
            val tv = TextView(this).apply {
                text = "ZAP-TV — Erro ao iniciar\n\n$msg\n\nFotografe e reinstale."
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0xFF000000.toInt())
                textSize = 13f
                setPadding(32,32,32,32)
            }
            setContentView(tv)
        } catch (_: Exception) {}
    }
}
