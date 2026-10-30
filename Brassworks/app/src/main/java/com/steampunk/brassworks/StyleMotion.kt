package com.steampunk.brassworks

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * One small piece of motion for each non-brass style, kept light on purpose.
 * Dieselpunk: now and then the neon edges flicker, like a tired tube. Corp HUD: a thin scan line sweeps down the screen
 * when you change page. Both stop when Turning gears is off, and neither does anything in Steampunk.
 */
object StyleMotion {
    /** 1 = steady. Dieselpunk panels read this while drawing, so a flicker repaints edges without recomposing anything. */
    var flicker by mutableFloatStateOf(1f)
}

/** Runs the Dieselpunk flicker while it should: home screen showing, gears allowed to move, Dieselpunk chosen. */
@Composable
fun NeonFlicker(active: Boolean, animations: Boolean, style: String) {
    LaunchedEffect(active, animations, style) {
        StyleMotion.flicker = 1f
        if (!active || !animations || style != "diesel") return@LaunchedEffect
        while (true) {
            delay((6000L..14000L).random())
            for (v in listOf(0.35f, 1f, 0.5f, 0.9f, 0.3f, 1f)) {
                StyleMotion.flicker = v
                delay(55L + (0..50).random())
            }
        }
    }
}

/** The Corp HUD scan line. Sweeps once from top to bottom each time [trigger] changes (not on the first draw). */
@Composable
fun ScanLine(trigger: Int) {
    val animate = LocalAnimations.current
    val progress = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(trigger) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        if (animate) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        }
    }
    val v = progress.value
    if (v >= 1f) return
    val glow = Steam.nixie.glow
    val lit = Steam.nixie.lit
    Canvas(Modifier.fillMaxSize()) {
        val y = size.height * v
        val trail = size.height * 0.14f
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, glow.copy(alpha = 0.16f * (1f - v))), startY = y - trail, endY = y),
            Offset(0f, y - trail), Size(size.width, trail),
        )
        drawLine(lit.copy(alpha = 0.85f * (1f - v)), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.5.dp.toPx())
    }
}
