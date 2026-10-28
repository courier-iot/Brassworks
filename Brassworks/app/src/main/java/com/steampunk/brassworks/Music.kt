package com.steampunk.brassworks

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Having this service declared in the manifest is what lets Android list Brassworks under
 * Notification access. It does nothing itself: it only gives us the right to read which track is playing.
 */
class MediaListenerService : NotificationListenerService()

private fun listenerEnabled(ctx: Context): Boolean {
    val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
    return flat.split(":").any { ComponentName.unflattenFromString(it)?.packageName == ctx.packageName }
}

/** Opens Android's Notification access screen, where Brassworks can be switched on. */
internal fun openListenerSettings(ctx: Context) {
    try {
        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Open Android Settings, then Notifications, then Notification access", Toast.LENGTH_LONG).show()
    }
}

/** Play, pause and skip work for any music app and need no permission at all. */
private fun sendMediaKey(ctx: Context, code: Int) {
    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
    am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
}

/** What the player knows right now. The track details and progress need Notification access. */
private class MusicState {
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var playing by mutableStateOf(false)
    var progress by mutableFloatStateOf(-1f) // 0 to 1, or -1 when unknown
    var access by mutableStateOf(false)

    fun refresh(ctx: Context) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        var ctl: MediaController? = null
        access = listenerEnabled(ctx)
        if (access) {
            try {
                val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
                val list = msm.getActiveSessions(ComponentName(ctx, MediaListenerService::class.java))
                ctl = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
            } catch (e: SecurityException) {
                access = false
            }
        }
        if (ctl == null) {
            title = ""
            artist = ""
            progress = -1f
            playing = am.isMusicActive
            return
        }
        val md = ctl.metadata
        title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val ps = ctl.playbackState
        playing = ps?.state == PlaybackState.STATE_PLAYING
        val dur = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        progress = if (ps != null && dur > 0L) {
            var pos = ps.position
            if (playing) pos += ((SystemClock.elapsedRealtime() - ps.lastPositionUpdateTime) * ps.playbackSpeed).toLong()
            (pos.toFloat() / dur).coerceIn(0f, 1f)
        } else {
            -1f
        }
    }
}

/**
 * A neon record player. Wide and short it becomes a strip, taller and narrower it becomes a square card,
 * so resize the widget to change its shape. The neon colour follows the nixie colour in Settings.
 */
@Composable
fun MusicContent() {
    val ctx = LocalContext.current
    val active = LocalActive.current
    val scope = rememberCoroutineScope()
    val st = remember { MusicState() }

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            st.refresh(ctx)
            delay(if (st.playing) 1000L else 2500L)
        }
    }

    val lit = Steam.nixie.lit
    val trackKey = st.title + "|" + st.artist
    // a fresh random tint for the record every time the track changes
    val tint = remember(trackKey) { if (trackKey == "|") lit else Color(android.graphics.Color.HSVToColor(floatArrayOf(Random.nextFloat() * 360f, 0.6f, 0.95f))) }

    val press: (Int) -> Unit = { code ->
        sendMediaKey(ctx, code)
        scope.launch {
            delay(350)
            st.refresh(ctx)
        }
    }
    val onPrev = { press(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
    val onPlay = { press(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
    val onNext = { press(KeyEvent.KEYCODE_MEDIA_NEXT) }
    val onAllow = { openListenerSettings(ctx) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val strip = maxWidth >= 230.dp && maxWidth > maxHeight * 1.5f
        if (strip) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                RecordArt(Modifier.size(minOf(maxHeight, maxWidth * 0.3f)), st.playing, tint)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    TrackText(st, lit, true, false, 16f, onAllow)
                    Spacer(Modifier.height(6.dp))
                    ProgressLine(st.progress, lit)
                    Spacer(Modifier.height(6.dp))
                    ControlsRow(st.playing, Arrangement.Start, onPrev, onPlay, onNext)
                }
            }
        } else {
            val tall = maxHeight >= 125.dp
            val textH = if (tall) 36.dp else 20.dp
            val art = maxOf(minOf(maxHeight - 28.dp - textH - 4.dp - 18.dp, maxWidth), 30.dp)
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                RecordArt(Modifier.size(art), st.playing, tint)
                TrackText(st, lit, tall, true, 14f, onAllow)
                ProgressLine(st.progress, lit)
                ControlsRow(st.playing, Arrangement.Center, onPrev, onPlay, onNext)
            }
        }
    }
}

@Composable
private fun TrackText(st: MusicState, lit: Color, showArtist: Boolean, center: Boolean, size: Float, onAllow: () -> Unit) {
    val glow = Steam.nixie.glow
    val align = if (center) TextAlign.Center else TextAlign.Start
    val head: String
    val sub: String
    when {
        !st.access -> { head = "Now playing"; sub = "" }
        st.title.isBlank() -> { head = "Nothing playing"; sub = "Press play to start" }
        else -> { head = st.title; sub = st.artist }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        Text(
            head, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = align,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = size.sp,
                shadow = Shadow(glow.copy(alpha = 0.85f), Offset.Zero, 14f),
            ),
            color = if (st.access) lit else Steam.Parchment,
        )
        if (!st.access) {
            Text(
                "Tap to show track info", maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = align,
                modifier = Modifier.clickable(onClick = onAllow),
                style = MaterialTheme.typography.labelLarge, fontSize = (size - 3f).coerceAtLeast(11f).sp,
                color = lit, textDecoration = TextDecoration.Underline,
            )
        } else if (showArtist && sub.isNotBlank()) {
            Text(
                sub, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = align,
                style = MaterialTheme.typography.labelLarge, fontSize = (size - 3f).coerceAtLeast(11f).sp,
                color = Steam.Parchment,
            )
        }
    }
}

/** A thin neon line that fills as the track plays. It only shows progress, it is not a seek bar. */
@Composable
private fun ProgressLine(progress: Float, lit: Color) {
    val shown by animateFloatAsState(progress.coerceAtLeast(0f), tween(900, easing = LinearEasing), label = "track")
    val glow = Steam.nixie.glow
    Box(Modifier.fillMaxWidth().height(4.dp)) {
        if (progress >= 0f) {
            Canvas(Modifier.fillMaxSize()) {
                val h = size.height
                val y = h / 2f
                drawLine(lit.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width, y), strokeWidth = h * 0.5f, cap = StrokeCap.Round)
                if (shown > 0.005f) {
                    val x = maxOf(h, size.width * shown)
                    drawLine(glow.copy(alpha = 0.3f), Offset(0f, y), Offset(x, y), strokeWidth = h, cap = StrokeCap.Round)
                    drawLine(lit, Offset(0f, y), Offset(x, y), strokeWidth = h * 0.5f, cap = StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
private fun ControlsRow(playing: Boolean, arrangement: Arrangement.Horizontal, onPrev: () -> Unit, onPlay: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = arrangement, verticalAlignment = Alignment.CenterVertically) {
        MusicButton(0, playing, 28.dp, onPrev)
        Spacer(Modifier.width(10.dp))
        MusicButton(1, playing, 36.dp, onPlay)
        Spacer(Modifier.width(10.dp))
        MusicButton(2, playing, 28.dp, onNext)
    }
}

/** kind 0 = previous, 1 = play or pause (it shows what a tap will do), 2 = next. */
@Composable
private fun MusicButton(kind: Int, playing: Boolean, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val lit = Steam.nixie.lit
    val glow = Steam.nixie.glow
    Canvas(Modifier.size(size).clip(CircleShape).clickable(onClick = onClick)) {
        val w = this.size.width
        val line = 2.dp.toPx()
        fun neon(path: Path) {
            drawPath(path, glow.copy(alpha = 0.3f), style = Stroke(line * 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, lit, style = Stroke(line, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        fun tri(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float) = Path().apply {
            moveTo(ax * w, ay * w); lineTo(bx * w, by * w); lineTo(cx * w, cy * w); close()
        }
        fun bar(x: Float, y1: Float, y2: Float) = Path().apply { moveTo(x * w, y1 * w); lineTo(x * w, y2 * w) }
        when (kind) {
            0 -> {
                neon(bar(0.28f, 0.3f, 0.7f))
                neon(tri(0.72f, 0.3f, 0.38f, 0.5f, 0.72f, 0.7f))
            }
            2 -> {
                neon(tri(0.28f, 0.3f, 0.62f, 0.5f, 0.28f, 0.7f))
                neon(bar(0.72f, 0.3f, 0.7f))
            }
            else -> {
                neon(Path().apply { addOval(androidx.compose.ui.geometry.Rect(0.06f * w, 0.06f * w, 0.94f * w, 0.94f * w)) })
                if (playing) {
                    neon(bar(0.4f, 0.33f, 0.67f))
                    neon(bar(0.6f, 0.33f, 0.67f))
                } else {
                    neon(tri(0.42f, 0.32f, 0.7f, 0.5f, 0.42f, 0.68f))
                }
            }
        }
    }
}

/** The neon-framed record. It spins while music plays and its label takes the tint chosen for the current track. */
@Composable
private fun RecordArt(modifier: Modifier, playing: Boolean, tint: Color) {
    val spin = playing && LocalAnimations.current
    val angle: State<Float>? = if (spin) {
        rememberInfiniteTransition(label = "record").animateFloat(
            0f, 360f, infiniteRepeatable(tween(3200, easing = LinearEasing)), label = "spin",
        )
    } else {
        null
    }
    val lit = Steam.nixie.lit
    val glow = Steam.nixie.glow
    Canvas(modifier) {
        drawRecordFrame(lit, glow, tint, angle?.value ?: 0f)
    }
}

private fun DrawScope.drawRecordFrame(lit: Color, glow: Color, tint: Color, angle: Float) {
    val w = size.width
    val h = size.height
    val rim = 2.5.dp.toPx()
    val corner = CornerRadius(6.dp.toPx())
    val frameTL = Offset(rim, rim)
    val frameSize = Size(w - 2f * rim, h - 2f * rim)
    drawRoundRect(glow.copy(alpha = 0.22f), frameTL, frameSize, corner, style = Stroke(rim * 3f))
    drawRoundRect(Steam.Soot, frameTL, frameSize, corner)
    drawRoundRect(lit, frameTL, frameSize, corner, style = Stroke(rim))

    val c = Offset(w / 2f, h / 2f)
    val r = minOf(w, h) * 0.38f
    drawCircle(tint.copy(alpha = 0.14f), r, c)
    drawCircle(glow.copy(alpha = 0.22f), r, c, style = Stroke(rim * 2.4f))
    drawCircle(lit, r, c, style = Stroke(2.dp.toPx()))
    drawCircle(lit.copy(alpha = 0.35f), r * 0.82f, c, style = Stroke(1.dp.toPx()))
    drawCircle(lit.copy(alpha = 0.35f), r * 0.66f, c, style = Stroke(1.dp.toPx()))
    rotate(angle, c) {
        // a bright arc and a dot on the label, so you can see the record turn
        drawArc(
            Color.White.copy(alpha = 0.35f), 200f, 50f, false,
            Offset(c.x - r * 0.82f, c.y - r * 0.82f), Size(r * 1.64f, r * 1.64f),
            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
        )
        drawCircle(tint, r * 0.34f, c)
        drawCircle(Steam.Soot, r * 0.07f, c)
        drawCircle(Color.White.copy(alpha = 0.85f), r * 0.045f, Offset(c.x + r * 0.22f, c.y))
    }
}
