package com.zap.tv.presentation.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zap.tv.ui.theme.*
import kotlinx.coroutines.delay

enum class AspectRatioMode(val label: String, val desc: String) {
    FILL("Preencher Tela", "Ajuste total sem bordas pretas"),
    FIT("Original (Fit)", "Mantém a resolução original da fonte"),
    FOUR_THREE("4:3 Clássico", "Ideal para canais antigos sem esticar rostos"),
    ZOOM("Zoom 16:9", "Corta levemente topo/base para preenchimento")
}

@Composable
fun QuickOptionsMenu(
    visible: Boolean,
    currentAspectRatio: AspectRatioMode,
    onSelectAspectRatio: (AspectRatioMode) -> Unit,
    onClose: () -> Unit
) {
    val initialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(visible) {
        if (visible) {
            delay(150)
            try {
                initialFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it / 3 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it / 3 }) + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            Column(
                modifier = Modifier
                    .width(360.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xF514141C))
                    .border(1.5.dp, Color(0xFF383848), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "AJUSTES RÁPIDOS",
                        color = ZapYellowKey,
                        fontFamily = MontserratFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "🟡 Fechar",
                        color = ZapTextMuted,
                        fontFamily = MontserratFontFamily,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "FORMATO DE TELA",
                    color = ZapTextSecondary,
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))

                AspectRatioMode.values().forEach { mode ->
                    AspectRatioItem(
                        mode = mode,
                        isSelected = mode == currentAspectRatio,
                        initialFocusRequester = initialFocusRequester,
                        onSelect = { onSelectAspectRatio(mode) }
                    )
                }

                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
fun AspectRatioItem(
    mode: AspectRatioMode,
    isSelected: Boolean,
    initialFocusRequester: FocusRequester,
    onSelect: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFocused) ZapYellowKey
                else if (isSelected) Color(0xFF3C350A)
                else Color(0xFF1E1E28)
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickableNoRipple { onSelect() }
            .then(if (isSelected) Modifier.focusRequester(initialFocusRequester) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = mode.label,
                color = if (isFocused) Color.Black else ZapTextPrimary,
                fontFamily = MontserratFontFamily,
                fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Text(
                text = mode.desc,
                color = if (isFocused) Color(0xFF222222) else if (isSelected) Color(0xFFE0E0E0) else ZapTextMuted,
                fontFamily = MontserratFontFamily,
                fontSize = 10.sp
            )
        }
        if (isSelected) {
            Text(
                text = "✓",
                color = if (isFocused) Color.Black else ZapYellowKey,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
)
