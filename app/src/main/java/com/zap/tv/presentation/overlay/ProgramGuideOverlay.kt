package com.zap.tv.presentation.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zap.tv.data.local.entity.ChannelNumberEntity
import com.zap.tv.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ProgramGuideOverlay(
    visible: Boolean,
    channels: List<ChannelNumberEntity>,
    currentChannelNumber: Int,
    onSelectChannel: (Int) -> Unit,
    onClose: () -> Unit
) {
    val listState = rememberLazyListState()
    val initialFocusRequester = remember { FocusRequester() }
    val currentTime = remember {
        val sdf = SimpleDateFormat("HH:mm - EEEE, d 'de' MMMM", Locale("pt", "BR"))
        sdf.format(Date())
    }

    LaunchedEffect(visible) {
        if (visible) {
            val idx = channels.indexOfFirst { it.number == currentChannelNumber }
            if (idx >= 0) {
                listState.scrollToItem(maxOf(0, idx - 2))
            }
            delay(150)
            try {
                initialFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(initialScale = 0.96f) + fadeIn(),
        exit = scaleOut(targetScale = 0.96f) + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF00A0A0E))
                .padding(32.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "GUIA DE PROGRAMAÇÃO",
                            color = ZapGold,
                            fontFamily = MontserratFontFamily,
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "•   canais disponíveis",
                            color = ZapTextSecondary,
                            fontFamily = MontserratFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }

                    Text(
                        text = currentTime,
                        color = ZapTextPrimary,
                        fontFamily = MontserratFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }

                // Guide Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF181822))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CANAL",
                        color = ZapTextMuted,
                        fontFamily = MontserratFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.width(220.dp)
                    )
                    Text(
                        text = "PROGRAMAÇÃO NO AR (AO VIVO)",
                        color = ZapTextMuted,
                        fontFamily = MontserratFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "STATUS",
                        color = ZapTextMuted,
                        fontFamily = MontserratFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        modifier = Modifier.width(90.dp)
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Guide List
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(channels) { index, ch ->
                        val isCurrent = ch.number == currentChannelNumber
                        var isFocused by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isFocused) ZapGold
                                    else if (isCurrent) Color(0xFF282412)
                                    else Color(0xFF14141C)
                                )
                                .border(
                                    width = if (isFocused) 2.dp else if (isCurrent) 1.5.dp else 1.dp,
                                    color = if (isFocused) Color.White else if (isCurrent) ZapGold else Color(0xFF242432),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .onFocusChanged { isFocused = it.isFocused }
                                .focusable()
                                .clickableNoRipple {
                                    onSelectChannel(ch.number)
                                    onClose()
                                }
                                .then(if (isCurrent) Modifier.focusRequester(initialFocusRequester) else Modifier)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Number + Logo + Name
                            Row(
                                modifier = Modifier.width(220.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "",
                                    color = if (isFocused) Color.Black else if (isCurrent) ZapGold else ZapTextSecondary,
                                    fontFamily = MontserratFontFamily,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    modifier = Modifier.width(42.dp)
                                )

                                Box(
                                    modifier = Modifier
                                        .size(width = 40.dp, height = 28.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF20202C)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!ch.logoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ch.logoUrl,
                                            contentDescription = null,
                                            modifier = Modifier.size(width = 34.dp, height = 24.dp)
                                        )
                                    } else {
                                        Text(ch.country, color = Color.White, fontSize = 9.sp)
                                    }
                                }

                                Spacer(Modifier.width(12.dp))

                                Text(
                                    text = ch.channelName,
                                    color = if (isFocused) Color.Black else if (isCurrent) ZapGold else ZapTextPrimary,
                                    fontFamily = MontserratFontFamily,
                                    fontWeight = if (isFocused || isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Current Program Information
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Transmissão Digital • Grade Nacional/Regional",
                                    color = if (isFocused) Color.Black else ZapTextPrimary,
                                    fontFamily = MontserratFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Sinal contínuo ao vivo de alta definição",
                                    color = if (isFocused) Color(0xFF222222) else ZapTextMuted,
                                    fontFamily = MontserratFontFamily,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }

                            // Live / HD badge
                            Box(
                                modifier = Modifier
                                    .width(90.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isFocused) Color.Black.copy(alpha = 0.15f)
                                        else if (isCurrent) ZapGold.copy(alpha = 0.2f)
                                        else Color(0xFF20202C)
                                    )
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                              ) {
                                Text(
                                    text = if (isCurrent) "ASSISTINDO" else "AO VIVO",
                                    color = if (isFocused) Color.Black else if (isCurrent) ZapGold else ZapTextSecondary,
                                    fontFamily = MontserratFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "OK Sintonizar canal  •  ▲▼ Navegar na lista  •  🔵 / Voltar Fechar guia",
                        color = ZapTextSecondary,
                        fontFamily = MontserratFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }
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
