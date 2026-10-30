package com.steampunk.brassworks

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import org.json.JSONObject
import kotlin.math.roundToInt

/** Settings that belong to one widget only. The defaults mean "behave as before". */
data class WidgetSettings(
    val clock: Int = 0,             // 0 = follow the phone, 12 or 24
    val fahrenheit: Boolean = false,
    val title: String = "",         // blank = the widget's own title
    val opacity: Float = 1f,        // 0.2 to 1: how solid the panel behind the widget is
    val vials: List<String> = DEFAULT_VIALS, // Vials widget: which readings, in order
    val vialColors: Map<String, Int> = emptyMap(), // Vials widget: own liquid colour per reading (ARGB). Missing = automatic
    val tone: String = "",          // Countdown: ringtone (a content address), blank = the phone's alarm sound
    val lanternMin: Int = 0,        // Lantern: switch off by itself after this many minutes, 0 = never
    val lanternLevel: Int = 100,    // Lantern: brightness in percent, on phones that can dim the torch
    val player: String = "",        // Music player: package of the app to follow, blank = whichever is playing
)

internal fun encodeSettings(s: WidgetSettings): String {
    val vc = JSONObject()
    s.vialColors.forEach { (k, v) -> vc.put(k, v) }
    return JSONObject().put("c", s.clock).put("f", s.fahrenheit).put("t", s.title).put("o", s.opacity.toDouble())
        .put("v", s.vials.joinToString(",")).put("vc", vc).put("p", s.player).put("tn", s.tone).put("lm", s.lanternMin).put("ll", s.lanternLevel).toString()
}

internal fun decodeSettings(raw: String?): WidgetSettings {
    if (raw == null) return WidgetSettings()
    return try {
        val o = JSONObject(raw)
        WidgetSettings(
            clock = o.optInt("c", 0).let { if (it == 12 || it == 24) it else 0 },
            fahrenheit = o.optBoolean("f", false),
            title = o.optString("t", ""),
            opacity = o.optDouble("o", 1.0).toFloat().coerceIn(0.2f, 1f),
            vials = o.optString("v", "").split(",")
                .filter { k -> VIAL_METRICS.any { it.key == k } }.distinct().take(MAX_VIALS)
                .ifEmpty { DEFAULT_VIALS },
            vialColors = o.optJSONObject("vc")?.let { vc ->
                vc.keys().asSequence().filter { k -> VIAL_METRICS.any { it.key == k } }.associateWith { vc.optInt(it, 0) }
            }.orEmpty(),
            player = o.optString("p", "").take(150),
            tone = o.optString("tn", "").take(400),
            lanternMin = o.optInt("lm", 0).let { if (it in listOf(2, 5, 10, 30)) it else 0 },
            lanternLevel = o.optInt("ll", 100).coerceIn(10, 100),
        )
    } catch (e: Exception) {
        WidgetSettings()
    }
}

/** Widgets that show a time, so a 12 or 24 hour choice makes sense. */
internal fun WType.hasClockFormat() = this == WType.CLOCK || this == WType.NIXIE || this == WType.ANALOG || this == WType.DIGITAL || this == WType.SUN ||
    this == WType.TIMER || this == WType.AGENDA

/** Widgets that show a temperature. */
internal fun WType.hasTemperature() = this == WType.ENGINE || this == WType.VIALS || this == WType.WEATHER

/** Widgets with a heading you can rename. Folders and link folders are renamed from inside. */
internal fun WType.hasTitle() = this == WType.BOILER || this == WType.CARGO || this == WType.BAROMETER ||
    this == WType.MEMO || this == WType.SHELF || this == WType.PICKS || this == WType.AGENDA || this == WType.WEATHER

internal fun WType.defaultTitle(): String = when (this) {
    WType.WEATHER -> "Weather"
    WType.AGENDA -> "Agenda"
    WType.BOILER -> "Boiler pressure"
    WType.CARGO -> "Cargo hold"
    WType.BAROMETER -> "Barometer"
    WType.MEMO -> "Memorandum"
    WType.SHELF -> "App shelf"
    WType.PICKS -> "Chief Engineer's picks"
    else -> title
}

/** The cog-wheel pop-up shown in Arrange mode. Every change is saved straight away. */
@Composable
fun WidgetSettingsDialog(w: WidgetItem, board: WidgetBoard, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val cur = board.settingsOf(w.id)
    var title by remember(w.id) { mutableStateOf(cur.title) }
    fun change(f: (WidgetSettings) -> WidgetSettings) = board.setSettings(w.id, f(board.settingsOf(w.id)))

    Dialog(onDismissRequest = onClose) {
        RivetedPanel(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(w.type.title.uppercase(), style = MaterialTheme.typography.titleLarge, color = Steam.BrassLight)
                Text("These settings belong to this widget only.", style = MaterialTheme.typography.labelLarge, color = Steam.Steam)

                if (w.type.hasClockFormat()) {
                    Text("Hour format", style = MaterialTheme.typography.titleMedium, color = Steam.Parchment)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpeedChip("Phone", cur.clock == 0, Modifier.weight(1f)) { change { it.copy(clock = 0) } }
                        SpeedChip("12-hour", cur.clock == 12, Modifier.weight(1f)) { change { it.copy(clock = 12) } }
                        SpeedChip("24-hour", cur.clock == 24, Modifier.weight(1f)) { change { it.copy(clock = 24) } }
                    }
                }

                if (w.type == WType.MUSIC) {
                    Text("Neon colour", style = MaterialTheme.typography.titleMedium, color = Steam.Parchment)
                    Text(
                        "The neon follows your nixie colour. Change it in Settings > Look > Nixie glow. " +
                            "Resize the widget to change its shape: wide and short is a strip, taller and narrower is a square card.",
                        style = MaterialTheme.typography.labelLarge, color = Steam.Steam,
                    )
                    Text("Track info", style = MaterialTheme.typography.titleMedium, color = Steam.Parchment)
                    Text(
                        "Play, pause, next and previous always work. The track name, artist and progress line need Notification access, " +
                            "which Android asks you to switch on once. On some phones you may first need to allow restricted settings " +
                            "for Brassworks in its App info.",
                        style = MaterialTheme.typography.labelLarge, color = Steam.Steam,
                    )
                    CogRow { CogButton("Notification access") { openListenerSettings(ctx) } }
                    Text("Player to follow", style = MaterialTheme.typography.titleMedium, color = Steam.Parchment)
                    Text(
                        "Automatic follows whichever app is playing. Pick one to stay with that app even when others are running. " +
                            "Only apps that are playing right now are listed, so start the one you want first. Needs Notification access.",
                        style = MaterialTheme.typography.labelLarge, color = Steam.Steam,
                    )
                    val players = remember { activePlayers(ctx) }
                    val choices = buildList {
                        add("" to "Automatic")
                        addAll(players)
                        if (cur.player.isNotBlank() && players.none { it.first == cur.player }) add(cur.player to playerName(ctx, cur.player))
                    }
                    choices.chunked(2).forEach { rowOf ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowOf.forEach { (pkg, name) ->
                                SpeedChip(name, cur.player == pkg, Modifier.weight(1f)) { change { it.copy(player = pkg) } }
                            }
                            if (rowOf.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }

                if (w.type == WType.TIMER) {
                    Text("Ringtone", style = MaterialTheme.typography.titleMedium, color = Steam.Parchment)
                    val pickTone = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
                        if (res.resultCode == Activity.RESULT_OK) {
                            val picked: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                                res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                res.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                            }
                            change { it.copy(tone = picked?.toString() ?: "") }
                        }
                    }
                    val toneName = remember(cur.tone) {
                        if (cur.tone.isBlank()) "The phone's alarm sound" else try {
                            RingtoneManager.getRingtone(ctx, Uri.parse(cur.tone))?.getTitle(ctx) ?: "Your choice"
                        } catch (e: Exception) {
                            "Your choice"
                        }
                    }
                    Text(
                        "$toneName. Used by every countdown and the alarm in this widget. " +
                            "A countdown has three slots: tap 1, 2 or 3 beside its title to switch. A number with a dot is running.",
                        style = MaterialTheme.typography.labelLarge, color = Steam.Steam,
                    )
                    CogRow {
                        CogButton("Choose") {
                            val ask = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Countdown ringtone")
                            if (cur.tone.isNotBlank()) ask.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(cur.tone))
                            try {
                                pickTone.launch(ask)
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "No ringtone chooser on this phone.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        if (cur.tone.isNotBlank()) CogButton("Default") { change { it.copy(tone = "") } }
                    }
                }

                if (w.type == WType.LANTERN) {
                    Text("Switch off by itself", style = MaterialTheme.typography.titleMedium, color = Steam.Parchment)
                    Text(
                        "Puts the torch out after this long, so it cannot drain the battery in a pocket.",
                        style = MaterialTheme.typography.labelLarge, color = Steam.Steam,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Never", 2 to "2 min", 5 to "5 min").forEach { (m, label) ->
                            SpeedChip(label, cur.lanternMin == m, Modifier.weight(1f)) { change { it.copy(lanternMin = m) } }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10 to "10 min", 30 to "30 min").forEach { (m, label) ->
                            SpeedChip(label, cur.lanternMin == m, Modifier.weight(1f)) { change { it.copy(lanternMin = m) } }
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    Text("Brightness", style = MaterialTheme.typography.titleMedium, color = Steam.Parchment)
                    if (remember { lanternMaxLevel(ctx) } > 1) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(25 to "Low", 60 to "Medium", 100 to "Full").forEach { (p, label) ->
                                SpeedChip(label, cur.lanternLevel == p, Modifier.weight(1f)) { change { it.copy(lanternLevel = p) } }
                            }
                        }
                        Text("Takes effect the next time you light it.", style = MaterialTheme.typography.labelLarge, color = Steam.Steam)
                    } else {
                        Text(
                            "This phone cannot dim its torch, or has an Android older than 13, so it always lights at full brightness.",
                            style = MaterialTheme.typography.labelLarge, color = Steam.Steam,
                        )
                    }
                }

                if (w.type == WType.VIALS) {
                    Text("Readings", style = MaterialTheme.typography.titleMedium, color = Steam.Parchment)
                    Text(
                        "Tap to add or remove a vial. They appear in the order you pick them. Choose 1 to $MAX_VIALS.",
                        style = MaterialTheme.typography.labelLarge, color = Steam.Steam,
                    )
                    VIAL_METRICS.chunked(3).forEach { rowOf ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowOf.forEach { m ->
                                SpeedChip(m.label, m.key in cur.vials, Modifier.weight(1f)) {
                                    change { s ->
                                        val now = board.settingsOf(w.id).vials
                                        s.copy(
                                            vials = when {
                                                m.key in now -> if (now.size > 1) now - m.key else now // keep at least one
                                                now.size < MAX_VIALS -> now + m.key
                                                else -> now
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (w.type == WType.VIALS) {
                    Text("Liquid colour", style = MaterialTheme.typography.titleMedium, color = Steam.Parchment)
                    Text(
                        "Auto turns amber, then copper, as a level gets worrying. Pick a colour to keep a vial the same. Tap a vial on the home screen to see its exact figure.",
                        style = MaterialTheme.typography.labelLarge, color = Steam.Steam,
                    )
                    cur.vials.forEach { key ->
                        val metric = VIAL_METRICS.firstOrNull { it.key == key } ?: return@forEach
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(metric.label, Modifier.width(64.dp), style = MaterialTheme.typography.labelLarge, color = Steam.Parchment, maxLines = 1)
                            VIAL_COLOURS.forEach { (name, argb) ->
                                val chosen = cur.vialColors[key] == argb
                                val swatch = if (argb == null) Steam.Soot else Color(argb)
                                Box(
                                    Modifier.size(28.dp).clip(CircleShape).background(swatch)
                                        .border(if (chosen) 3.dp else 1.dp, if (chosen) Steam.BrassLight else Steam.Steam, CircleShape)
                                        .clickable {
                                            change { s ->
                                                s.copy(vialColors = if (argb == null) s.vialColors - key else s.vialColors + (key to argb))
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) { if (argb == null) Text("A", color = Steam.Parchment, style = MaterialTheme.typography.labelLarge) }
                            }
                        }
                    }
                }

                if (w.type == WType.ENGINE || w.type == WType.WEATHER || (w.type == WType.VIALS && "heat" in cur.vials)) {
                    Text("Temperature", style = MaterialTheme.typography.titleMedium, color = Steam.Parchment)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SpeedChip("Celsius", !cur.fahrenheit, Modifier.weight(1f)) { change { it.copy(fahrenheit = false) } }
                        SpeedChip("Fahrenheit", cur.fahrenheit, Modifier.weight(1f)) { change { it.copy(fahrenheit = true) } }
                    }
                }

                if (w.type.hasTitle()) {
                    Text("Title", style = MaterialTheme.typography.titleMedium, color = Steam.Parchment)
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(40); change { s -> s.copy(title = title) } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(w.type.defaultTitle()) },
                        colors = steamFieldColors(),
                    )
                }

                Text("Opacity " + (cur.opacity * 100f).roundToInt() + "%", style = MaterialTheme.typography.titleMedium, color = Steam.Parchment)
                Text(
                    "How solid the panel behind the widget is. Lower it to let your wallpaper show through.",
                    style = MaterialTheme.typography.labelLarge, color = Steam.Steam,
                )
                Slider(
                    value = cur.opacity,
                    onValueChange = { v -> change { it.copy(opacity = v) } },
                    valueRange = 0.2f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Steam.BrassLight, activeTrackColor = Steam.Brass, inactiveTrackColor = Steam.Soot,
                    ),
                )

                if (board.pageCount > 1) {
                    Text("Move to page", style = MaterialTheme.typography.titleMedium, color = Steam.Parchment)
                    CogRow {
                        for (p in 0 until board.pageCount) {
                            if (p != board.page) {
                                CogButton("Page ${p + 1}") {
                                    if (board.moveToPage(w.id, p)) {
                                        onClose()
                                    } else {
                                        Toast.makeText(ctx, "No room left on page ${p + 1}.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }
                }

                CogRow {
                    CogButton("Reset") {
                        title = ""
                        board.setSettings(w.id, WidgetSettings())
                    }
                    CogButton("Done", onClick = onClose)
                }
            }
        }
    }
}
