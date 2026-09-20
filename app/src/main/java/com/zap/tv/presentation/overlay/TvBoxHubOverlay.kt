package com.zap.tv.presentation.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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

enum class TvBoxTab(val label: String, val icon: String) {
    CATEGORIES("Canais", "📺"),
    FAVORITES("Favoritos", "⭐"),
    SETTINGS("Ajustes", "⚙️")
}

@Composable
fun TvBoxHubOverlay(
    visible: Boolean,
    channels: List<ChannelNumberEntity>,
    favorites: Set<Int>,
    currentChannelNumber: Int,
    currentAspectRatio: AspectRatioMode,
    onSelectChannel: (Int) -> Unit,
    onSetAspectRatio: (AspectRatioMode) -> Unit,
    onClose: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(TvBoxTab.CATEGORIES) }
    var selectedCategory by remember { mutableStateOf(CategoryFilter.ALL) }
    
    val initialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(visible) {
        if (visible) {
            delay(150)
            try { initialFocusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    val filteredChannels = remember(selectedTab, selectedCategory, channels, favorites) {
        if (selectedTab == TvBoxTab.FAVORITES) {
            channels.filter { it.number in favorites }
        } else {
            when (selectedCategory) {
                CategoryFilter.ALL -> channels
                CategoryFilter.FAVORITES -> channels.filter { it.number in favorites }
                CategoryFilter.GENERAL -> channels.filter { it.number in 1..9 || it.categories.contains("general", true) }
                CategoryFilter.SPORTS -> channels.filter { it.categories.contains("sports", true) || it.channelName.contains("sport", true) || it.channelName.contains("espn", true) || it.channelName.contains("premiere", true) }
                CategoryFilter.MOVIES -> channels.filter { it.categories.contains("movies", true) || it.categories.contains("series", true) || it.channelName.contains("telecine", true) || it.channelName.contains("hbo", true) }
                CategoryFilter.NEWS -> channels.filter { it.categories.contains("news", true) || it.channelName.contains("news", true) || it.channelName.contains("cnn", true) || it.channelName.contains("globonews", true) }
                CategoryFilter.KIDS -> channels.filter { it.categories.contains("kids", true) || it.channelName.contains("cartoon", true) || it.channelName.contains("gloob", true) || it.channelName.contains("disney", true) }
                CategoryFilter.MUSIC -> channels.filter { it.categories.contains("music", true) || it.channelName.contains("mtv", true) || it.channelName.contains("multishow", true) }
                CategoryFilter.DOCUMENTARY -> channels.filter { it.categories.contains("documentary", true) || it.channelName.contains("discovery", true) || it.channelName.contains("history", true) }
                CategoryFilter.MARATONAR -> channels.filter { it.categories.contains("maratonar", true) }
                CategoryFilter.MUNDO -> channels.filter { it.categories.contains("mundo", true) || it.country == "INTL" }
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .width(620.dp) // Wider than CategoriesDrawer to fit Tabs + Genres + List
                .background(Color(0xF50F0F14))
                .border(width = 1.dp, color = Color(0xFF2D2D3A))
                .padding(20.dp)
        ) {
            // COLUNA 1: ABAS PRINCIPAIS (TV BOX)
            Column(
                modifier = Modifier
                    .width(140.dp)
                    .fillMaxHeight()
            ) {
                Text(
                    text = "MENU TV BOX",
                    color = ZapGold,
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                )

                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(TvBoxTab.values()) { tab ->
                        val isSelected = tab == selectedTab
                        var isFocused by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isFocused) ZapGold
                                    else if (isSelected) Color(0xFF2C2814)
                                    else Color(0xFF1B1B24)
                                )
                                .border(
                                    width = if (isFocused) 2.dp else 0.dp,
                                    color = if (isFocused) Color.White else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .onFocusChanged { isFocused = it.isFocused }
                                .focusable()
                                .clickableNoRipple { selectedTab = tab }
                                .then(if (tab == TvBoxTab.CATEGORIES) Modifier.focusRequester(initialFocusRequester) else Modifier)
                                .padding(horizontal = 10.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = tab.icon, fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = tab.label,
                                color = if (isFocused) Color.Black else if (isSelected) ZapGold else ZapTextPrimary,
                                fontFamily = MontserratFontFamily,
                                fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(16.dp))
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color(0xFF2A2A38)))
            Spacer(Modifier.width(16.dp))

            // DEPENDENDO DA ABA SELECIONADA
            if (selectedTab == TvBoxTab.SETTINGS) {
                // RENDERIZAR AJUSTES DE TELA
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Text(
                        text = "AJUSTES DE TELA",
                        color = ZapTextPrimary,
                        fontFamily = MontserratFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    val aspectModes = AspectRatioMode.values()
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(aspectModes) { mode ->
                            val isSelected = mode == currentAspectRatio
                            var isFocused by remember { mutableStateOf(false) }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isFocused) ZapGold else if (isSelected) Color(0xFF2C2814) else Color(0xFF181822))
                                    .border(
                                        width = if (isFocused) 2.dp else if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isFocused) Color.White else if (isSelected) ZapGold else Color(0xFF282836),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .focusable()
                                    .clickableNoRipple { 
                                        onSetAspectRatio(mode) 
                                        onClose()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isSelected) "✓  " else "     ",
                                    color = if (isFocused) Color.Black else ZapGold,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = mode.label,
                                    color = if (isFocused) Color.Black else if (isSelected) ZapGold else ZapTextPrimary,
                                    fontFamily = MontserratFontFamily,
                                    fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            } else {
                // RENDERIZAR CATEGORIAS E LISTA DE CANAIS (MUITO SIMILAR AO CategoriesDrawer)
                if (selectedTab == TvBoxTab.CATEGORIES) {
                    // COLUNA 2: CATEGORIAS (Só aparece se não for Favoritos)
                    Column(
                        modifier = Modifier
                            .width(150.dp)
                            .fillMaxHeight()
                    ) {
                        Text(
                            text = "GÊNEROS",
                            color = ZapTextPrimary,
                            fontFamily = MontserratFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                        )

                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(CategoryFilter.values().filter { it != CategoryFilter.FAVORITES }) { cat ->
                                val isSelected = cat == selectedCategory
                                var isFocused by remember { mutableStateOf(false) }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isFocused) Color(0xFF3C3C4C) else if (isSelected) Color(0xFF282836) else Color.Transparent)
                                        .onFocusChanged { isFocused = it.isFocused }
                                        .focusable()
                                        .clickableNoRipple { selectedCategory = cat }
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = cat.icon, fontSize = 14.sp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = cat.label,
                                        color = if (isFocused) Color.White else if (isSelected) ZapTextPrimary else ZapTextSecondary,
                                        fontFamily = MontserratFontFamily,
                                        fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.width(12.dp))
                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color(0xFF2A2A38)))
                    Spacer(Modifier.width(12.dp))
                }

                // COLUNA 3: LISTA DE CANAIS
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (selectedTab == TvBoxTab.FAVORITES) "MEUS FAVORITOS" else selectedCategory.label.uppercase(),
                            color = ZapTextPrimary,
                            fontFamily = MontserratFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    if (filteredChannels.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Nenhum canal encontrado",
                                color = ZapTextMuted,
                                fontFamily = MontserratFontFamily,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            itemsIndexed(filteredChannels) { _, ch ->
                                val isCurrent = ch.number == currentChannelNumber
                                var isFocused by remember { mutableStateOf(false) }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isFocused) ZapGold
                                            else if (isCurrent) Color(0xFF2C2814)
                                            else Color(0xFF181822)
                                        )
                                        .border(
                                            width = if (isFocused) 2.dp else if (isCurrent) 1.5.dp else 1.dp,
                                            color = if (isFocused) Color.White else if (isCurrent) ZapGold else Color(0xFF282836),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .onFocusChanged { isFocused = it.isFocused }
                                        .focusable()
                                        .clickableNoRipple {
                                            onSelectChannel(ch.number)
                                            onClose()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${ch.number}",
                                        color = if (isFocused) Color.Black else if (isCurrent) ZapGold else ZapTextSecondary,
                                        fontFamily = MontserratFontFamily,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        modifier = Modifier.width(30.dp)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(width = 30.dp, height = 22.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFF22222E)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!ch.logoUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = ch.logoUrl,
                                                contentDescription = null,
                                                modifier = Modifier.size(width = 26.dp, height = 18.dp)
                                            )
                                        } else {
                                            Text(ch.country, color = Color.White, fontSize = 8.sp)
                                        }
                                    }

                                    Spacer(Modifier.width(8.dp))

                                    Text(
                                        text = ch.channelName,
                                        color = if (isFocused) Color.Black else if (isCurrent) ZapGold else ZapTextPrimary,
                                        fontFamily = MontserratFontFamily,
                                        fontWeight = if (isFocused || isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )

                                    if (ch.number in favorites) {
                                        Text("★", color = if (isFocused) Color.Black else ZapGold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
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
