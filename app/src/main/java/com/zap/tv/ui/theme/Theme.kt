package com.zap.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = ZapGold,
    secondary = ZapGold,
    background = ZapDarkBackground,
    surface = ZapSurfaceCard,
    onPrimary = ZapDarkBackground,
    onBackground = ZapTextPrimary,
    onSurface = ZapTextPrimary
)

@Composable
fun ZapTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = ZapTypography,
        content = content
    )
}
