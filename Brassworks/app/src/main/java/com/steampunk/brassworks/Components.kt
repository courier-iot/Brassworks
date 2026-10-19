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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            animationSpec = infiniteRepeatable(tween((speed * 1000).toInt(), easing = LinearEasing)),
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
