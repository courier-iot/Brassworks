package com.steampunk.brassworks

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The camera that has the torch, usually the back one. Null if this phone has none. */
private fun torchCamera(cm: CameraManager): String? = try {
    val withFlash = cm.cameraIdList.filter { id ->
        cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    }
    withFlash.firstOrNull { id ->
        cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    } ?: withFlash.firstOrNull()
} catch (e: Exception) {
    null
}

/** How bright the torch can go: 1 on a phone that cannot dim it, otherwise the number of steps it offers (Android 13 and later). */
internal fun lanternMaxLevel(ctx: Context): Int {
    if (Build.VERSION.SDK_INT < 33) return 1
    return try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = torchCamera(cm) ?: return 1
        cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
    } catch (e: Exception) {
        1
    }
}

/** Puts the torch out by itself after a while. It lives outside the screen, so closing the launcher does not forget it. */
private object LanternTimer {
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    fun arm(cm: CameraManager, camId: String, minutes: Int) {
        disarm()
        val r = Runnable {
            try {
                cm.setTorchMode(camId, false)
            } catch (e: Exception) {
                // already off, or the camera is in use
            }
        }
        pending = r
        handler.postDelayed(r, minutes * 60_000L)
    }

    fun disarm() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
    }
}

/**
 * The Lantern widget: tap to switch the phone's torch on or off. It needs no permission. It follows the torch, so
 * if you switch it on from the quick settings panel the lantern lights up here too.
 */
@Composable
fun LanternContent(autoOffMin: Int = 0, levelPct: Int = 100) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val ctx = LocalContext.current
    val mw = maxWidth
    val mh = maxHeight
    val cm = remember { ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    val camId = remember { torchCamera(cm) }
    var on by remember { mutableStateOf(false) }

    DisposableEffect(camId) {
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == camId) on = enabled
            }

            override fun onTorchModeUnavailable(cameraId: String) {
                if (cameraId == camId) on = false
            }
        }
        try {
            cm.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            // no torch service
        }
        onDispose {
            try {
                cm.unregisterTorchCallback(callback)
            } catch (e: Exception) {
            }
        }
    }

    // an automatic switch-off follows the torch, however it was lit
    LaunchedEffect(on, autoOffMin, camId) {
        if (on && autoOffMin > 0 && camId != null) LanternTimer.arm(cm, camId, autoOffMin) else LanternTimer.disarm()
    }

    val maxLevel = remember { lanternMaxLevel(ctx) }
    val toggle = {
        if (camId == null) {
            Toast.makeText(ctx, "This phone has no torch.", Toast.LENGTH_SHORT).show()
        } else {
            try {
                if (!on && maxLevel > 1 && levelPct < 100 && Build.VERSION.SDK_INT >= 33) {
                    val level = (maxLevel * levelPct / 100).coerceIn(1, maxLevel)
                    cm.turnOnTorchWithStrengthLevel(camId, level)
                } else {
                    cm.setTorchMode(camId, !on)
                }
            } catch (e: Exception) {
                Toast.makeText(ctx, "The torch is busy. Try again in a moment.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val lamp = minOf(mh, mw * 0.42f)
    val metal = Steam.Brass
    val metalLight = Steam.BrassLight
    val neon = Steam.nixie.lit
    val neonGlow = Steam.nixie.glow
    val punk = Steam.punk
    val litGlass = Color(0xFFFFE9A8)
    Row(Modifier.fillMaxSize().clickable { toggle() }, verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(lamp)) {
            val w = size.width
            val h = size.height
            val glass = Rect(w * 0.28f, h * 0.26f, w * 0.72f, h * 0.80f)
            if (on) {
                drawCircle(
                    Brush.radialGradient(listOf(litGlass.copy(alpha = 0.75f), Color.Transparent), center = glass.center, radius = w * 0.62f),
                    w * 0.62f, glass.center,
                )
            }
            drawRoundRect(if (on) litGlass.copy(alpha = 0.9f) else Steam.Soot, glass.topLeft, glass.size, CornerRadius(w * 0.08f))
            if (punk && on) {
                drawRoundRect(neonGlow.copy(alpha = 0.3f), glass.topLeft, glass.size, CornerRadius(w * 0.08f), style = Stroke(w * 0.08f))
            }
            drawRoundRect(if (punk) neon else metal, glass.topLeft, glass.size, CornerRadius(w * 0.08f), style = Stroke(w * 0.035f))
            for (i in 1..2) {
                val x = glass.left + glass.width * i / 3f
                drawLine(metal.copy(alpha = 0.8f), Offset(x, glass.top), Offset(x, glass.bottom), strokeWidth = w * 0.02f)
            }
            val cap = Path().apply {
                moveTo(w * 0.24f, h * 0.26f); lineTo(w * 0.76f, h * 0.26f); lineTo(w * 0.62f, h * 0.14f); lineTo(w * 0.38f, h * 0.14f); close()
            }
            drawPath(cap, metal)
            drawArc(
                metalLight, 180f, 180f, false, Offset(w * 0.40f, h * 0.03f), Size(w * 0.20f, h * 0.22f),
                style = Stroke(w * 0.03f),
            )
            drawRoundRect(metal, Offset(w * 0.24f, h * 0.80f), Size(w * 0.52f, h * 0.07f), CornerRadius(w * 0.02f))
            if (on) {
                val flame = Path().apply {
                    moveTo(w * 0.5f, h * 0.38f)
                    cubicTo(w * 0.62f, h * 0.52f, w * 0.60f, h * 0.70f, w * 0.5f, h * 0.74f)
                    cubicTo(w * 0.40f, h * 0.70f, w * 0.38f, h * 0.52f, w * 0.5f, h * 0.38f)
                    close()
                }
                drawPath(flame, Color(0xFFFFB347))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Lantern", fontSize = 16.sp, fontFamily = Steam.font, fontWeight = FontWeight.Bold,
                color = if (punk) neon else Steam.BrassLight, maxLines = 1,
            )
            Text(
                when {
                    camId == null -> "No torch on this phone"
                    on -> "Lit. Tap to put it out" + (if (autoOffMin > 0) " (goes out after $autoOffMin min)" else "")
                    else -> "Dark. Tap to light it"
                },
                fontSize = 12.sp, fontFamily = Steam.font, color = Steam.Steam, maxLines = 2,
            )
        }
    }
}
