package com.steampunk.brassworks

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Weather widget. It asks open-meteo.com, a free weather service that needs no account or key, for the weather at
 * your rough location. Only the rounded latitude and longitude are sent (to about 1 km), nothing else. The last answer
 * is kept, so the widget still shows something when you have no signal. Weather data by Open-Meteo.com (CC BY 4.0).
 */
private class DayForecast(val date: LocalDate, val code: Int, val high: Float, val low: Float)

private class WeatherNow(val temp: Float, val feels: Float, val code: Int, val day: Boolean, val wind: Float, val days: List<DayForecast>)

/** Words for the WMO weather codes the service uses. */
private fun weatherName(code: Int): String = when (code) {
    0 -> "Clear"
    1 -> "Mostly clear"
    2 -> "Part cloud"
    3 -> "Overcast"
    45, 48 -> "Fog"
    51, 53, 55 -> "Drizzle"
    56, 57 -> "Freezing drizzle"
    61, 63, 65 -> "Rain"
    66, 67 -> "Freezing rain"
    71, 73, 75, 77 -> "Snow"
    80, 81, 82 -> "Showers"
    85, 86 -> "Snow showers"
    95 -> "Thunderstorm"
    96, 99 -> "Thunder and hail"
    else -> "Unsettled"
}

private fun parseWeather(json: String): WeatherNow? = try {
    val root = JSONObject(json)
    val cur = root.getJSONObject("current")
    val daily = root.getJSONObject("daily")
    val dates = daily.getJSONArray("time")
    val codes = daily.getJSONArray("weather_code")
    val highs = daily.getJSONArray("temperature_2m_max")
    val lows = daily.getJSONArray("temperature_2m_min")
    val days = (0 until dates.length()).map { i ->
        DayForecast(LocalDate.parse(dates.getString(i)), codes.getInt(i), highs.getDouble(i).toFloat(), lows.getDouble(i).toFloat())
    }
    WeatherNow(
        cur.getDouble("temperature_2m").toFloat(), cur.getDouble("apparent_temperature").toFloat(), cur.getInt("weather_code"),
        cur.getInt("is_day") == 1, cur.getDouble("wind_speed_10m").toFloat(), days,
    )
} catch (e: Exception) {
    null
}

/** Asks the service. Run this off the main thread. Returns the raw answer, or null if it could not be had. */
private fun fetchWeather(lat: Double, lon: Double): String? = try {
    val url = "https://api.open-meteo.com/v1/forecast?latitude=" + "%.2f".format(Locale.US, lat) + "&longitude=" + "%.2f".format(Locale.US, lon) +
        "&current=temperature_2m,apparent_temperature,weather_code,is_day,wind_speed_10m" +
        "&daily=weather_code,temperature_2m_max,temperature_2m_min&forecast_days=4&timezone=auto"
    val c = URL(url).openConnection() as HttpURLConnection
    c.connectTimeout = 8000
    c.readTimeout = 8000
    c.requestMethod = "GET"
    try {
        if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
    } finally {
        c.disconnect()
    }
} catch (e: Exception) {
    null
}

private fun degrees(c: Float, fahrenheit: Boolean): String =
    (if (fahrenheit) (c * 9f / 5f + 32f).roundToInt() else c.roundToInt()).toString() + "°"

/** A small weather picture drawn from shapes, in the colours of the theme. */
private fun DrawScope.glyph(code: Int, day: Boolean) {
    val w = size.width
    val h = size.height
    val punk = Steam.punk
    val sun = if (punk) Steam.nixie.lit else Steam.BrassLight
    val sunGlow = if (punk) Steam.nixie.glow else Steam.Brass
    val cloud = if (punk) Steam.BrassLight else Steam.Parchment
    val cloudEdge = if (punk) Steam.Brass else Steam.BrassDark
    val kind = when (code) {
        0, 1 -> 0                     // clear
        2 -> 1                        // part cloud
        3 -> 2                        // overcast
        45, 48 -> 3                   // fog
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> 4 // rain
        71, 73, 75, 77, 85, 86 -> 5   // snow
        95, 96, 99 -> 6               // storm
        else -> 2
    }
    fun drawSun(cx: Float, cy: Float, r: Float) {
        if (!day) {
            // a crescent for the night
            drawCircle(sun, r, Offset(cx, cy))
            drawCircle(Steam.Soot, r * 0.85f, Offset(cx + r * 0.45f, cy - r * 0.2f))
            return
        }
        drawCircle(sunGlow.copy(alpha = 0.35f), r * 1.35f, Offset(cx, cy))
        drawCircle(sun, r, Offset(cx, cy))
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            drawLine(
                sun, Offset(cx + (r * 1.5f * cos(a)).toFloat(), cy + (r * 1.5f * sin(a)).toFloat()),
                Offset(cx + (r * 1.85f * cos(a)).toFloat(), cy + (r * 1.85f * sin(a)).toFloat()),
                strokeWidth = r * 0.18f, cap = StrokeCap.Round,
            )
        }
    }
    fun drawCloud(cx: Float, cy: Float, s: Float) {
        val p = Path().apply {
            moveTo(cx - s, cy + s * 0.5f)
            lineTo(cx + s, cy + s * 0.5f)
            cubicTo(cx + s * 1.5f, cy + s * 0.5f, cx + s * 1.5f, cy - s * 0.2f, cx + s * 0.8f, cy - s * 0.2f)
            cubicTo(cx + s * 0.8f, cy - s * 0.9f, cx - s * 0.1f, cy - s * 1.0f, cx - s * 0.2f, cy - s * 0.3f)
            cubicTo(cx - s * 0.7f, cy - s * 0.5f, cx - s * 1.3f, cy - s * 0.1f, cx - s * 1.0f, cy + s * 0.2f)
            cubicTo(cx - s * 1.5f, cy + s * 0.3f, cx - s * 1.3f, cy + s * 0.5f, cx - s, cy + s * 0.5f)
            close()
        }
        drawPath(p, cloud)
        drawPath(p, cloudEdge, style = Stroke(s * 0.08f))
    }
    when (kind) {
        0 -> drawSun(w * 0.5f, h * 0.5f, minOf(w, h) * 0.22f)
        1 -> { drawSun(w * 0.38f, h * 0.38f, minOf(w, h) * 0.17f); drawCloud(w * 0.55f, h * 0.58f, minOf(w, h) * 0.26f) }
        2 -> drawCloud(w * 0.5f, h * 0.5f, minOf(w, h) * 0.30f)
        3 -> for (i in 0 until 4) {
            drawLine(cloud.copy(alpha = 0.85f), Offset(w * 0.2f, h * (0.28f + i * 0.15f)), Offset(w * 0.8f, h * (0.28f + i * 0.15f)), strokeWidth = h * 0.07f, cap = StrokeCap.Round)
        }
        4 -> {
            drawCloud(w * 0.5f, h * 0.38f, minOf(w, h) * 0.26f)
            for (i in 0 until 3) {
                val x = w * (0.34f + i * 0.16f)
                drawLine(Steam.Verdigris, Offset(x, h * 0.68f), Offset(x - w * 0.05f, h * 0.88f), strokeWidth = h * 0.06f, cap = StrokeCap.Round)
            }
        }
        5 -> {
            drawCloud(w * 0.5f, h * 0.38f, minOf(w, h) * 0.26f)
            for (i in 0 until 3) drawCircle(Color.White, h * 0.045f, Offset(w * (0.34f + i * 0.16f), h * (0.72f + (i % 2) * 0.1f)))
        }
        else -> {
            drawCloud(w * 0.5f, h * 0.36f, minOf(w, h) * 0.26f)
            val bolt = Path().apply {
                moveTo(w * 0.54f, h * 0.6f); lineTo(w * 0.42f, h * 0.78f); lineTo(w * 0.52f, h * 0.78f); lineTo(w * 0.46f, h * 0.95f)
                lineTo(w * 0.62f, h * 0.72f); lineTo(w * 0.52f, h * 0.72f); close()
            }
            drawPath(bolt, sun)
        }
    }
}

@Composable
private fun Glyph(code: Int, day: Boolean, modifier: Modifier) {
    Canvas(modifier) { glyph(code, day) }
}

@Composable
fun WeatherContent(prefs: SharedPreferences, fahrenheit: Boolean, title: String) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val ctx = LocalContext.current
    val active = LocalActive.current
    val mw = maxWidth
    val mh = maxHeight
    var granted by remember {
        mutableStateOf(ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok }
    var retry by remember { mutableIntStateOf(0) }
    val loc = remember(granted, retry) { findPlace(ctx, prefs, granted) }
    var weather by remember { mutableStateOf(parseWeather(prefs.getString("wx_json", null) ?: "")) }
    var failed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    // Refresh at most every half hour while the home screen is showing
    LaunchedEffect(loc, active, retry) {
        if (loc == null || !active) return@LaunchedEffect
        while (true) {
            val last = prefs.getLong("wx_time", 0L)
            val sameSpot = prefs.getString("wx_spot", null) == "%.1f,%.1f".format(Locale.US, loc.first, loc.second)
            if (!sameSpot || System.currentTimeMillis() - last > 30 * 60_000L) {
                loading = true
                val raw = withContext(Dispatchers.IO) { fetchWeather(loc.first, loc.second) }
                loading = false
                val parsed = raw?.let { parseWeather(it) }
                if (parsed != null) {
                    weather = parsed
                    failed = false
                    prefs.edit().putString("wx_json", raw).putLong("wx_time", System.currentTimeMillis())
                        .putString("wx_spot", "%.1f,%.1f".format(Locale.US, loc.first, loc.second)).apply()
                } else {
                    failed = true
                }
            }
            delay(5 * 60_000L)
        }
    }

    val w = weather
    if (loc == null && w == null) {
        WeatherNote(
            title,
            if (!granted) "Tap to share your rough location once. It stays on this phone, and only the rounded place is sent to open-meteo.com for the forecast."
            else "No location yet. Open a maps app once, then tap here to try again.",
            Modifier.clickable { if (!granted) ask.launch(Manifest.permission.ACCESS_COARSE_LOCATION) else retry++ },
        )
    } else if (w == null) {
        WeatherNote(
            title,
            if (failed) "Could not reach the weather service. Tap to try again." else "Fetching the weather...",
            Modifier.clickable { failed = false; prefs.edit().remove("wx_time").apply(); retry++ },
        )
    } else {
        val punk = Steam.punk
        val hi = if (punk) Steam.nixie.lit else Steam.BrassLight
        val tall = mh > 150.dp
        val showDays = mw > 250.dp && w.days.size > 1
        val glyphSize = minOf(mh * (if (tall) 0.5f else 0.8f), mw * 0.3f)
        Column(Modifier.fillMaxSize().clickable { failed = false; prefs.edit().remove("wx_time").apply(); retry++ }) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Glyph(w.code, w.day, Modifier.size(glyphSize))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 12.sp, fontFamily = Steam.font, fontWeight = FontWeight.Bold, color = Steam.Steam, maxLines = 1)
                    Text(
                        degrees(w.temp, fahrenheit) + (if (fahrenheit) "F" else "C"),
                        fontSize = minOf(mh.value * 0.34f, 38f).coerceAtLeast(18f).sp,
                        fontFamily = Steam.font, fontWeight = FontWeight.Bold, color = hi, maxLines = 1,
                    )
                    Text(
                        weatherName(w.code) + ", feels " + degrees(w.feels, fahrenheit),
                        fontSize = 12.sp, fontFamily = Steam.font, color = Steam.Parchment, maxLines = 1,
                    )
                    if (tall) {
                        Text(
                            "Wind " + (if (fahrenheit) "${(w.wind * 0.621f).roundToInt()} mph" else "${w.wind.roundToInt()} km/h") +
                                (if (loading) "  (updating)" else if (failed) "  (offline)" else ""),
                            fontSize = 11.sp, fontFamily = Steam.font, color = Steam.Steam, maxLines = 1,
                        )
                    }
                }
            }
            if (showDays && tall) {
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    w.days.drop(1).take(3).forEach { d ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                                fontSize = 11.sp, fontFamily = Steam.font, color = Steam.Steam, maxLines = 1,
                            )
                            Glyph(d.code, true, Modifier.size(30.dp))
                            Text(
                                degrees(d.high, fahrenheit) + " " + degrees(d.low, fahrenheit),
                                fontSize = 11.sp, fontFamily = Steam.font, color = Steam.Parchment, maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherNote(title: String, text: String, modifier: Modifier) {
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontSize = 14.sp, fontFamily = Steam.font, fontWeight = FontWeight.Bold, color = Steam.BrassLight, maxLines = 1)
        Text(text, fontSize = 12.sp, fontFamily = Steam.font, color = Steam.Steam)
    }
}

/** The same remembered place the Solar phase widget uses, so you only share your location once. */
private fun findPlace(ctx: Context, prefs: SharedPreferences, granted: Boolean): Pair<Double, Double>? = findLocation(ctx, prefs, granted)
