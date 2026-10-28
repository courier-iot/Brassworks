package com.steampunk.brassworks

import android.widget.Toast
import androidx.compose.foundation.layout.*
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
)

internal fun encodeSettings(s: WidgetSettings): String =
    JSONObject().put("c", s.clock).put("f", s.fahrenheit).put("t", s.title).put("o", s.opacity.toDouble()).put("v", s.vials.joinToString(",")).toString()

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
        )
    } catch (e: Exception) {
        WidgetSettings()
    }
}

/** Widgets that show a time, so a 12 or 24 hour choice makes sense. */
internal fun WType.hasClockFormat() = this == WType.CLOCK || this == WType.NIXIE || this == WType.ANALOG || this == WType.DIGITAL || this == WType.SUN

/** Widgets that show a temperature. */
internal fun WType.hasTemperature() = this == WType.ENGINE || this == WType.VIALS

/** Widgets with a heading you can rename. Folders and link folders are renamed from inside. */
internal fun WType.hasTitle() = this == WType.BOILER || this == WType.CARGO || this == WType.BAROMETER ||
    this == WType.MEMO || this == WType.SHELF || this == WType.PICKS

internal fun WType.defaultTitle(): String = when (this) {
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

                if (w.type == WType.ENGINE || (w.type == WType.VIALS && "heat" in cur.vials)) {
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
