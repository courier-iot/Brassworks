package com.steampunk.brassworks

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal fun polar(c: Offset, r: Float, deg: Double) = Offset(
    c.x + r * cos(Math.toRadians(deg)).toFloat(),
    c.y + r * sin(Math.toRadians(deg)).toFloat(),
)

/** A slowly turning brass gear. */
@Composable
fun Gear(
    modifier: Modifier = Modifier,
    teeth: Int = 12,
    speed: Float = 12f, // seconds per full turn
    reverse: Boolean = false,
    animate: Boolean = LocalAnimations.current,
) {
    val angle = if (animate) {
        val t = rememberInfiniteTransition(label = "gear")
        val a by t.animateFloat(
            initialValue = 0f,
            targetValue = if (reverse) -360f else 360f,
            animationSpec = infiniteRepeatable(tween((speed * 1000f * Steam.gearFactor).toInt(), easing = LinearEasing)),
            label = "rot",
        )
        a
    } else 0f

    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f
        val brush = Brush.radialGradient(listOf(Steam.BrassLight, Steam.Brass, Steam.BrassDark), center = c, radius = r)
        rotate(angle, c) {
            val toothW = (2f * Math.PI.toFloat() * r * 0.8f / teeth) * 0.5f
            for (i in 0 until teeth) {
                rotate(i * 360f / teeth, c) {
                    drawRect(brush, topLeft = Offset(c.x - toothW / 2, c.y - r), size = Size(toothW, r * 0.22f))
                }
            }
            drawCircle(brush, r * 0.84f, c)
            drawCircle(Steam.Soot.copy(alpha = 0.85f), r * 0.58f, c)
            for (i in 0 until 4) {
                rotate(i * 90f + 45f, c) {
                    drawRect(brush, topLeft = Offset(c.x - r * 0.05f, c.y - r * 0.6f), size = Size(r * 0.10f, r * 0.3f))
                }
            }
            drawCircle(brush, r * 0.34f, c)
            drawCircle(Steam.Soot, r * 0.12f, c)
        }
    }
}

/** Leather backdrop with two ghost gears. Used behind every screen. */
@Composable
fun SteamBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Steam.Leather, Steam.Soot)))) {
        Gear(Modifier.size(280.dp).align(Alignment.TopEnd).offset(100.dp, (-60).dp).alpha(0.09f), teeth = 14, speed = 48f)
        Gear(Modifier.size(220.dp).align(Alignment.CenterStart).offset((-110).dp, 40.dp).alpha(0.07f), teeth = 11, speed = 36f, reverse = true)
        Gear(Modifier.size(200.dp).align(Alignment.BottomEnd).offset(70.dp, 60.dp).alpha(0.08f), teeth = 10, speed = 30f, reverse = true)
    }
}

private fun DrawScope.rivet(at: Offset, scale: Float = 1f) {
    drawCircle(Steam.BrassDark, 6.dp.toPx() * scale, at)
    drawCircle(Steam.Brass, 4.5.dp.toPx() * scale, at)
    drawCircle(Steam.BrassLight, 1.8.dp.toPx() * scale, at - Offset(1.5.dp.toPx() * scale, 1.5.dp.toPx() * scale))
}

/** Dark wood panel with a brass frame and four rivets. */
@Composable
fun RivetedPanel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .shadow(6.dp, shape)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Steam.Walnut, Steam.Leather)))
            .border(3.dp, Brush.verticalGradient(listOf(Steam.BrassLight, Steam.Brass, Steam.BrassDark)), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .drawBehind {
                val i = 10.dp.toPx()
                rivet(Offset(i, i)); rivet(Offset(size.width - i, i))
                rivet(Offset(i, size.height - i)); rivet(Offset(size.width - i, size.height - i))
            }
            .padding(16.dp),
        content = content,
    )
}

/** Chunky brass button that sinks when pressed. */
@Composable
fun BrassButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val shape = RoundedCornerShape(12.dp)
    val colors = if (pressed) listOf(Steam.BrassDark, Steam.Brass) else listOf(Steam.BrassLight, Steam.Brass, Steam.BrassDark)
    Box(
        modifier
            .alpha(if (enabled) 1f else 0.45f)
            .heightIn(min = 52.dp)
            .shadow(if (pressed) 1.dp else 6.dp, shape)
            .clip(shape)
            .background(Brush.verticalGradient(colors))
            .border(2.dp, Steam.Ink.copy(alpha = 0.6f), shape)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = Steam.Ink)
    }
}

/** Every app icon sits in a riveted brass porthole, so the whole device gets the same look. */
@Composable
fun Porthole(icon: ImageBitmap, modifier: Modifier = Modifier, size: Dp = 62.dp) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = this.size.minDimension / 2f
            drawCircle(Brush.radialGradient(listOf(Steam.BrassLight, Steam.Brass, Steam.BrassDark), center = c, radius = r), r, c)
            drawCircle(Steam.Soot, r * 0.80f, c)
            for (i in 0 until 8) {
                val p = polar(c, r * 0.90f, i * 45.0)
                drawCircle(Steam.BrassDark, r * 0.060f, p)
                drawCircle(Steam.BrassLight, r * 0.025f, p - Offset(r * 0.015f, r * 0.015f))
            }
        }
        Image(icon, contentDescription = null, modifier = Modifier.fillMaxSize(0.62f).clip(CircleShape))
    }
}

/** Dial gauge. value is 0..1. dangerLow puts the red zone at the low end (for a battery). */
@Composable
fun PressureGauge(value: Float, modifier: Modifier = Modifier, dangerLow: Boolean = false) {
    val v by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "needle",
    )
    Canvas(modifier.aspectRatio(1f)) {
        val c = center
        val r = size.minDimension / 2f
        drawCircle(Brush.radialGradient(listOf(Steam.BrassLight, Steam.BrassDark), center = c, radius = r), r, c)
        drawCircle(Steam.Parchment, r * 0.88f, c)
        drawArc(
            Steam.Copper,
            startAngle = if (dangerLow) 135f else 135f + 270f * 0.8f,
            sweepAngle = 270f * 0.2f, useCenter = false,
            topLeft = Offset(c.x - r * 0.8f, c.y - r * 0.8f), size = Size(r * 1.6f, r * 1.6f),
            style = Stroke(r * 0.07f),
        )
        for (i in 0..10) {
            val major = i % 5 == 0
            val deg = 135.0 + 27.0 * i
            drawLine(Steam.Ink, polar(c, r * (if (major) 0.60f else 0.68f), deg), polar(c, r * 0.74f, deg), strokeWidth = if (major) 5f else 3f)
        }
        drawLine(Steam.Copper, c, polar(c, r * 0.62f, 135.0 + 270.0 * v), strokeWidth = r * 0.04f, cap = StrokeCap.Round)
        drawCircle(Steam.BrassDark, r * 0.08f, c)
        drawCircle(Steam.BrassLight, r * 0.03f, c)
    }
}

/** Analogue clock face. hour is 0..12 (fractional), minute is 0..60 (fractional). */
@Composable
fun AnalogClock(hour: Float, minute: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(1f)) {
        val c = center
        val r = size.minDimension / 2f
        drawCircle(Brush.radialGradient(listOf(Steam.BrassLight, Steam.BrassDark), center = c, radius = r), r, c)
        drawCircle(Steam.Parchment, r * 0.9f, c)
        for (i in 0 until 60) {
            val major = i % 5 == 0
            val deg = i * 6.0 - 90.0
            drawLine(Steam.Ink, polar(c, r * (if (major) 0.72f else 0.80f), deg), polar(c, r * 0.86f, deg), strokeWidth = if (major) 5f else 2f)
        }
        drawLine(Steam.Ink, c, polar(c, r * 0.45f, hour / 12.0 * 360.0 - 90.0), strokeWidth = r * 0.075f, cap = StrokeCap.Round)
        drawLine(Steam.Copper, c, polar(c, r * 0.68f, minute / 60.0 * 360.0 - 90.0), strokeWidth = r * 0.045f, cap = StrokeCap.Round)
        drawCircle(Steam.BrassDark, r * 0.07f, c)
        drawCircle(Steam.BrassLight, r * 0.03f, c)
    }
}


/** The single floating brass gear. Tap = workshop menu, press and hold = open the archive. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SteamFab(onTap: () -> Unit, onHold: () -> Unit, modifier: Modifier = Modifier) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, label = "fab")
    val haptic = LocalHapticFeedback.current
    Box(
        modifier
            .size(72.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(10.dp, CircleShape)
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = src, indication = null, onClick = onTap,
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onHold() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f
            drawCircle(Brush.radialGradient(listOf(Steam.BrassLight, Steam.Brass, Steam.BrassDark), center = c, radius = r), r, c)
            drawCircle(Steam.Soot, r * 0.80f, c)
            for (i in 0 until 8) {
                val p = polar(c, r * 0.90f, i * 45.0)
                drawCircle(Steam.BrassDark, r * 0.060f, p)
                drawCircle(Steam.BrassLight, r * 0.025f, p - Offset(r * 0.015f, r * 0.015f))
            }
        }
        Gear(Modifier.fillMaxSize(0.62f), teeth = 10, speed = 9f)
    }
}


/** A small brass cog with a single word inside. The cog turns a notch when pressed. */
@Composable
fun CogButton(
    text: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 92.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val turn by animateFloatAsState(
        if (pressed) 15f else 0f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "cogTurn",
    )
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "cogScale")
    val teeth = 12
    Box(
        modifier
            .size(diameter)
            .alpha(if (enabled) 1f else 0.45f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = this.size.minDimension / 2f
            val body = Brush.radialGradient(listOf(Steam.BrassLight, Steam.Brass, Steam.BrassDark), center = c, radius = r)
            rotate(turn, c) {
                val toothW = (2f * Math.PI.toFloat() * r * 0.80f / teeth) * 0.55f
                for (i in 0 until teeth) {
                    rotate(i * 360f / teeth, c) {
                        drawRect(body, topLeft = Offset(c.x - toothW / 2, c.y - r), size = Size(toothW, r * 0.20f))
                    }
                }
                drawCircle(body, r * 0.86f, c)
                for (i in 0 until 6) drawCircle(Steam.BrassDark, r * 0.045f, polar(c, r * 0.78f, i * 60.0 + 30.0))
            }
            drawCircle(Steam.Soot, r * 0.72f, c)
            val face = if (pressed) listOf(Steam.Brass, Steam.BrassDark) else listOf(Steam.BrassLight, Steam.Brass)
            drawCircle(Brush.radialGradient(face, center = c, radius = r * 0.66f), r * 0.66f, c)
        }
        val fs = (diameter.value * 0.56f / (text.length * 0.62f)).coerceIn(9f, 15f)
        Text(
            text, fontSize = fs.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
            color = Steam.Ink, maxLines = 1, softWrap = false,
        )
    }
}

/** A centred, wrapping row of cogs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CogRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}


// ───────────────────────── Nixie tube clock ─────────────────────────

private val NixieOrange = Color(0xFFFFA24D)
private val NixieGlow = Color(0xFFFF6A00)

@Composable
private fun NixieTube(digit: Char, w: Dp, h: Dp) {
    val glass = RoundedCornerShape(topStart = w / 2, topEnd = w / 2, bottomStart = 6.dp, bottomEnd = 6.dp)
    val fs = with(LocalDensity.current) { (h * 0.60f).toSp() }
    Box(
        Modifier.size(w, h).clip(glass)
            .background(Brush.verticalGradient(listOf(Color(0xFF241109), Color(0xFF0B0604))))
            .border(1.5.dp, Brush.verticalGradient(listOf(Steam.Brass, Steam.BrassDark)), glass),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // faint anode mesh, a glass highlight and the brass base
            val step = size.width / 6f
            var x = step
            while (x < size.width) {
                drawLine(NixieGlow.copy(alpha = 0.10f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = step
            while (y < size.height) {
                drawLine(NixieGlow.copy(alpha = 0.07f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
            drawRect(Color.White.copy(alpha = 0.07f), topLeft = Offset(size.width * 0.12f, size.height * 0.06f), size = Size(size.width * 0.08f, size.height * 0.5f))
            drawRect(Brush.verticalGradient(listOf(Steam.Brass, Steam.BrassDark)), topLeft = Offset(0f, size.height * 0.92f), size = Size(size.width, size.height * 0.08f))
        }
        // the unlit cathodes behind the lit one
        Text("8", fontSize = fs, fontFamily = FontFamily.SansSerif, color = NixieGlow.copy(alpha = 0.14f))
        if (digit != ' ') {
            Text(
                digit.toString(), fontSize = fs, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium,
                color = NixieOrange, style = TextStyle(shadow = Shadow(NixieGlow, Offset.Zero, 24f)),
            )
        }
    }
}

@Composable
private fun NixieColon(w: Dp, h: Dp) {
    val glow = if (LocalAnimations.current) {
        val t = rememberInfiniteTransition(label = "nixieColon")
        val a by t.animateFloat(1f, 0.35f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
        a
    } else 1f
    Canvas(Modifier.size(w, h)) {
        val r = size.width * 0.14f
        for (f in listOf(0.34f, 0.66f)) {
            val c = Offset(size.width / 2f, size.height * f)
            drawCircle(NixieGlow.copy(alpha = 0.35f * glow), r * 2.6f, c)
            drawCircle(NixieOrange.copy(alpha = glow), r, c)
        }
    }
}

/** Four glowing tubes (HHMM, a space gives a dark tube) with blinking colon dots and a date line. */
@Composable
fun NixieClock(hhmm: String, date: String?, modifier: Modifier = Modifier) =
    BoxWithConstraints(modifier) {
        val mw = maxWidth
        val mh = maxHeight
        val gap = 4.dp
        val dateH = if (date != null && mh > 80.dp) 22.dp else 0.dp
        val th = maxOf(minOf(mh - dateH, (mw - gap * 4) / 2.64f), 20.dp)
        val tw = th * 0.56f
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
                NixieTube(hhmm[0], tw, th)
                NixieTube(hhmm[1], tw, th)
                NixieColon(tw * 0.4f, th)
                NixieTube(hhmm[2], tw, th)
                NixieTube(hhmm[3], tw, th)
            }
            if (dateH > 0.dp && date != null) {
                Text(date, fontSize = 13.sp, fontFamily = FontFamily.Serif, color = Steam.Parchment, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }


// ───────────────────────── Compass, moon and sun drawings ─────────────────────────

/** Brass compass. The card turns so your heading (degrees, 0 = north) sits at the top. */
@Composable
fun CompassRose(heading: Float, modifier: Modifier = Modifier) = BoxWithConstraints(modifier.aspectRatio(1f)) {
    val d = minOf(maxWidth, maxHeight)
    val fs = with(LocalDensity.current) { (d * 0.12f).toSp() }
    val inset = d * 0.15f
    Canvas(Modifier.fillMaxSize()) {
        val c = center
        val r = size.minDimension / 2f
        drawCircle(Brush.radialGradient(listOf(Steam.BrassLight, Steam.BrassDark), center = c, radius = r), r, c)
        drawCircle(Steam.Parchment, r * 0.88f, c)
    }
    Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = -heading }) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f
            for (i in 0 until 36) {
                val major = i % 3 == 0
                val deg = i * 10.0 - 90.0
                drawLine(Steam.Ink, polar(c, r * (if (major) 0.78f else 0.81f), deg), polar(c, r * 0.85f, deg), strokeWidth = if (major) 4f else 2f)
            }
            val north = Path().apply {
                moveTo(c.x, c.y - r * 0.40f); lineTo(c.x - r * 0.09f, c.y); lineTo(c.x + r * 0.09f, c.y); close()
            }
            val south = Path().apply {
                moveTo(c.x, c.y + r * 0.40f); lineTo(c.x - r * 0.09f, c.y); lineTo(c.x + r * 0.09f, c.y); close()
            }
            drawPath(north, Steam.Copper)
            drawPath(south, Steam.Ink)
            drawCircle(Steam.BrassDark, r * 0.06f, c)
            drawCircle(Steam.BrassLight, r * 0.025f, c)
        }
        Text("N", Modifier.align(Alignment.TopCenter).padding(top = inset), fontSize = fs, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Steam.Copper)
        Text("E", Modifier.align(Alignment.CenterEnd).padding(end = inset), fontSize = fs, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Steam.Ink)
        Text("S", Modifier.align(Alignment.BottomCenter).padding(bottom = inset), fontSize = fs, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Steam.Ink)
        Text("W", Modifier.align(Alignment.CenterStart).padding(start = inset), fontSize = fs, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Steam.Ink)
    }
    Canvas(Modifier.fillMaxSize()) {
        val c = center
        val r = size.minDimension / 2f
        val mark = Path().apply {
            moveTo(c.x, c.y - r * 0.80f); lineTo(c.x - r * 0.06f, c.y - r * 0.93f); lineTo(c.x + r * 0.06f, c.y - r * 0.93f); close()
        }
        drawPath(mark, Steam.Copper)
    }
}

/** The moon as it looks tonight. ageDays is days since the new moon (0 to 29.53). */
@Composable
fun MoonDisc(ageDays: Double, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(1f)) {
        val c = center
        val r = size.minDimension / 2f
        val mr = r * 0.88f
        val f = (ageDays / 29.530588853).toFloat().coerceIn(0f, 1f)
        drawCircle(Brush.radialGradient(listOf(Steam.BrassLight, Steam.BrassDark), center = c, radius = r), r, c)
        drawCircle(Color(0xFF232733), mr, c) // the dark side
        val waxing = f <= 0.5f
        val q = if (waxing) f else 1f - f // 0 = new, 0.5 = full
        val k = cos(2.0 * Math.PI * q).toFloat() // 1 = new ... -1 = full
        val rx = maxOf(abs(k) * mr, 0.5f)
        val lit = Path().apply {
            arcTo(Rect(c.x - mr, c.y - mr, c.x + mr, c.y + mr), -90f, 180f, true)
            arcTo(Rect(c.x - rx, c.y - mr, c.x + rx, c.y + mr), 90f, if (k > 0f) -180f else 180f, false)
            close()
        }
        scale(if (waxing) 1f else -1f, 1f, c) {
            clipPath(lit) {
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFFF5EFD6), Color(0xFFD9CFA8)), center = Offset(c.x - mr * 0.2f, c.y - mr * 0.2f), radius = mr * 1.4f),
                    mr, c,
                )
                val craters = listOf(
                    Triple(-0.30f, -0.25f, 0.16f), Triple(0.25f, -0.35f, 0.10f), Triple(0.10f, 0.20f, 0.20f),
                    Triple(-0.35f, 0.35f, 0.09f), Triple(0.45f, 0.30f, 0.07f),
                )
                for ((dx, dy, rr) in craters) drawCircle(Color(0x22604020), mr * rr, Offset(c.x + mr * dx, c.y + mr * dy))
            }
        }
    }
}

/** The sun's path across the sky. progress is 0 at sunrise to 1 at sunset, or null at night. */
@Composable
fun SunArc(progress: Float?, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val baseY = h * 0.82f
        val rad = minOf(w * 0.42f, baseY - 6f)
        val cx = w / 2f
        drawLine(Steam.BrassDark, Offset(cx - rad - 10f, baseY), Offset(cx + rad + 10f, baseY), strokeWidth = 3f, cap = StrokeCap.Round)
        drawArc(
            Steam.Brass, 180f, 180f, false, Offset(cx - rad, baseY - rad), Size(rad * 2f, rad * 2f),
            style = Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
        )
        if (progress != null) {
            val th = Math.PI * (1.0 - progress)
            val sx = cx + rad * cos(th).toFloat()
            val sy = baseY - rad * sin(th).toFloat()
            drawCircle(Steam.BrassLight.copy(alpha = 0.30f), 16f, Offset(sx, sy))
            drawCircle(Steam.BrassLight, 9f, Offset(sx, sy))
        }
    }
}
