package com.zap.tv.presentation.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zap.tv.ui.theme.*

@Composable
fun ChannelNumberOverlay(digits: String) {
    if (digits.isEmpty()) return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .background(Color(0xEB101016), RoundedCornerShape(20.dp))
                .border(2.5.dp, ZapGold, RoundedCornerShape(20.dp))
                .padding(horizontal = 48.dp, vertical = 24.dp)
        ) {
            Text(
                text = digits,
                color = ZapGold,
                fontFamily = MontserratFontFamily,
                fontSize = 72.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
fun ParentalLockOverlay(error: String?, onSubmit: (String) -> Unit, onCancel: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .background(Color(0xFF16161E), RoundedCornerShape(18.dp))
                .border(1.5.dp, ZapGold, RoundedCornerShape(18.dp))
                .padding(28.dp)
                .width(440.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🔒  Canal Restrito",
                color = ZapTextPrimary,
                fontFamily = MontserratFontFamily,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Digite o PIN de 4 dígitos para desbloquear",
                color = ZapTextSecondary,
                fontFamily = MontserratFontFamily,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = if (pin.isEmpty()) "••••" else pin.map { "•" }.joinToString(" "),
                color = ZapGold,
                fontFamily = MontserratFontFamily,
                fontSize = 36.sp,
                letterSpacing = 12.sp,
                fontWeight = FontWeight.Black
            )
            if (!error.isNullOrEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error,
                    color = ZapLiveRed,
                    fontFamily = MontserratFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(18.dp))
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(5),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(120.dp)
            ) {
                items(10) { i ->
                    val digit = if (i < 9) "${i + 1}" else "0"
                    Box(
                        Modifier
                            .size(50.dp)
                            .border(1.dp, Color(0xFF383848), RoundedCornerShape(10.dp))
                            .background(Color(0xFF22222E), RoundedCornerShape(10.dp))
                            .clickableNoRipple { if (pin.length < 4) pin += digit },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = digit,
                            color = ZapTextPrimary,
                            fontFamily = MontserratFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
                item {
                    Box(
                        Modifier
                            .size(50.dp)
                            .border(1.dp, Color(0xFF383848), RoundedCornerShape(10.dp))
                            .background(Color(0xFF22222E), RoundedCornerShape(10.dp))
                            .clickableNoRipple { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⌫", color = ZapTextPrimary, fontFamily = MontserratFontFamily, fontSize = 16.sp)
                    }
                }
                item {
                    Box(
                        Modifier
                            .size(50.dp)
                            .background(ZapGold, RoundedCornerShape(10.dp))
                            .clickableNoRipple { if (pin.length == 4) onSubmit(pin) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "OK",
                            color = Color.Black,
                            fontFamily = MontserratFontFamily,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            androidx.compose.material3.OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Cancelar / Voltar",
                    fontFamily = MontserratFontFamily,
                    fontWeight = FontWeight.SemiBold
                )
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
