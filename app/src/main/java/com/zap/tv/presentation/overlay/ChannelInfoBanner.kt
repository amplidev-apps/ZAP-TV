package com.zap.tv.presentation.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.zap.tv.R
import com.zap.tv.data.local.entity.ChannelNumberEntity
import com.zap.tv.ui.theme.*
import com.zap.tv.util.CategoryTranslator
import com.zap.tv.util.CountryTranslator
import com.zap.tv.util.FlagEmoji
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChannelInfoBanner(
    visible: Boolean,
    channel: ChannelNumberEntity,
    isFavorite: Boolean,
    deviceMode: String,
    isReconnecting: Boolean = false,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
        modifier = modifier
    ) {
        val currentTime = remember {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            sdf.format(Date())
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 20.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xF0181820),
                            Color(0xF80E0E12)
                        )
                    )
                )
                .border(1.5.dp, Color(0xFF323240), RoundedCornerShape(18.dp))
                .padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header receiver estilo — logo ZAP-TV no topo à esquerda
                // Header receiver — mapeia faixa superior: 48dp reservados à esquerda para logo quadrada 1024×1024
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1A1A24))
                            .border(1.dp, Color(0xFF2A2A3C), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.zap_tv_logo),
                            contentDescription = "ZAP-TV",
                            modifier = Modifier.fillMaxSize().padding(6.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Box(
                        Modifier.weight(1f).height(1.dp).background(Color(0xFF2E2E3C))
                    )
                    Spacer(Modifier.width(8.dp))
                    if (isReconnecting) {
                        Text(
                            text = "• Extraindo Sinal",
                            color = ZapGold,
                            fontFamily = MontserratFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    } else {
                        Text(
                            text = "ZAP-TV",
                            color = Color(0xFF6C6C7A),
                            fontFamily = MontserratFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
                // Top Row: Logo, Number, Name, Badges, Favorite
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Logo Box
                    Box(
                        modifier = Modifier
                            .size(width = 68.dp, height = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF242430))
                            .border(1.dp, Color(0xFF3C3C4C), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!channel.logoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = channel.logoUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(width = 60.dp, height = 42.dp)
                                    .padding(4.dp)
                            )
                        } else {
                            Text(
                                text = channel.country,
                                color = ZapGold,
                                fontFamily = MontserratFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(Modifier.width(16.dp))

                    // Channel Number
                    Text(
                        text = "${channel.number}",
                        color = ZapGold,
                        fontFamily = MontserratFontFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 32.sp
                    )

                    Spacer(Modifier.width(14.dp))

                    // Channel Name and Category + Country
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = channel.channelName,
                                color = ZapTextPrimary,
                                fontFamily = MontserratFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 21.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (isFavorite) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "★",
                                    color = ZapGold,
                                    fontSize = 18.sp
                                )
                            }
                        }

                        val catText = CategoryTranslator.translateList(channel.categories)
                        val countryText = CountryTranslator.translate(channel.country)
                        val flag = FlagEmoji.forCountry(channel.country)
                        val countryWithFlag = if (flag.isNotEmpty()) "$flag $countryText" else countryText
                        val subtitle = buildString {
                            if (catText.isNotBlank()) append(catText)
                            if (catText.isNotBlank() && countryWithFlag.isNotBlank()) append("  •  ")
                            if (countryWithFlag.isNotBlank()) append(countryWithFlag)
                        }
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                color = ZapTextSecondary,
                                fontFamily = MontserratFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                    }

                    // Quality Badges
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QualityBadge("FHD")
                        QualityBadge("1080p")

                        // Live Badge
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(ZapLiveRed.copy(alpha = 0.2f))
                                .border(1.dp, ZapLiveRed, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(ZapLiveRed)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "AO VIVO",
                                color = ZapLiveRed,
                                fontFamily = MontserratFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }

                        // Clock
                        Text(
                            text = currentTime,
                            color = ZapTextPrimary,
                            fontFamily = MontserratFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Middle Row: Now & Next program info with Progress Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF16161E))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "NO AR: ",
                                color = ZapGold,
                                fontFamily = MontserratFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Transmissão Digital Ao Vivo",
                                color = ZapTextPrimary,
                                fontFamily = MontserratFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                        Text(
                            text = "A seguir: Programação Geral",
                            color = ZapTextMuted,
                            fontFamily = MontserratFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(Modifier.height(6.dp))

                    // Progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF2E2E3C))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .fillMaxHeight()
                                .background(ZapGold)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Bottom Footer: Hints for Navigation
                if (deviceMode == "ANDROID_TV") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ColorKeyLegend(ZapRedKey, if (isFavorite) "Remover Favorito" else "Favoritar")
                        ColorKeyLegend(ZapGreenKey, "Categorias")
                        ColorKeyLegend(ZapYellowKey, "Áudio & Tela")
                        ColorKeyLegend(ZapBlueKey, "Guia Completo")
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Pressione e segure [OK] para Favoritar, ver Categorias e Opções",
                            color = ZapTextSecondary,
                            fontFamily = MontserratFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QualityBadge(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(ZapBadgeBackground)
            .border(1.dp, Color(0xFF404050), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = ZapTextSecondary,
            fontFamily = MontserratFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

@Composable
fun ColorKeyLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = ZapTextSecondary,
            fontFamily = MontserratFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )
    }
}
