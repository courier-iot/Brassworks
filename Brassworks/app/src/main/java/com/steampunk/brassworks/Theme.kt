package com.steampunk.brassworks

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.io.File

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
    // Purple: deep violet wood with amethyst metal
    Palette(
        "Amethyst",
        Color(0xFF120C1A), Color(0xFF1E1530), Color(0xFF2D2046),
        Color(0xFF9B6BD6), Color(0xFFD9BEFF), Color(0xFF4E2F82),
        Color(0xFFD9506A), Color(0xFF6FD0B0),
        Color(0xFFEDE6F5), Color(0xFF1B1226), Color(0xFFC8BFD6),
    ),
    // Pink: rosewood with soft rose metal (pink, not yellow gold)
    Palette(
        "Rose",
        Color(0xFF1A0F12), Color(0xFF2A1820), Color(0xFF3D2430),
        Color(0xFFD98E9A), Color(0xFFF7C9CF), Color(0xFF8A4553),
        Color(0xFFD94B4B), Color(0xFF6CC0A4),
        Color(0xFFF3E3DD), Color(0xFF2A1519), Color(0xFFD6C3C3),
    ),
    // Blue: navy with cobalt metal
    Palette(
        "Cobalt",
        Color(0xFF090E1A), Color(0xFF111A2E), Color(0xFF1B2A47),
        Color(0xFF3F7FE0), Color(0xFF9CC6FF), Color(0xFF1E4287),
        Color(0xFFF0883A), Color(0xFF5FD0C0),
        Color(0xFFE6EEF8), Color(0xFF0F1830), Color(0xFFB7C4D8),
    ),
    // Orange on charcoal: cooler and darker than Copper
    Palette(
        "Ember",
        Color(0xFF121110), Color(0xFF1F1C1A), Color(0xFF2F2926),
        Color(0xFFE2661F), Color(0xFFFFB06B), Color(0xFF8A3A0C),
        Color(0xFFC21E1E), Color(0xFF6AB8A0),
        Color(0xFFEEE3D2), Color(0xFF211510), Color(0xFFCFC6BB),
    ),
)

/**
 * The colours every screen uses. They read the current palette, so changing the theme
 * repaints everything immediately.
 */
/** A colour for the nixie tubes: the lit digit and the halo around it. */
class NixieColor(val name: String, val lit: Color, val glow: Color)

val NixieColors = listOf(
    NixieColor("Orange", Color(0xFFFFA24D), Color(0xFFFF6A00)),
    NixieColor("Amber", Color(0xFFFFD36B), Color(0xFFFFA800)),
    NixieColor("Red", Color(0xFFFF7A6B), Color(0xFFFF2A1A)),
    NixieColor("Pink", Color(0xFFFF9AD5), Color(0xFFFF2DAA)),
    NixieColor("Violet", Color(0xFFC9A0FF), Color(0xFF8A3DFF)),
    NixieColor("Blue", Color(0xFF8FD0FF), Color(0xFF2E8BFF)),
    NixieColor("Green", Color(0xFF9DFFB0), Color(0xFF1FD655)),
    NixieColor("White", Color(0xFFFFF4E0), Color(0xFFFFDFA8)),
)

object Steam {
    var palette by mutableStateOf(Palettes[0])

    /** The glow colour of every nixie tube. */
    var nixie by mutableStateOf(NixieColors[0])

    /** "steam" (brass, wood and rivets) or "diesel" (gunmetal, cut corners, neon edges and grime). */
    var style by mutableStateOf("steam")

    /** True in the Dieselpunk style. */
    val punk: Boolean get() = style == "diesel"

    /** Multiplies how long a gear takes to turn: 1.8 is slow, 1 normal, 0.5 fast. */
    var gearFactor by mutableFloatStateOf(1f)

    /** "classic", "victorian", "script", "typewriter", "plain" or "custom" (a font file you uploaded). */
    var fontKey by mutableStateOf("classic")
    private var custom by mutableStateOf<FontFamily?>(null)
    val hasCustomFont: Boolean get() = custom != null

    /** Old Standard TT, a 19th-century "Modern" face. The GitHub build downloads it into assets/fonts. */
    private var victorian by mutableStateOf<FontFamily?>(null)
    val hasVictorian: Boolean get() = victorian != null

    /** A picture you chose, drawn behind the widgets and the archive. */
    var wallpaper by mutableStateOf<ImageBitmap?>(null)
        private set
    val hasWallpaper: Boolean get() = wallpaper != null

    /** How much the wallpaper is darkened, 0 to 0.85. */
    var wallDim by mutableFloatStateOf(0.45f)

    /** The font used for all text. Reading it repaints everything when you change it. */
    val font: FontFamily
        get() = when (fontKey) {
            "victorian" -> victorian ?: FontFamily.Serif
            "script" -> FontFamily.Cursive
            "typewriter" -> FontFamily.Monospace
            "plain" -> FontFamily.SansSerif
            "custom" -> custom ?: FontFamily.Serif
            else -> FontFamily.Serif
        }

    // In Dieselpunk the wood becomes gunmetal and the brass becomes steel. Your Metal choice still tints
    // the steel a little, so Cobalt gives blue steel and Copper gives warm steel.
    val Soot: Color get() = if (punk) lerp(palette.soot, Color(0xFF080A0D), 0.75f) else palette.soot
    val Leather: Color get() = if (punk) lerp(palette.leather, Color(0xFF14181D), 0.8f) else palette.leather
    val Walnut: Color get() = if (punk) lerp(palette.walnut, Color(0xFF232930), 0.8f) else palette.walnut
    val Brass: Color get() = if (punk) lerp(palette.brass, Color(0xFF8A95A0), 0.7f) else palette.brass
    val BrassLight: Color get() = if (punk) lerp(palette.brassLight, Color(0xFFD3DBE2), 0.7f) else palette.brassLight
    val BrassDark: Color get() = if (punk) lerp(palette.brassDark, Color(0xFF39414A), 0.7f) else palette.brassDark
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
        style = if (p.getString("style", "steam") == "diesel") "diesel" else "steam"
        gearFactor = p.getFloat("gear", 1f)
        nixie = NixieColors.firstOrNull { it.name == p.getString("nixie", "Orange") } ?: NixieColors[0]
        fontKey = p.getString("font", "classic") ?: "classic"
        readCustomFont(ctx)
        readVictorian(ctx)
        if (fontKey == "custom" && custom == null) fontKey = "classic"
        if (fontKey == "victorian" && victorian == null) fontKey = "classic"
        wallDim = p.getFloat("wall_dim", 0.45f)
        readWallpaper(ctx)
    }

    private fun readVictorian(ctx: Context) {
        victorian = try {
            val names = ctx.assets.list("fonts")?.toSet().orEmpty()
            val need = listOf("OldStandard-Regular.ttf", "OldStandard-Bold.ttf", "OldStandard-Italic.ttf")
            if (names.containsAll(need)) {
                val am = ctx.assets
                FontFamily(
                    Font("fonts/OldStandard-Regular.ttf", am, FontWeight.Normal),
                    Font("fonts/OldStandard-Bold.ttf", am, FontWeight.Bold),
                    Font("fonts/OldStandard-Italic.ttf", am, FontWeight.Normal, FontStyle.Italic),
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    // ----- wallpaper -----

    private fun wallFile(ctx: Context) = File(ctx.filesDir, "wallpaper.jpg")

    private fun readWallpaper(ctx: Context) {
        val f = wallFile(ctx)
        wallpaper = if (f.exists()) {
            try { BitmapFactory.decodeFile(f.path)?.asImageBitmap() } catch (e: Exception) { null }
        } else {
            null
        }
    }

    /** Shrinks the picture to a sensible size, fixes its rotation, and keeps a private copy. */
    fun installWallpaper(ctx: Context, uri: Uri): Boolean {
        return try {
            val cr = ctx.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1800) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return false
            val turn = try {
                cr.openInputStream(uri)?.use { stream ->
                    when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            } catch (e: Exception) {
                0f
            }
            if (turn != 0f) {
                val m = Matrix().apply { postRotate(turn) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            wallFile(ctx).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            wallpaper = bmp.asImageBitmap()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun removeWallpaper(ctx: Context) {
        wallFile(ctx).delete()
        wallpaper = null
    }

    fun setDim(ctx: Context, v: Float) {
        wallDim = v
        prefs(ctx).edit().putFloat("wall_dim", v).apply()
    }

    fun chooseNixie(ctx: Context, c: NixieColor) {
        nixie = c
        prefs(ctx).edit().putString("nixie", c.name).apply()
    }

    fun choose(ctx: Context, pal: Palette) {
        palette = pal
        prefs(ctx).edit().putString("theme", pal.name).apply()
    }

    /** Switch between "steam" and "diesel". Everything repaints straight away. */
    fun chooseStyle(ctx: Context, key: String) {
        style = if (key == "diesel") "diesel" else "steam"
        prefs(ctx).edit().putString("style", style).apply()
    }

    private fun customFile(ctx: Context) = File(ctx.filesDir, "custom_font")

    private fun readCustomFont(ctx: Context) {
        val f = customFile(ctx)
        custom = if (f.exists()) {
            try { FontFamily(Typeface.createFromFile(f)) } catch (e: Exception) { null }
        } else {
            null
        }
    }

    fun chooseFont(ctx: Context, key: String) {
        fontKey = key
        prefs(ctx).edit().putString("font", key).apply()
    }

    private fun looksLikeFont(b: ByteArray): Boolean {
        val tag = String(b, Charsets.ISO_8859_1)
        val ttf = b[0].toInt() == 0 && b[1].toInt() == 1 && b[2].toInt() == 0 && b[3].toInt() == 0
        return ttf || tag == "true" || tag == "OTTO" || tag == "ttcf"
    }

    /** Copies a .ttf or .otf you picked into the app's private storage. Returns false if it is not a font. */
    fun installCustomFont(ctx: Context, uri: Uri): Boolean {
        return try {
            val tmp = File(ctx.filesDir, "custom_font.tmp")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { out -> input.copyTo(out) }
            } ?: return false
            val head = ByteArray(4)
            val sizeOk = tmp.length() in 1_000L..25_000_000L
            val magicOk = sizeOk && tmp.inputStream().use { it.read(head) == 4 } && looksLikeFont(head)
            if (!magicOk) {
                tmp.delete()
                return false
            }
            tmp.copyTo(customFile(ctx), overwrite = true)
            tmp.delete()
            readCustomFont(ctx)
            if (custom == null) return false
            chooseFont(ctx, "custom")
            true
        } catch (e: Exception) {
            false
        }
    }

    fun removeCustomFont(ctx: Context) {
        customFile(ctx).delete()
        custom = null
        if (fontKey == "custom") chooseFont(ctx, "classic")
    }

    fun setGear(ctx: Context, factor: Float) {
        gearFactor = factor
        prefs(ctx).edit().putFloat("gear", factor).apply()
    }
}

val LocalAnimations = compositionLocalOf { true }

/** False while the home screen is in the background, so sensors and timers can sleep. */
val LocalActive = compositionLocalOf { true }

private fun steamTypography(font: FontFamily): Typography {
    val base = Typography()
    // Line heights are relative (em) so they shrink with the text. A fixed 24sp line used to inflate
    // every small label and get it clipped at the bottom of a widget.
    return Typography(
        headlineMedium = base.headlineMedium.copy(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = 1.5.sp, lineHeight = 1.2.em),
        titleLarge = base.titleLarge.copy(fontFamily = font, fontWeight = FontWeight.Bold, lineHeight = 1.2.em),
        titleMedium = base.titleMedium.copy(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 1.25.em),
        bodyLarge = base.bodyLarge.copy(fontFamily = font, fontSize = 17.sp, lineHeight = 1.3.em),
        bodyMedium = base.bodyMedium.copy(fontFamily = font, lineHeight = 1.3.em),
        labelLarge = base.labelLarge.copy(fontFamily = font, fontWeight = FontWeight.Medium, lineHeight = 1.25.em),
    )
}

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
        typography = remember(Steam.font) { steamTypography(Steam.font) },
        content = content,
    )
}
