package io.suzuai.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val MonoFamily = FontFamily.Monospace

val SuzuTypography = Typography(
    displayLarge = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp),
    headlineLarge = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    headlineMedium = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp),
    titleLarge = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    titleMedium = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = MonoFamily, fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontFamily = MonoFamily, fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelMedium = TextStyle(fontFamily = MonoFamily, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp),
)
