package com.steampunk.brassworks

import android.app.Activity
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** One complete colour scheme: three metals, three woods, plus accents and text colours. */
class Palette(
    val name: String,
    val soot: Color, val leather: Color, val walnut: Color,
    val brass: Color, val brassLight: Color, val brassDark: Color,
    val copper: Color, val verdigris: Color,
    val parchment: Color, val ink: Color, val steam: Color,
)

val Palettes = listOf(
    Palette(
        "Brass",
        Color(0xFF1B1210), Color(0xFF2B1B14), Color(0xFF3E2A1E),
        Color(0xFFB5883E), Color(0xFFE3C16F), Color(0xFF6E4F1E),
        Color(0xFFC25A30), Color(0xFF58B09C),
        Color(0xFFEBDDB8), Color(0xFF241810), Color(0xFFCFC8B8),
    ),
    Palette(
        "Copper",
        Color(0xFF1D100C), Color(0xFF2E1912), Color(0xFF432619),
        Color(0xFFC8703F), Color(0xFFF0B08A), Color(0xFF7A3B1C),
        Color(0xFFE0413A), Color(0xFF58B09C),
        Color(0xFFEFDDC5), Color(0xFF2A1810), Color(0xFFD2C4B6),
    ),
    Palette(
        "Silver",
        Color(0xFF0F1316), Color(0xFF181E23), Color(0xFF252D34),
        Color(0xFFA9B4BB), Color(0xFFE9EEF1), Color(0xFF5A656C),
        Color(0xFFD0603A), Color(0xFF5FB8A4),
        Color(0xFFE7ECEF), Color(0xFF151A1E), Color(0xFFB4BEC5),
    ),
    Palette(
        "Verdigris",
        Color(0xFF0B1311), Color(0xFF14211E), Color(0xFF1E322D),
        Color(0xFF4E9C8A), Color(0xFFA2E3CF), Color(0xFF245549),
        Color(0xFFCF6A3C), Color(0xFF8AD6BE),
        Color(0xFFE4ECDD), Color(0xFF10211C), Color(0xFFB7C9C0),
    ),
)

/**
 * The colours every screen uses. They read the current palette, so changing the theme
 * repaints everything immediately.
 */
object Steam {
    var palette by mutableStateOf(Palettes[0])

    /** Multiplies how long a gear takes to turn: 1.8 is slow, 1 normal, 0.5 fast. */
    var gearFactor by mutableFloatStateOf(1f)

    val Soot: Color get() = palette.soot
    val Leather: Color get() = palette.leather
    val Walnut: Color get() = palette.walnut
    val Brass: Color get() = palette.brass
    val BrassLight: Color get() = palette.brassLight
    val BrassDark: Color get() = palette.brassDark
    val Copper: Color get() = palette.copper
    val Verdigris: Color get() = palette.verdigris
    val Parchment: Color get() = palette.parchment
    val Ink: Color get() = palette.ink
    val Steam: Color get() = palette.steam

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("brassworks", Context.MODE_PRIVATE)

    /** Call once at start-up, before anything is drawn. */
    fun load(ctx: Context) {
        val p = prefs(ctx)
        palette = Palettes.firstOrNull { it.name == p.getString("theme", "Brass") } ?: Palettes[0]
        gearFactor = p.getFloat("gear", 1f)
    }

    fun choose(ctx: Context, pal: Palette) {
        palette = pal
        prefs(ctx).edit().putString("theme", pal.name).apply()
    }

    fun setGear(ctx: Context, factor: Float) {
        gearFactor = factor
        prefs(ctx).edit().putFloat("gear", factor).apply()
    }
}

val LocalAnimations = compositionLocalOf { true }

/** False while the home screen is in the background, so sensors and timers can sleep. */
val LocalActive = compositionLocalOf { true }

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
    // keep the status and navigation bars in the same wood tone as the theme
    val view = LocalView.current
    val bar = Steam.Soot.toArgb()
    SideEffect {
        val window = (view.context as? Activity)?.window
        window?.statusBarColor = bar
        window?.navigationBarColor = bar
    }
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
