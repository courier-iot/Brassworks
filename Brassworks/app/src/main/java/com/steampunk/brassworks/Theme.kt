package com.steampunk.brassworks

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Steam {
    val Soot = Color(0xFF1B1210)
    val Leather = Color(0xFF2B1B14)
    val Walnut = Color(0xFF3E2A1E)
    val Brass = Color(0xFFB5883E)
    val BrassLight = Color(0xFFE3C16F)
    val BrassDark = Color(0xFF6E4F1E)
    val Copper = Color(0xFFC25A30)
    val Verdigris = Color(0xFF58B09C)
    val Parchment = Color(0xFFEBDDB8)
    val Ink = Color(0xFF241810)
    val Steam = Color(0xFFCFC8B8)
}

val LocalAnimations = compositionLocalOf { true }

private val base = Typography()
private val serif = FontFamily.Serif
private val SteamTypography = Typography(
    headlineMedium = base.headlineMedium.copy(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 1.5.sp),
    titleLarge = base.titleLarge.copy(fontFamily = serif, fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 17.sp),
    bodyLarge = base.bodyLarge.copy(fontFamily = serif, fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = serif, lineHeight = 21.sp),
    labelLarge = base.labelLarge.copy(fontFamily = serif, fontWeight = FontWeight.Medium),
)

@Composable
fun SteampunkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Steam.Brass, onPrimary = Steam.Ink,
            secondary = Steam.Copper, background = Steam.Soot,
            surface = Steam.Leather, onSurface = Steam.Parchment, onBackground = Steam.Parchment,
        ),
        typography = SteamTypography,
        content = content,
    )
}
