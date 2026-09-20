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

enum class CategoryFilter(val label: String, val icon: String, val keyword: String) {
    ALL("Todos os Canais", "📺", ""),
    FAVORITES("Favoritos", "⭐", "fav"),
    GENERAL("Canais Abertos", "📡", "general"),
    SPORTS("Esportes", "⚽", "sports"),
    MOVIES("Filmes & Séries", "🎬", "movies"),
    NEWS("Notícias", "📰", "news"),
    KIDS("Infantil", "🧸", "kids"),
    MUSIC("Música", "🎶", "music"),
    DOCUMENTARY("Documentários", "🌍", "documentary"),
    MARATONAR("Canais para Maratonar", "🍿", "maratonar"),
    MUNDO("Canais pelo Mundo", "🌐", "mundo")
}

@Composable
fun CategoriesDrawer(
    visible: Boolean,
    channels: List<ChannelNumberEntity>,
    favorites: Set<Int>,
    currentChannelNumber: Int,
    onSelectChannel: (Int) -> Unit,
    onClose: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf(CategoryFilter.ALL) }
    val initialFocusRequester = remember { FocusRequester() }

    val filteredChannels = remember(selectedCategory, channels, favorites) {
        when (selectedCategory) {
            CategoryFilter.ALL -> channels
            CategoryFilter.FAVORITES -> channels.filter { it.number in favorites }
            CategoryFilter.GENERAL -> channels.filter {
                it.number in 1..9 || it.categories.contains("general", ignoreCase = true)
            }
            CategoryFilter.SPORTS -> channels.filter {
                it.categories.contains("sports", ignoreCase = true) ||
                it.channelName.contains("sport", ignoreCase = true) ||
                it.channelName.contains("espn", ignoreCase = true) ||
                it.channelName.contains("premiere", ignoreCase = true) ||
                it.channelName.contains("combate", ignoreCase = true)
            }
            CategoryFilter.MOVIES -> channels.filter {
                it.categories.contains("movies", ignoreCase = true) ||
                it.categories.contains("series", ignoreCase = true) ||
                it.categories.contains("entertainment", ignoreCase = true) ||
                it.channelName.contains("telecine", ignoreCase = true) ||
                it.channelName.contains("hbo", ignoreCase = true) ||
                it.channelName.contains("cinema", ignoreCase = true)
            }
            CategoryFilter.NEWS -> channels.filter {
                it.categories.contains("news", ignoreCase = true) ||
                it.channelName.contains("news", ignoreCase = true) ||
                it.channelName.contains("cnn", ignoreCase = true) ||
                it.channelName.contains("globonews", ignoreCase = true)
            }
            CategoryFilter.KIDS -> channels.filter {
                it.categories.contains("kids", ignoreCase = true) ||
                it.categories.contains("animation", ignoreCase = true) ||
                it.channelName.contains("cartoon", ignoreCase = true) ||
                it.channelName.contains("gloob", ignoreCase = true) ||
                it.channelName.contains("disney", ignoreCase = true)
            }
            CategoryFilter.MUSIC -> channels.filter {
                it.categories.contains("music", ignoreCase = true) ||
                it.channelName.contains("mtv", ignoreCase = true) ||
                it.channelName.contains("bis", ignoreCase = true) ||
                it.channelName.contains("multishow", ignoreCase = true)
            }
            CategoryFilter.DOCUMENTARY -> channels.filter {
                it.categories.contains("documentary", ignoreCase = true) ||
                it.categories.contains("culture", ignoreCase = true) ||
                it.channelName.contains("discovery", ignoreCase = true) ||
                it.channelName.contains("history", ignoreCase = true) ||
                it.channelName.contains("geo", ignoreCase = true)
            }
            CategoryFilter.MARATONAR -> channels.filter {
                it.categories.contains("maratonar", ignoreCase = true)
            }
            CategoryFilter.MUNDO -> channels.filter {
                it.categories.contains("mundo", ignoreCase = true) ||
                it.country == "INTL"
            }
        }
    }

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
        enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .width(480.dp)
                .background(Color(0xF50F0F14))
                .border(width = 1.dp, color = Color(0xFF2D2D3A))
                .padding(20.dp)
        ) {
            // Left Column: Category Pills
            Column(
                modifier = Modifier
                    .width(170.dp)
                    .fillMaxHeight()
            ) {
                Text(
                    text = "GÊNEROS",
                    color = ZapGold,
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxHeight()
                ) {
                    items(CategoryFilter.values()) { cat ->
                        val isSelected = cat == selectedCategory
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
                                .clickableNoRipple { selectedCategory = cat }
                                .then(if (cat == CategoryFilter.ALL) Modifier.focusRequester(initialFocusRequester) else Modifier)
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = cat.icon, fontSize = 16.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = cat.label,
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

            // Right Column: Channel List for chosen Category
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
                        text = selectedCategory.label.uppercase(),
                        color = ZapTextPrimary,
                        fontFamily = MontserratFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = " canais",
                        color = ZapTextMuted,
                        fontFamily = MontserratFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
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
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        itemsIndexed(filteredChannels) { index, ch ->
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
                                // Channel Number
                                Text(
                                    text = "",
                                    color = if (isFocused) Color.Black else if (isCurrent) ZapGold else ZapTextSecondary,
                                    fontFamily = MontserratFontFamily,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    modifier = Modifier.width(36.dp)
                                )

                                // Channel Logo
                                Box(
                                    modifier = Modifier
                                        .size(width = 36.dp, height = 26.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF22222E)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!ch.logoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ch.logoUrl,
                                            contentDescription = null,
                                            modifier = Modifier.size(width = 32.dp, height = 22.dp)
                                        )
                                    } else {
                                        Text(ch.country, color = Color.White, fontSize = 9.sp)
                                    }
                                }

                                Spacer(Modifier.width(10.dp))

                                // Channel Name
                                Text(
                                    text = ch.channelName,
                                    color = if (isFocused) Color.Black else if (isCurrent) ZapGold else ZapTextPrimary,
                                    fontFamily = MontserratFontFamily,
                                    fontWeight = if (isFocused || isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                if (ch.number in favorites) {
                                    Text("★", color = if (isFocused) Color.Black else ZapGold, fontSize = 13.sp)
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
