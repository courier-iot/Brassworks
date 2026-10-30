package com.steampunk.brassworks

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * Sound effects and steam puffs. Both are off until you switch them on in Settings > Look.
 *
 * The sounds are short files kept in the app's assets/sounds folder. If a file is missing the effect is simply silent,
 * so nothing can go wrong. They use the system's own sound volume, so silent mode silences them too.
 */
object Sfx {
    enum class Kind(val file: String) {
        TICK("tick.wav"),         // opening an app
        GEAR("gear.wav"),         // the gear, opening the Archive
        HISS("hiss.wav"),         // the Workshop menu
        RELEASE("release.wav"),   // entering Arrange mode
        DROP("drop.wav"),         // putting a widget down
        WHOOSH("whoosh.wav"),     // changing page
        BELL("bell.wav"),         // something saved or applied
    }

    var enabled by mutableStateOf(false)
        private set
    var puffs by mutableStateOf(false)
        private set
    var volumePct by mutableIntStateOf(65)
        private set

    private var pool: SoundPool? = null
    private val ids = HashMap<Kind, Int>()
    private val ready = HashSet<Int>()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("brassworks", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        val p = prefs(ctx)
        enabled = p.getBoolean("sfx_on", false)
        puffs = p.getBoolean("sfx_puffs", false)
        volumePct = p.getInt("sfx_vol", 65).coerceIn(10, 100)
        if (enabled) start(ctx)
    }

    fun setEnabled(ctx: Context, on: Boolean) {
        enabled = on
        prefs(ctx).edit().putBoolean("sfx_on", on).apply()
        if (on) {
            start(ctx)
            play(Kind.GEAR) // so you can hear that it works
        } else {
            stop()
        }
    }

    fun setPuffs(ctx: Context, on: Boolean) {
        puffs = on
        prefs(ctx).edit().putBoolean("sfx_puffs", on).apply()
    }

    fun setVolume(ctx: Context, pct: Int) {
        volumePct = pct.coerceIn(10, 100)
        prefs(ctx).edit().putInt("sfx_vol", volumePct).apply()
        play(Kind.TICK)
    }

    private fun start(ctx: Context) {
        if (pool != null) return
        try {
            val sp = SoundPool.Builder()
                .setMaxStreams(3)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .build()
            sp.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) ready.add(sampleId) }
            for (k in Kind.values()) {
                try {
                    ctx.applicationContext.assets.openFd("sounds/" + k.file).use { fd -> ids[k] = sp.load(fd, 1) }
                } catch (e: Exception) {
                    // this sound file is not there, so that effect stays silent
                }
            }
            pool = sp
        } catch (e: Exception) {
            pool = null
        }
    }

    private fun stop() {
        try {
            pool?.release()
        } catch (e: Exception) {
            // nothing to do
        }
        pool = null
        ids.clear()
        ready.clear()
    }

    fun play(kind: Kind) {
        if (!enabled) return
        val id = ids[kind] ?: return
        if (id !in ready) return
        val v = volumePct / 100f
        try {
            pool?.play(id, v, v, 1, 0, 1f)
        } catch (e: Exception) {
            // a missed click is not worth a crash
        }
    }
}

/**
 * A few puffs of steam that rise and fade. Draws upward from where it sits, so put it on the gear. Plays each time [key]
 * changes, if steam puffs are switched on and the gears are allowed to move.
 */
@Composable
fun SteamPuffs(key: Int, modifier: Modifier = Modifier) {
    val animate = LocalAnimations.current
    val progress = remember { Animatable(1f) }
    LaunchedEffect(key) {
        if (key > 0 && Sfx.puffs && animate) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(1300, easing = LinearEasing))
        }
    }
    val p = progress.value
    if (p >= 1f) return
    val tone = if (Steam.punk) Steam.nixie.lit else Steam.Parchment
    Canvas(modifier.size(72.dp)) {
        val s = size.width
        for (i in 0 until 8) {
            val seed = i * 1.37f
            val lag = (i % 4) * 0.08f
            val q = ((p - lag) / (1f - lag)).coerceIn(0f, 1f)
            if (q <= 0f) continue
            val x = s * (0.5f + 0.18f * sin(seed * 2.3f) + 0.22f * sin(seed + q * 3f) * q)
            val y = s * 0.2f - q * s * (1.2f + 0.25f * (i % 3))
            val r = s * (0.10f + 0.26f * q) * (0.8f + 0.1f * (i % 3))
            val a = (1f - q) * (1f - q) * 0.45f
            drawCircle(Color(tone.red, tone.green, tone.blue, a), r, Offset(x, y))
        }
    }
}
