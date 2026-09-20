package com.zap.tv.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zap.tv.R

// Montserrat embutida no APK — funciona offline em qualquer TV Box
val MontserratFontFamily = FontFamily(
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_medium, FontWeight.Medium),
    Font(R.font.montserrat_semibold, FontWeight.SemiBold),
    Font(R.font.montserrat_bold, FontWeight.Bold),
    Font(R.font.montserrat_extrabold, FontWeight.ExtraBold),
    Font(R.font.montserrat_black, FontWeight.Black)
)

val ZapTypography = Typography(
    displayLarge  = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.Black, fontSize = 57.sp),
    displayMedium = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.Bold, fontSize = 45.sp),
    displaySmall  = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.Bold, fontSize = 36.sp),
    headlineLarge = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp),
    headlineMedium= TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
    headlineSmall = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge    = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium   = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    titleSmall    = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge     = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium    = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall     = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge    = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium   = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall    = TextStyle(fontFamily = MontserratFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp)
)
