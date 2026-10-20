package com.steampunk.brassworks

import android.content.ClipData
import android.content.ClipboardManager
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.util.Base64
import android.os.BatteryManager
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.MathContext
import android.net.TrafficStats
import android.os.SystemClock
import kotlin.math.ln
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

// ───────────────────────── Apps ─────────────────────────

data class AppInfo(
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val icon: ImageBitmap,
)

private fun loadApps(ctx: Context): List<AppInfo> {
    val pm = ctx.packageManager
    val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(query, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map {
            AppInfo(
                label = it.loadLabel(pm).toString(),
                packageName = it.activityInfo.packageName,
                component = ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                icon = it.loadIcon(pm).toBitmap(144, 144).asImageBitmap(),
            )
        }
        .sortedBy { it.label.lowercase() }
}

/** First run: put the phone's default dialer, messages, browser and camera on the app shelf. */
private fun defaultShelf(ctx: Context): List<String> {
    val pm = ctx.packageManager
    val intents = listOf(
        Intent(Intent.ACTION_DIAL),
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING),
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER),
        Intent(MediaStore.ACTION_IMAGE_CAPTURE),
    )
    return intents
        .mapNotNull { pm.resolveActivity(it, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }
        .filter { it != "android" }
        .distinct()
}

private fun loadPinned(ctx: Context, prefs: SharedPreferences): List<String> {
    val saved = prefs.getString("pinned", null)
    if (saved != null) return saved.split(",").filter { it.isNotBlank() }
    val d = defaultShelf(ctx)
    prefs.edit().putString("pinned", d.joinToString(",")).apply()
    return d
}

private fun loadCounts(prefs: SharedPreferences): Map<String, Int> =
    (prefs.getString("counts", "") ?: "").split(",").mapNotNull {
        val p = it.split(":")
        if (p.size == 2) p[1].toIntOrNull()?.let { n -> p[0] to n } else null
    }.toMap()

private fun launchApp(ctx: Context, app: AppInfo) {
    val i = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(app.component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    try { ctx.startActivity(i) } catch (e: Exception) {
        Toast.makeText(ctx, "Could not open ${app.label}", Toast.LENGTH_SHORT).show()
    }
}

private fun open(ctx: Context, intent: Intent) {
    try { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {
        Toast.makeText(ctx, "That is not available on this device", Toast.LENGTH_SHORT).show()
    }
}

private fun batteryLevel(ctx: Context): Float {
    val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    return if (level < 0 || scale <= 0) 0f else level / scale.toFloat()
}

/** (fraction used, GB free) */
private fun storageUse(): Pair<Float, Float> = try {
    val s = StatFs(Environment.getDataDirectory().path)
    val total = s.totalBytes.toFloat()
    val free = s.availableBytes.toFloat()
    (1f - free / total) to (free / 1e9f)
} catch (e: Exception) { 0f to 0f }

// ───────────────────────── Power-user helpers ─────────────────────────

/** Fuzzy match score (higher is better, null = no match). Handles prefixes, word starts, initials and typos in order. */
private fun fuzzyScore(label: String, query: String): Int? {
    val l = label.lowercase()
    val q = query.trim().lowercase()
    if (q.isEmpty()) return 0
    if (l == q) return 1000
    if (l.startsWith(q)) return 900 - l.length
    val words = l.split(" ", "-", "_", ".").filter { it.isNotEmpty() }
    if (words.any { it.startsWith(q) }) return 800 - l.length
    val at = l.indexOf(q)
    if (at >= 0) return 700 - at
    if (words.map { it[0] }.joinToString("").startsWith(q)) return 600 // "gm" finds Google Maps
    if (q.length >= 3) {
        var qi = 0
        var gaps = 0
        var last = -1
        for ((i, ch) in l.withIndex()) {
            if (qi < q.length && ch == q[qi]) {
                if (last >= 0 && i - last > 1) gaps++
                last = i
                qi++
            }
        }
        if (qi == q.length) return 400 - gaps * 10 - l.length // "clndr" finds Calendar
    }
    return null
}

/** A tiny, safe arithmetic parser: + - * / ^ % and brackets. */
private class CalcParser(private val s: String) {
    private var i = 0

    fun parse(): Double? = try {
        val v = expr()
        skip()
        if (i == s.length && v.isFinite()) v else null
    } catch (e: Exception) { null }

    private fun skip() { while (i < s.length && s[i] == ' ') i++ }
    private fun peek(): Char = if (i < s.length) s[i] else '\u0000'

    private fun expr(): Double {
        var v = term()
        var more = true
        while (more) {
            skip()
            when (peek()) {
                '+' -> { i++; v += term() }
                '-' -> { i++; v -= term() }
                else -> more = false
            }
        }
        return v
    }

    private fun term(): Double {
        var v = unary()
        var more = true
        while (more) {
            skip()
            when (peek()) {
                '*' -> { i++; v *= unary() }
                '/' -> { i++; v /= unary() }
                else -> more = false
            }
        }
        return v
    }

    private fun unary(): Double {
        skip()
        return when (peek()) {
            '-' -> { i++; -unary() }
            '+' -> { i++; unary() }
            else -> power()
        }
    }

    private fun power(): Double {
        val b = postfix()
        skip()
        if (peek() == '^') { i++; return Math.pow(b, unary()) }
        return b
    }

    private fun postfix(): Double {
        var v = atom()
        skip()
        while (peek() == '%') { i++; v /= 100.0; skip() }
        return v
    }

    private fun atom(): Double {
        skip()
        if (peek() == '(') {
            i++
            val v = expr()
            skip()
            if (peek() != ')') throw IllegalArgumentException("missing bracket")
            i++
            return v
        }
        val st = i
        while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
        if (st == i) throw IllegalArgumentException("number expected")
        return s.substring(st, i).toDouble()
    }
}

private fun formatNumber(v: Double): String {
    if (v == Math.rint(v) && Math.abs(v) < 1e15) return v.toLong().toString()
    return BigDecimal(v).round(MathContext(10)).stripTrailingZeros().toPlainString()
}

/** The answer if the search text is a sum such as 18*4+6, otherwise null. */
private fun calcResult(query: String): String? {
    val q = query.trim()
        .replace('×', '*').replace('÷', '/').replace(',', '.')
        .replace(Regex("(?<=[0-9)])\\s*[xX]\\s*(?=[0-9(])"), "*")
    if (q.length < 3 || q.any { it !in "0123456789+-*/^%(). " }) return null
    val hasOp = q.indices.any { it > 0 && q[it] in "+-*/^%" } || q.contains('(')
    if (!hasOp || q.none { it.isDigit() }) return null
    val v = CalcParser(q).parse() ?: return null
    return formatNumber(v)
}

private fun loadHidden(prefs: SharedPreferences): Set<String> =
    (prefs.getString("hidden", "") ?: "").split(",").filter { it.isNotBlank() }.toSet()

private fun canUseShortcuts(ctx: Context): Boolean = try {
    (ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps).hasShortcutHostPermission()
} catch (e: Exception) { false }

/** App shortcuts (e.g. "New message"). Android only gives these to the default home app. */
private fun loadShortcuts(ctx: Context, pkg: String): List<ShortcutInfo> = try {
    val la = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    if (!la.hasShortcutHostPermission()) {
        emptyList()
    } else {
        val q = LauncherApps.ShortcutQuery()
            .setPackage(pkg)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        (la.getShortcuts(q, Process.myUserHandle()) ?: emptyList())
            .filter { it.isEnabled && it.shortLabel != null }
            .take(4)
    }
} catch (e: Exception) { emptyList() }

private fun startShortcut(ctx: Context, sc: ShortcutInfo) {
    try {
        (ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps).startShortcut(sc, null, null)
    } catch (e: Exception) {
        Toast.makeText(ctx, "Could not open that shortcut", Toast.LENGTH_SHORT).show()
    }
}

// ───────────────────────── Widgets: model ─────────────────────────

enum class WType(
    val title: String, val blurb: String,
    val defW: Float, val defH: Float, val minW: Float, val minH: Float,
    val tip: String? = null,
) {
    CLOCK("Nixie clock", "Glowing tube digits in a brass frame", 340f, 140f, 150f, 100f, "Recommended: the centrepiece"),
    ANALOG("Brass dial", "Analogue dial with the time", 340f, 140f, 150f, 120f),
    DIGITAL("Time plaque", "Large digital time and date", 340f, 100f, 150f, 80f),
    BOILER("Boiler pressure", "Battery level on a dial gauge", 164f, 140f, 110f, 110f, "Recommended: your charge at a glance"),
    CARGO("Cargo hold", "Storage used and free", 164f, 140f, 110f, 110f, "Recommended: spot a full phone early"),
    ENGINE("Engine room", "RAM, battery heat and network flow on three dials", 340f, 140f, 200f, 110f, "Recommended: see what is loading the phone"),
    ALMANAC("Almanac", "Month calendar with today marked", 340f, 280f, 200f, 200f, "Recommended: the month at a glance"),
    MEMO("Memorandum", "A notepad you can type in", 164f, 140f, 150f, 100f, "Recommended: jot things down fast"),
    SHELF("App shelf", "Apps you choose, one tap away", 340f, 130f, 110f, 100f, "Recommended: replaces the old dock"),
    FOLDER("Folder", "A named bundle of apps. Tap to open it", 164f, 150f, 110f, 110f, "Recommended: keeps related apps tidy"),
    PICKS("Chief Engineer's picks", "Apps you open most, learned on this phone", 340f, 210f, 110f, 100f, "Recommended: suggestions from your habits"),
}

data class WidgetItem(val id: Long, val type: WType, val x: Float, val y: Float, val w: Float, val h: Float)

private fun encode(list: List<WidgetItem>): String = JSONArray().also { arr ->
    list.forEach {
        arr.put(
            JSONObject().put("id", it.id).put("t", it.type.name)
                .put("x", it.x.toDouble()).put("y", it.y.toDouble())
                .put("w", it.w.toDouble()).put("h", it.h.toDouble())
        )
    }
}.toString()

private fun decode(s: String?): List<WidgetItem>? {
    if (s == null) return null
    return try {
        val a = JSONArray(s)
        (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val t = WType.entries.firstOrNull { e -> e.name == o.getString("t") } ?: return@mapNotNull null
            WidgetItem(o.getLong("id"), t, o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), o.getDouble("w").toFloat(), o.getDouble("h").toFloat())
        }
    } catch (e: Exception) { null }
}

// ───────────────────────── Presets ─────────────────────────

/** One widget slot in a preset, in grid cells (so it fits any screen width). */
data class PresetItem(val type: WType, val col: Int, val row: Int, val cols: Int, val rows: Int, val id: Long = 0L)
data class Preset(val name: String, val items: List<PresetItem>, val builtin: Boolean = false)

private const val AUTO_PRESET = "Previous layout"

private val BUILTIN_PRESETS = listOf(
    Preset("Classic", listOf(
        PresetItem(WType.CLOCK, 0, 0, 4, 2), PresetItem(WType.BOILER, 0, 2, 2, 2),
        PresetItem(WType.CARGO, 2, 2, 2, 2), PresetItem(WType.SHELF, 0, 4, 4, 2),
    ), true),
    Preset("Minimal", listOf(
        PresetItem(WType.CLOCK, 0, 0, 4, 2), PresetItem(WType.PICKS, 0, 2, 4, 3),
    ), true),
    Preset("Engineer", listOf(
        PresetItem(WType.CLOCK, 0, 0, 4, 2), PresetItem(WType.BOILER, 0, 2, 2, 2), PresetItem(WType.CARGO, 2, 2, 2, 2),
        PresetItem(WType.MEMO, 0, 4, 2, 2), PresetItem(WType.FOLDER, 2, 4, 2, 2),
    ), true),
    Preset("Planner", listOf(
        PresetItem(WType.CLOCK, 0, 0, 4, 2), PresetItem(WType.ALMANAC, 0, 2, 4, 4), PresetItem(WType.MEMO, 0, 6, 4, 2),
    ), true),
    Preset("Tinkerer", listOf(
        PresetItem(WType.CLOCK, 0, 0, 4, 2), PresetItem(WType.ENGINE, 0, 2, 4, 2),
        PresetItem(WType.BOILER, 0, 4, 2, 2), PresetItem(WType.CARGO, 2, 4, 2, 2),
    ), true),
)

private fun encodePresets(list: List<Preset>): String = JSONArray().also { arr ->
    list.forEach { p ->
        val its = JSONArray()
        p.items.forEach { i ->
            its.put(
                JSONObject().put("t", i.type.name).put("c", i.col).put("r", i.row)
                    .put("cs", i.cols).put("rs", i.rows).put("id", i.id)
            )
        }
        arr.put(JSONObject().put("name", p.name).put("items", its))
    }
}.toString()

private fun decodePresets(s: String?): List<Preset> {
    if (s == null) return emptyList()
    return try {
        val a = JSONArray(s)
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val its = o.getJSONArray("items")
            Preset(
                o.getString("name"),
                (0 until its.length()).mapNotNull { j ->
                    val x = its.getJSONObject(j)
                    val t = WType.entries.firstOrNull { e -> e.name == x.getString("t") } ?: return@mapNotNull null
                    PresetItem(t, x.getInt("c"), x.getInt("r"), x.getInt("cs"), x.getInt("rs"), x.optLong("id", 0L))
                },
            )
        }
    } catch (e: Exception) { emptyList() }
}

/** Holds every widget's position and size (in dp), the grid rules, folders and presets. */
class WidgetBoard(private val prefs: SharedPreferences) {
    var items by mutableStateOf(decode(prefs.getString("widgets_v1", null)))
    var areaW by mutableFloatStateOf(0f)
    var areaH by mutableFloatStateOf(0f)

    /** true = fixed grid (snap, no overlap); false = floating (place anywhere). */
    var gridMode by mutableStateOf(prefs.getBoolean("grid", true))
        private set

    var userPresets by mutableStateOf(decodePresets(prefs.getString("presets_v1", null)))
        private set

    /** Each Folder widget has its own name and its own list of apps. */
    val folderNames = mutableStateMapOf<Long, String>()
    val folderApps = mutableStateMapOf<Long, List<String>>()

    init {
        items?.filter { it.type == WType.FOLDER }?.forEach { loadFolder(it.id) }
    }

    private fun loadFolder(id: Long) {
        folderNames[id] = prefs.getString("fname_$id", "Folder") ?: "Folder"
        folderApps[id] = (prefs.getString("fapps_$id", "") ?: "").split(",").filter { it.isNotBlank() }
    }

    private fun saveFolder(id: Long) {
        prefs.edit()
            .putString("fname_$id", folderNames[id] ?: "Folder")
            .putString("fapps_$id", folderApps[id].orEmpty().joinToString(","))
            .apply()
    }

    private fun hasData(id: Long) = prefs.contains("fname_$id") || prefs.contains("note_$id")

    fun addToFolder(id: Long, pkg: String) {
        val cur = folderApps[id].orEmpty()
        if (pkg !in cur) { folderApps[id] = cur + pkg; saveFolder(id) }
    }

    fun removeFromFolder(id: Long, pkg: String) {
        folderApps[id] = folderApps[id].orEmpty() - pkg
        saveFolder(id)
    }

    fun renameFolder(id: Long, name: String) {
        folderNames[id] = name
        saveFolder(id)
    }

    // ----- grid maths -----

    private val gridCols = 4
    private val gap = 8f
    val columns: Int get() = gridCols
    val cellW: Float get() = if (areaW > 0f) (areaW - gap * (gridCols - 1)) / gridCols else 80f
    val cellH: Float get() = cellW * 0.85f
    val stepX: Float get() = cellW + gap
    val stepY: Float get() = cellH + gap
    val maxRows: Int get() = if (areaH > 0f) maxOf(1, ((areaH + gap) / stepY).toInt()) else 8

    private fun spanW(n: Int) = n * cellW + (n - 1) * gap
    private fun spanH(n: Int) = n * cellH + (n - 1) * gap
    private fun colsOf(w: Float) = ((w + gap) / stepX).roundToInt().coerceIn(1, gridCols)
    private fun rowsOf(h: Float) = ((h + gap) / stepY).roundToInt().coerceAtLeast(1)
    private fun minColsOf(t: WType) = ceil((t.minW + gap) / stepX).toInt().coerceIn(1, gridCols)
    private fun minRowsOf(t: WType) = ceil((t.minH + gap) / stepY).toInt().coerceAtLeast(1)

    private fun place(w: WidgetItem, col: Int, row: Int, c: Int, r: Int) =
        w.copy(x = col * stepX, y = row * stepY, w = spanW(c), h = spanH(r))

    private fun snapped(w: WidgetItem): WidgetItem {
        val c = maxOf(colsOf(w.w), minColsOf(w.type))
        val r = maxOf(rowsOf(w.h), minRowsOf(w.type))
        val col = (w.x / stepX).roundToInt().coerceIn(0, gridCols - c)
        val row = (w.y / stepY).roundToInt().coerceIn(0, maxOf(0, maxRows - r))
        return place(w, col, row, c, r)
    }

    private fun overlaps(a: WidgetItem, b: WidgetItem) =
        a.x < b.x + b.w - 1f && b.x < a.x + a.w - 1f && a.y < b.y + b.h - 1f && b.y < a.y + a.h - 1f

    private fun newOcc() = Array(maxRows) { BooleanArray(gridCols) }

    private fun Array<BooleanArray>.fits(col: Int, row: Int, c: Int, r: Int): Boolean {
        if (col < 0 || row < 0 || col + c > gridCols || row + r > size) return false
        for (rr in row until row + r) for (cc in col until col + c) if (this[rr][cc]) return false
        return true
    }

    private fun Array<BooleanArray>.mark(col: Int, row: Int, c: Int, r: Int) {
        for (rr in row until minOf(row + r, size)) for (cc in col until minOf(col + c, gridCols)) this[rr][cc] = true
    }

    /** The free spot closest to (nearCol, nearRow) that fits c x r cells. */
    private fun Array<BooleanArray>.findSpot(c: Int, r: Int, nearCol: Int, nearRow: Int): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null
        var bestD = Int.MAX_VALUE
        for (row in 0..(size - r)) for (col in 0..(gridCols - c)) {
            if (fits(col, row, c, r)) {
                val d = (col - nearCol) * (col - nearCol) + (row - nearRow) * (row - nearRow)
                if (d < bestD) { bestD = d; best = col to row }
            }
        }
        return best
    }

    private fun markAll(occ: Array<BooleanArray>, list: List<WidgetItem>) {
        list.forEach { o ->
            occ.mark((o.x / stepX).roundToInt().coerceAtLeast(0), (o.y / stepY).roundToInt().coerceAtLeast(0), colsOf(o.w), rowsOf(o.h))
        }
    }

    /** Snap everything to the grid and remove overlaps. If dropIfNoRoom, widgets with no space are left out. */
    private fun tidyList(list: List<WidgetItem>, dropIfNoRoom: Boolean): List<WidgetItem> {
        val occ = newOcc()
        val out = mutableListOf<WidgetItem>()
        for (w in list.sortedWith(compareBy({ it.y }, { it.x }))) {
            var c = maxOf(colsOf(w.w), minColsOf(w.type))
            var r = maxOf(rowsOf(w.h), minRowsOf(w.type))
            val nc = (w.x / stepX).roundToInt().coerceIn(0, gridCols - c)
            val nr = (w.y / stepY).roundToInt().coerceAtLeast(0)
            var spot = occ.findSpot(c, r, nc, nr)
            if (spot == null) {
                c = minColsOf(w.type); r = minRowsOf(w.type)
                spot = occ.findSpot(c, r, nc, nr)
            }
            if (spot != null) {
                occ.mark(spot.first, spot.second, c, r)
                out += place(w, spot.first, spot.second, c, r)
            } else if (!dropIfNoRoom) {
                out += place(w, nc.coerceIn(0, gridCols - c), nr, c, r)
            }
        }
        return out
    }

    fun useGrid(on: Boolean) {
        gridMode = on
        prefs.edit().putBoolean("grid", on).apply()
        if (on) {
            items = items?.let { tidyList(it, false) }
            persist()
        }
    }

    // ----- first run / defaults -----

    fun ensureDefaults() {
        if (areaW <= 0f) return
        val cur = items
        if (cur == null) {
            items = tidyList(instantiate(BUILTIN_PRESETS[0], emptyList()), true)
            prefs.edit().putBoolean("grid_done", true).apply()
            persist()
        } else if (gridMode && !prefs.getBoolean("grid_done", false)) {
            // layouts made before the grid existed get tidied once
            items = tidyList(cur, false)
            prefs.edit().putBoolean("grid_done", true).apply()
            persist()
        }
    }

    // ----- drag / resize -----

    private var start: WidgetItem? = null

    fun begin(id: Long) { start = items?.firstOrNull { it.id == id } }

    private fun update(id: Long, f: (WidgetItem) -> WidgetItem) {
        items = items?.map { if (it.id == id) f(it) else it }
    }

    fun move(id: Long, dx: Float, dy: Float) = update(id) {
        it.copy(
            x = (it.x + dx).coerceIn(0f, maxOf(0f, areaW - it.w)),
            y = (it.y + dy).coerceIn(0f, maxOf(0f, areaH - it.h)),
        )
    }

    fun resize(id: Long, dw: Float, dh: Float) = update(id) {
        val maxW = maxOf(it.type.minW, areaW - it.x)
        val maxH = maxOf(it.type.minH, areaH - it.y)
        it.copy(
            w = (it.w + dw).coerceIn(it.type.minW, maxW),
            h = (it.h + dh).coerceIn(it.type.minH, maxH),
        )
    }

    /** Called when a drag or resize ends. In grid mode the widget snaps into a free spot. */
    fun settle(id: Long, resizing: Boolean) {
        val list = items ?: return
        if (gridMode) {
            val cur = list.firstOrNull { it.id == id } ?: return
            val others = list.filter { it.id != id }
            val s = snapped(cur)
            val back = start?.takeIf { it.id == id }?.let { snapped(it) }
            val result = when {
                others.none { overlaps(s, it) } -> s
                resizing -> back ?: s
                else -> nearestFree(cur, others) ?: back ?: s
            }
            items = list.map { if (it.id == id) result else it }
        }
        persist()
    }

    private fun nearestFree(cur: WidgetItem, others: List<WidgetItem>): WidgetItem? {
        val occ = newOcc()
        markAll(occ, others)
        val c = maxOf(colsOf(cur.w), minColsOf(cur.type))
        val r = maxOf(rowsOf(cur.h), minRowsOf(cur.type))
        val spot = occ.findSpot(c, r, (cur.x / stepX).roundToInt(), (cur.y / stepY).roundToInt()) ?: return null
        return place(cur, spot.first, spot.second, c, r)
    }

    // ----- add / remove -----

    /** Returns the new widget's id, or -1 if there is no room left on the grid. */
    fun add(type: WType): Long {
        val id = System.currentTimeMillis()
        if (gridMode) {
            val occ = newOcc()
            markAll(occ, items.orEmpty())
            var c = maxOf(colsOf(type.defW), minColsOf(type))
            var r = maxOf(rowsOf(type.defH), minRowsOf(type))
            var spot = occ.findSpot(c, r, 0, 0)
            if (spot == null) {
                c = minColsOf(type); r = minRowsOf(type)
                spot = occ.findSpot(c, r, 0, 0)
            }
            if (spot == null) return -1L
            items = items.orEmpty() + place(WidgetItem(id, type, 0f, 0f, 0f, 0f), spot.first, spot.second, c, r)
        } else {
            val n = items?.size ?: 0
            val w = minOf(type.defW, areaW)
            val h = type.defH
            val x = ((areaW - w) / 2f).coerceAtLeast(0f)
            val y = (24f + (n % 6) * 28f).coerceAtMost(maxOf(0f, areaH - h))
            items = items.orEmpty() + WidgetItem(id, type, x, y, w, h)
        }
        if (type == WType.FOLDER) {
            folderNames[id] = "Folder"
            folderApps[id] = emptyList()
            saveFolder(id)
        }
        persist()
        return id
    }

    fun remove(id: Long) {
        items = items?.filter { it.id != id }
        folderNames.remove(id)
        folderApps.remove(id)
        prefs.edit().remove("note_$id").remove("fname_$id").remove("fapps_$id").apply()
        persist()
    }

    fun persist() {
        items?.let { prefs.edit().putString("widgets_v1", encode(it)).apply() }
    }

    // ----- presets -----

    private fun snapshot(name: String): Preset? {
        val list = items.orEmpty()
        if (list.isEmpty()) return null
        val tidy = tidyList(list, false)
        return Preset(
            name,
            tidy.map { PresetItem(it.type, (it.x / stepX).roundToInt(), (it.y / stepY).roundToInt(), colsOf(it.w), rowsOf(it.h), it.id) },
        )
    }

    private fun persistPresets() {
        prefs.edit().putString("presets_v1", encodePresets(userPresets)).apply()
    }

    /** Save the current layout under a name. Using an existing name updates that preset. */
    fun savePreset(name: String) {
        val p = snapshot(name) ?: return
        userPresets = userPresets.filter { it.name != name } + p
        persistPresets()
    }

    /** A text code for the current layout. Send it to anyone and they can import it. Ids stay private to this phone. */
    fun exportCode(): String? {
        val p = snapshot("Shared layout") ?: return null
        val clean = p.copy(items = p.items.map { it.copy(id = 0L) })
        val json = encodePresets(listOf(clean))
        return "BRASSWORKS1:" + Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
    }

    /** Adds a shared layout to your presets (it is not applied). Returns null if the code is not valid. */
    fun importCode(code: String): Preset? {
        val raw = code.trim().removePrefix("BRASSWORKS1:").filter { !it.isWhitespace() }
        val json = try {
            String(Base64.decode(raw, Base64.URL_SAFE), Charsets.UTF_8)
        } catch (e: Exception) {
            return null
        }
        val p = decodePresets(json).firstOrNull() ?: return null
        val items = p.items.take(20).map {
            it.copy(col = it.col.coerceIn(0, 3), row = it.row.coerceIn(0, 30), cols = it.cols.coerceIn(1, 4), rows = it.rows.coerceIn(1, 12), id = 0L)
        }
        if (items.isEmpty()) return null
        var name = "Imported layout"
        var n = 2
        while (userPresets.any { it.name == name }) { name = "Imported layout $n"; n++ }
        val imported = Preset(name, items)
        userPresets = userPresets + imported
        persistPresets()
        return imported
    }

    fun deletePreset(name: String) {
        userPresets = userPresets.filter { it.name != name }
        persistPresets()
    }

    /**
     * Turn a preset into widgets. Widgets you already have (same id, else same type) are reused,
     * so notes and folder contents survive. Returns how many widgets did not fit.
     */
    private fun instantiate(p: Preset, existing: List<WidgetItem>): List<WidgetItem> {
        val used = mutableSetOf<Long>()
        var stamp = System.currentTimeMillis()
        return p.items.map { pi ->
            val reuse = existing.firstOrNull { it.id == pi.id && pi.id != 0L && it.id !in used && it.type == pi.type }
                ?: existing.firstOrNull { it.type == pi.type && it.id !in used }
            val id = when {
                reuse != null -> reuse.id
                pi.id != 0L && pi.id !in used && existing.none { it.id == pi.id } && hasData(pi.id) -> pi.id
                else -> ++stamp
            }
            used += id
            WidgetItem(id, pi.type, pi.col * stepX, pi.row * stepY, spanW(pi.cols), spanH(pi.rows))
        }
    }

    fun applyPreset(p: Preset): Int {
        savePreset(AUTO_PRESET) // undo point
        val raw = instantiate(p, items.orEmpty())
        val placed = tidyList(raw, true)
        items = placed
        placed.filter { it.type == WType.FOLDER }.forEach { if (it.id !in folderNames) loadFolder(it.id) }
        persist()
        return raw.size - placed.size
    }
}

// ───────────────────────── Root ─────────────────────────

@Composable
fun LauncherRoot(tick: Int, homeSignal: Int) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("brassworks", Context.MODE_PRIVATE) }
    val board = remember { WidgetBoard(prefs) }

    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    LaunchedEffect(tick) { apps = withContext(Dispatchers.Default) { loadApps(ctx) } }

    var pinned by remember { mutableStateOf(loadPinned(ctx, prefs)) }
    var counts by remember { mutableStateOf(loadCounts(prefs)) }
    var animations by remember { mutableStateOf(prefs.getBoolean("anim", true)) }
    var drawer by remember { mutableStateOf(false) }
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }
    var settings by remember { mutableStateOf(false) }
    var fabMenu by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var openFolder by remember { mutableStateOf<Long?>(null) }
    var menuFolder by remember { mutableStateOf<Long?>(null) }
    var folderPick by remember { mutableStateOf<AppInfo?>(null) }
    var presetDialog by remember { mutableStateOf(false) }
    var saveDialog by remember { mutableStateOf(false) }
    var hiddenDialog by remember { mutableStateOf(false) }
    var importDialog by remember { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(loadHidden(prefs)) }

    // Pressing the Home button closes everything and returns to the main screen.
    LaunchedEffect(homeSignal) {
        drawer = false; menuApp = null; settings = false; fabMenu = false; picker = false; editing = false
        openFolder = null; menuFolder = null; folderPick = null; presetDialog = false; saveDialog = false; hiddenDialog = false; importDialog = false
    }
    BackHandler(enabled = drawer || menuApp != null || settings || fabMenu || picker || editing || folderPick != null || openFolder != null) {
        when {
            menuApp != null -> menuApp = null
            folderPick != null -> folderPick = null
            openFolder != null -> openFolder = null
            picker -> picker = false
            fabMenu -> fabMenu = false
            settings -> settings = false
            drawer -> drawer = false
            else -> editing = false
        }
    }

    fun setPinned(list: List<String>) {
        pinned = list
        prefs.edit().putString("pinned", list.joinToString(",")).apply()
    }

    fun setHiddenSet(set: Set<String>) {
        hidden = set
        prefs.edit().putString("hidden", set.joinToString(",")).apply()
    }

    val visibleApps = remember(apps, hidden) { apps.filter { it.packageName !in hidden } }

    fun applyPreset(p: Preset) {
        val dropped = board.applyPreset(p)
        presetDialog = false
        Toast.makeText(
            ctx,
            "Applied ${p.name}" + (if (dropped > 0) ". $dropped widget(s) did not fit." else ""),
            Toast.LENGTH_SHORT,
        ).show()
    }

    val launch: (AppInfo) -> Unit = { app ->
        counts = counts + (app.packageName to ((counts[app.packageName] ?: 0) + 1))
        prefs.edit().putString("counts", counts.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()
        launchApp(ctx, app)
    }

    CompositionLocalProvider(LocalAnimations provides animations) {
        Surface(Modifier.fillMaxSize(), color = Steam.Soot, contentColor = Steam.Parchment) {
            Box(Modifier.fillMaxSize()) {
                HomeScreen(
                    board = board, apps = apps, pinned = pinned, counts = counts, hidden = hidden, tick = tick,
                    prefs = prefs, editing = editing,
                    onLaunch = launch,
                    onLongPress = { menuApp = it; menuFolder = null },
                    onOpenFolder = { openFolder = it },
                    onFabTap = { drawer = true },
                    onFabHold = { fabMenu = true },
                    onEnterEdit = { editing = true },
                    onAdd = { picker = true },
                    onDone = { editing = false },
                )
                AnimatedVisibility(
                    visible = drawer,
                    enter = slideInVertically(tween(300)) { it } + fadeIn(tween(300)),
                    exit = slideOutVertically(tween(250)) { it } + fadeOut(tween(250)),
                ) {
                    DrawerScreen(
                        apps = visibleApps,
                        onLaunch = launch,
                        onLongPress = { menuApp = it; menuFolder = null },
                        onClose = { drawer = false },
                    )
                }
            }
        }

        // Long-press menu for an app
        menuApp?.let { app ->
            val onShelf = app.packageName in pinned
            val isHidden = app.packageName in hidden
            val shortcuts = remember(app.packageName) { loadShortcuts(ctx, app.packageName) }
            val canShortcuts = remember { canUseShortcuts(ctx) }
            Dialog(onDismissRequest = { menuApp = null }) {
                RivetedPanel(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Porthole(app.icon, size = 64.dp)
                        Text(app.label, style = MaterialTheme.typography.titleMedium, color = Steam.Parchment)
                        Text(
                            (if (onShelf) "Unpin takes it off your app shelf." else "Pin puts it on your app shelf.") +
                                (if (menuFolder != null) " Eject takes it out of this folder." else "") +
                                " Hide keeps it out of the archive.",
                            style = MaterialTheme.typography.labelLarge, color = Steam.Steam, textAlign = TextAlign.Center,
                        )
                        if (shortcuts.isNotEmpty()) {
                            Text("Shortcuts", style = MaterialTheme.typography.labelLarge, color = Steam.BrassLight)
                            shortcuts.forEach { sc ->
                                ChoiceRow(sc.shortLabel.toString(), "") { startShortcut(ctx, sc); menuApp = null }
                            }
                        } else if (!canShortcuts) {
                            Text(
                                "Make Brassworks your home app to see this app's shortcuts here.",
                                style = MaterialTheme.typography.labelLarge, color = Steam.Steam.copy(alpha = 0.7f), textAlign = TextAlign.Center,
                            )
                        }
                        CogRow {
                            CogButton(if (onShelf) "Unpin" else "Pin") {
                                setPinned(if (onShelf) pinned - app.packageName else pinned + app.packageName)
                                menuApp = null
                            }
                            CogButton("Folder") { folderPick = app; menuApp = null }
                            CogButton(if (isHidden) "Show" else "Hide") {
                                setHiddenSet(if (isHidden) hidden - app.packageName else hidden + app.packageName)
                                menuApp = null
                            }
                            menuFolder?.let { fid ->
                                CogButton("Eject") {
                                    board.removeFromFolder(fid, app.packageName)
                                    menuApp = null
                                }
                            }
                            CogButton("Info") {
                                open(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")))
                                menuApp = null
                            }
                            CogButton("Uninstall") {
                                open(ctx, Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}")))
                                menuApp = null
                            }
                        }
                    }
                }
            }
        }

        // Tap on the floating gear
        if (fabMenu) {
            Dialog(onDismissRequest = { fabMenu = false }) {
                RivetedPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("THE WORKSHOP", style = MaterialTheme.typography.titleLarge, color = Steam.BrassLight)
                        Text("Tap the gear to open the archive. Add places a widget, Arrange moves and resizes them, Presets saves and applies layouts.", style = MaterialTheme.typography.labelLarge, color = Steam.Steam)
                        CogRow {
                            CogButton("Add") { fabMenu = false; picker = true }
                            CogButton("Arrange") { fabMenu = false; editing = true }
                            CogButton("Presets") { fabMenu = false; presetDialog = true }
                            CogButton("Settings") { fabMenu = false; settings = true }
                            CogButton("Close") { fabMenu = false }
                        }
                    }
                }
            }
        }

        // Folder: choose which folder an app goes into
        folderPick?.let { app ->
            val folders = board.items.orEmpty().filter { it.type == WType.FOLDER }
            Dialog(onDismissRequest = { folderPick = null }) {
                RivetedPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("ADD TO FOLDER", style = MaterialTheme.typography.titleLarge, color = Steam.BrassLight)
                        Text(app.label, style = MaterialTheme.typography.labelLarge, color = Steam.Steam)
                        Column(
                            Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            folders.forEach { f ->
                                val name = board.folderNames[f.id] ?: "Folder"
                                val n = board.folderApps[f.id].orEmpty().size
                                val has = app.packageName in board.folderApps[f.id].orEmpty()
                                ChoiceRow(name, if (has) "Already inside" else "$n ${if (n == 1) "app" else "apps"}") {
                                    board.addToFolder(f.id, app.packageName)
                                    Toast.makeText(ctx, "Added to $name", Toast.LENGTH_SHORT).show()
                                    folderPick = null
                                }
                            }
                        }
                        Text("New starts a fresh folder on your home screen.", style = MaterialTheme.typography.labelLarge, color = Steam.Steam)
                        CogRow {
                            CogButton("New") {
                                val id = board.add(WType.FOLDER)
                                if (id < 0L) {
                                    Toast.makeText(ctx, "No room left on the grid for a new folder.", Toast.LENGTH_LONG).show()
                                } else {
                                    board.addToFolder(id, app.packageName)
                                    Toast.makeText(ctx, "Created a new folder on your home screen", Toast.LENGTH_SHORT).show()
                                }
                                folderPick = null
                            }
                            CogButton("Cancel") { folderPick = null }
                        }
                    }
                }
            }
        }

        // Folder: the opened pop-up
        openFolder?.let { fid ->
            FolderDialog(
                id = fid, board = board, apps = apps,
                onLaunch = { launch(it); openFolder = null },
                onLongPress = { menuApp = it; menuFolder = fid },
                onClose = { openFolder = null },
            )
        }

        // Hidden apps
        if (hiddenDialog) {
            val hiddenApps = apps.filter { it.packageName in hidden }
            Dialog(onDismissRequest = { hiddenDialog = false }) {
                RivetedPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("HIDDEN APPS", style = MaterialTheme.typography.titleLarge, color = Steam.BrassLight)
                        Text(
                            "Hidden apps stay out of the archive, search and picks. Tap one to open it. This only tidies the view and does not lock anything.",
                            style = MaterialTheme.typography.labelLarge, color = Steam.Steam,
                        )
                        if (hiddenApps.isEmpty()) {
                            Text(
                                "Nothing is hidden. Long-press an app in the archive and choose Hide.",
                                style = MaterialTheme.typography.bodyMedium, color = Steam.Parchment,
                            )
                        } else {
                            Column(
                                Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                hiddenApps.forEach { a ->
                                    HiddenRow(a, onOpen = { launch(a); hiddenDialog = false }, onShow = { setHiddenSet(hidden - a.packageName) })
                                }
                            }
                        }
                        CogRow { CogButton("Close") { hiddenDialog = false } }
                    }
                }
            }
        }

        // Presets list
        if (presetDialog) {
            Dialog(onDismissRequest = { presetDialog = false }) {
                RivetedPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("PRESETS", style = MaterialTheme.typography.titleLarge, color = Steam.BrassLight)
                        Text(
                            "Tap a preset to apply it. Your current layout is kept as Previous layout. Save stores your layout, Share sends it as a text code, Import adds a code you received.",
                            style = MaterialTheme.typography.labelLarge, color = Steam.Steam,
                        )
                        Column(
                            Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            BUILTIN_PRESETS.forEach { p -> PresetRow(p, null) { applyPreset(p) } }
                            board.userPresets.forEach { p -> PresetRow(p, { board.deletePreset(p.name) }) { applyPreset(p) } }
                        }
                        CogRow {
                            CogButton("Save") { presetDialog = false; saveDialog = true }
                            CogButton("Share") {
                                val code = board.exportCode()
                                if (code == null) {
                                    Toast.makeText(ctx, "Nothing to share yet. Add a widget first.", Toast.LENGTH_SHORT).show()
                                } else {
                                    val send = Intent(Intent.ACTION_SEND)
                                        .setType("text/plain")
                                        .putExtra(Intent.EXTRA_SUBJECT, "Brassworks layout")
                                        .putExtra(Intent.EXTRA_TEXT, code)
                                    open(ctx, Intent.createChooser(send, "Share layout"))
                                }
                            }
                            CogButton("Import") { presetDialog = false; importDialog = true }
                            CogButton("Close") { presetDialog = false }
                        }
                    }
                }
            }
        }

        // Import a shared layout code
        if (importDialog) {
            var code by remember { mutableStateOf("") }
            Dialog(onDismissRequest = { importDialog = false }) {
                RivetedPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("IMPORT LAYOUT", style = MaterialTheme.typography.titleLarge, color = Steam.BrassLight)
                        Text(
                            "Paste a layout code someone shared with you. It is added to your presets and nothing changes until you apply it.",
                            style = MaterialTheme.typography.labelLarge, color = Steam.Steam,
                        )
                        OutlinedTextField(
                            value = code, onValueChange = { code = it }, modifier = Modifier.fillMaxWidth(),
                            maxLines = 4, placeholder = { Text("BRASSWORKS1:...") }, colors = steamFieldColors(),
                        )
                        CogRow {
                            CogButton("Paste") {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = cm.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    code = clip.getItemAt(0).text?.toString() ?: code
                                }
                            }
                            CogButton("Import") {
                                val imported = board.importCode(code)
                                if (imported == null) {
                                    Toast.makeText(ctx, "That does not look like a Brassworks layout code.", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(ctx, "Imported ${imported.name}. Find it in Presets.", Toast.LENGTH_LONG).show()
                                    importDialog = false
                                    presetDialog = true
                                }
                            }
                            CogButton("Cancel") { importDialog = false }
                        }
                    }
                }
            }
        }

        // Save the current layout as a preset
        if (saveDialog) {
            var name by remember { mutableStateOf("") }
            Dialog(onDismissRequest = { saveDialog = false }) {
                RivetedPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("SAVE PRESET", style = MaterialTheme.typography.titleLarge, color = Steam.BrassLight)
                        Text("Name your layout. Using an existing name updates that preset.", style = MaterialTheme.typography.labelLarge, color = Steam.Steam)
                        OutlinedTextField(
                            value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(),
                            singleLine = true, placeholder = { Text("Preset name") }, colors = steamFieldColors(),
                        )
                        CogRow {
                            CogButton("Save") {
                                val n = name.trim()
                                if (n.isNotEmpty()) {
                                    board.savePreset(n)
                                    Toast.makeText(ctx, "Saved preset $n", Toast.LENGTH_SHORT).show()
                                    saveDialog = false
                                }
                            }
                            CogButton("Cancel") { saveDialog = false }
                        }
                    }
                }
            }
        }

        // Widget picker
        if (picker) {
            Dialog(onDismissRequest = { picker = false }) {
                RivetedPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("ADD A WIDGET", style = MaterialTheme.typography.titleLarge, color = Steam.BrassLight)
                        Column(
                            Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            WType.entries.forEach { t ->
                                PickerRow(t) {
                                    val id = board.add(t)
                                    picker = false
                                    if (id < 0L) {
                                        Toast.makeText(ctx, "No room left on the grid. Remove or shrink a widget first.", Toast.LENGTH_LONG).show()
                                    } else {
                                        editing = true
                                    }
                                }
                            }
                        }
                        CogRow { CogButton("Cancel") { picker = false } }
                    }
                }
            }
        }

        if (settings) {
            Dialog(onDismissRequest = { settings = false }) {
                RivetedPanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("SETTINGS", style = MaterialTheme.typography.titleLarge, color = Steam.BrassLight)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Turning gears", style = MaterialTheme.typography.titleMedium, color = Steam.Parchment)
                                Text("Turn off to save battery", style = MaterialTheme.typography.labelLarge, color = Steam.Steam)
                            }
                            Switch(
                                checked = animations,
                                onCheckedChange = { animations = it; prefs.edit().putBoolean("anim", it).apply() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Steam.BrassLight, checkedTrackColor = Steam.BrassDark,
                                    uncheckedThumbColor = Steam.Steam, uncheckedTrackColor = Steam.Soot,
                                ),
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Fixed grid", style = MaterialTheme.typography.titleMedium, color = Steam.Parchment)
                                Text("Widgets snap into place and cannot overlap. Off lets them float anywhere.", style = MaterialTheme.typography.labelLarge, color = Steam.Steam)
                            }
                            Switch(
                                checked = board.gridMode,
                                onCheckedChange = { board.useGrid(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Steam.BrassLight, checkedTrackColor = Steam.BrassDark,
                                    uncheckedThumbColor = Steam.Steam, uncheckedTrackColor = Steam.Soot,
                                ),
                            )
                        }
                        Text("Home sets your default home app. Device opens Android settings. Hidden lists apps you hid.", style = MaterialTheme.typography.labelLarge, color = Steam.Steam)
                        CogRow {
                            CogButton("Home") { open(ctx, Intent(Settings.ACTION_HOME_SETTINGS)) }
                            CogButton("Device") { open(ctx, Intent(Settings.ACTION_SETTINGS)) }
                            CogButton("Hidden") { settings = false; hiddenDialog = true }
                            CogButton("Close") { settings = false }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HiddenRow(app: AppInfo, onOpen: () -> Unit, onShow: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(Steam.Soot.copy(alpha = 0.6f))
            .border(1.dp, Steam.BrassDark, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).clickable(onClick = onOpen).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Porthole(app.icon, size = 40.dp)
            Spacer(Modifier.width(10.dp))
            Text(app.label, style = MaterialTheme.typography.titleMedium, color = Steam.Parchment, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            "Show", Modifier.clickable(onClick = onShow).padding(14.dp),
            color = Steam.Verdigris, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PresetRow(p: Preset, onDelete: (() -> Unit)?, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(Steam.Soot.copy(alpha = 0.6f))
            .border(1.dp, Steam.BrassDark, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).clickable(onClick = onClick).padding(12.dp)) {
            Text(p.name, style = MaterialTheme.typography.titleMedium, color = Steam.BrassLight)
            Text(
                "${p.items.size} widgets · " + (if (p.builtin) "Built in" else "Yours"),
                style = MaterialTheme.typography.bodyMedium, color = Steam.Parchment,
            )
        }
        if (onDelete != null) {
            Text("✕", Modifier.clickable(onClick = onDelete).padding(14.dp), color = Steam.Copper, fontSize = 20.sp)
        }
    }
}

@Composable
private fun PickerRow(t: WType, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Steam.Soot.copy(alpha = 0.6f))
            .border(1.dp, Steam.BrassDark, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(t.title, style = MaterialTheme.typography.titleMedium, color = Steam.BrassLight)
        Text(t.blurb, style = MaterialTheme.typography.bodyMedium, color = Steam.Parchment)
        if (t.tip != null) Text("★ " + t.tip.removePrefix("Recommended: "), style = MaterialTheme.typography.labelLarge, color = Steam.Verdigris, fontSize = 12.sp)
    }
}

// ───────────────────────── Home ─────────────────────────

@Composable
private fun HomeScreen(
    board: WidgetBoard,
    apps: List<AppInfo>,
    pinned: List<String>,
    counts: Map<String, Int>,
    hidden: Set<String>,
    tick: Int,
    prefs: SharedPreferences,
    editing: Boolean,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onOpenFolder: (Long) -> Unit,
    onFabTap: () -> Unit,
    onFabHold: () -> Unit,
    onEnterEdit: () -> Unit,
    onAdd: () -> Unit,
    onDone: () -> Unit,
) {
    val ctx = LocalContext.current
    val is24 = remember { DateFormat.is24HourFormat(ctx) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var battery by remember { mutableFloatStateOf(batteryLevel(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            battery = batteryLevel(ctx)
            delay(60_000L - (System.currentTimeMillis() % 60_000L) + 50L) // wake on the next minute
        }
    }
    val storage = remember(tick) { storageUse() }

    Box(Modifier.fillMaxSize()) {
        SteamBackdrop()
        // Press and hold on empty space to rearrange widgets
        Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onLongPress = { onEnterEdit() }) })

        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
            LaunchedEffect(maxWidth, maxHeight) {
                board.areaW = maxWidth.value
                board.areaH = maxHeight.value
                board.ensureDefaults()
            }
            if (editing && board.gridMode) {
                Canvas(Modifier.fillMaxSize()) {
                    val sx = board.stepX.dp.toPx()
                    val sy = board.stepY.dp.toPx()
                    val cw = board.cellW.dp.toPx()
                    val ch = board.cellH.dp.toPx()
                    for (r in 0 until board.maxRows) for (c in 0 until board.columns) {
                        drawRoundRect(
                            Steam.BrassLight.copy(alpha = 0.18f),
                            topLeft = Offset(c * sx, r * sy), size = Size(cw, ch),
                            cornerRadius = CornerRadius(8.dp.toPx()), style = Stroke(1.dp.toPx()),
                        )
                    }
                }
            }
            board.items.orEmpty().forEach { w ->
                key(w.id) {
                    WidgetHost(w, board, editing) {
                        WidgetBody(w, board, now, is24, battery, storage, apps, pinned, counts, hidden, prefs, editing, onLaunch, onLongPress, onOpenFolder)
                    }
                }
            }
        }

        if (editing) {
            RivetedPanel(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Drag a widget to move it. Pull its corner to resize.",
                        style = MaterialTheme.typography.labelLarge, color = Steam.Steam, modifier = Modifier.weight(1f),
                    )
                    CogButton("Add", onClick = onAdd)
                    CogButton("Done", onClick = onDone)
                }
            }
        } else {
            SteamFab(onFabTap, onFabHold, Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp))
        }
    }
}

/** Positions one widget and, in edit mode, adds the move / resize / remove controls. */
@Composable
private fun WidgetHost(w: WidgetItem, board: WidgetBoard, editing: Boolean, content: @Composable () -> Unit) {
    val wd = if (board.areaW > 0f) w.w.coerceAtMost(board.areaW) else w.w
    val ht = if (board.areaH > 0f) w.h.coerceAtMost(board.areaH) else w.h
    val x = w.x.coerceIn(0f, maxOf(0f, board.areaW - wd))
    val y = w.y.coerceIn(0f, maxOf(0f, board.areaH - ht))

    Box(
        Modifier
            .offset { IntOffset(x.dp.roundToPx(), y.dp.roundToPx()) }
            .size(wd.dp, ht.dp)
    ) {
        content()
        if (editing) {
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier.fillMaxSize().clip(shape)
                    .background(Color(0x40000000))
                    .border(2.dp, Steam.BrassLight, shape)
                    .pointerInput(w.id) {
                        detectDragGestures(
                            onDragStart = { board.begin(w.id) },
                            onDragEnd = { board.settle(w.id, false) },
                            onDragCancel = { board.settle(w.id, false) },
                        ) { change, drag ->
                            change.consume()
                            board.move(w.id, drag.x.toDp().value, drag.y.toDp().value)
                        }
                    }
            )
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(30.dp).clip(CircleShape)
                    .background(Steam.Copper).clickable { board.remove(w.id) },
                contentAlignment = Alignment.Center,
            ) { Text("✕", color = Color.White, fontSize = 14.sp) }
            Box(
                Modifier.align(Alignment.BottomEnd).size(44.dp)
                    .pointerInput(w.id) {
                        detectDragGestures(
                            onDragStart = { board.begin(w.id) },
                            onDragEnd = { board.settle(w.id, true) },
                            onDragCancel = { board.settle(w.id, true) },
                        ) { change, drag ->
                            change.consume()
                            board.resize(w.id, drag.x.toDp().value, drag.y.toDp().value)
                        }
                    },
                contentAlignment = Alignment.BottomEnd,
            ) { Text("◢", color = Steam.BrassLight, fontSize = 26.sp, modifier = Modifier.padding(8.dp)) }
        }
    }
}

@Composable
private fun WidgetBody(
    w: WidgetItem,
    board: WidgetBoard,
    now: LocalDateTime,
    is24: Boolean,
    battery: Float,
    storage: Pair<Float, Float>,
    apps: List<AppInfo>,
    pinned: List<String>,
    counts: Map<String, Int>,
    hidden: Set<String>,
    prefs: SharedPreferences,
    editing: Boolean,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onOpenFolder: (Long) -> Unit,
) {
    RivetedPanel(Modifier.fillMaxSize()) {
        when (w.type) {
            WType.CLOCK -> NixieContent(now, is24)
            WType.ANALOG -> ClockContent(now, is24)
            WType.DIGITAL -> DigitalContent(now, is24)
            WType.BOILER -> GaugeContent(battery, true, "Boiler pressure", "${(battery * 100).toInt()}% charged")
            WType.CARGO -> GaugeContent(storage.first, false, "Cargo hold", "${(storage.first * 100).toInt()}% full")
            WType.ENGINE -> EngineRoomContent()
            WType.ALMANAC -> AlmanacContent(now)
            WType.MEMO -> MemoContent(w.id, prefs, editing)
            WType.SHELF -> AppRowContent(
                "App shelf",
                pinned.mapNotNull { p -> apps.firstOrNull { it.packageName == p } },
                "Long-press an app in the archive to add it here.",
                onLaunch, onLongPress,
            )
            WType.FOLDER -> FolderContent(
                board.folderNames[w.id] ?: "Folder",
                board.folderApps[w.id].orEmpty().mapNotNull { p -> apps.firstOrNull { it.packageName == p } },
            ) { onOpenFolder(w.id) }
            WType.PICKS -> AppRowContent(
                "Chief Engineer's picks",
                apps.filter { it.packageName !in hidden && (counts[it.packageName] ?: 0) > 0 }.sortedByDescending { counts[it.packageName] ?: 0 }.take(8),
                "Open a few apps and I will learn which you favour.",
                onLaunch, onLongPress, iconSize = 60.dp,
            )
        }
    }
}

// ───────────────────────── Widgets: content ─────────────────────────

private fun clockText(now: LocalDateTime, is24: Boolean): Pair<String, String?> =
    now.format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm")) to
        (if (is24) null else now.format(DateTimeFormatter.ofPattern("a")))

@Composable
private fun TimeLine(time: String, ampm: String?, fontSize: Float) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(time, fontSize = fontSize.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Steam.BrassLight, maxLines = 1)
        if (ampm != null) Text(
            ampm, style = MaterialTheme.typography.labelLarge, color = Steam.Steam,
            modifier = Modifier.padding(start = 6.dp, bottom = (fontSize * 0.14f).dp),
        )
    }
}

@Composable
private fun ClockContent(now: LocalDateTime, is24: Boolean) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val (time, ampm) = clockText(now, is24)
    val hour = now.hour % 12 + now.minute / 60f
    val minute = now.minute + now.second / 60f
    val mw = maxWidth
    val mh = maxHeight
    if (mw > mh * 1.3f) {
        val dial = minOf(mh, mw * 0.5f)
        val textW = mw - dial - 12.dp
        val fs = minOf(textW.value * 0.27f, mh.value * 0.45f).coerceIn(18f, 48f)
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            AnalogClock(hour, minute, Modifier.size(dial))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                TimeLine(time, ampm, fs)
                Text(
                    now.format(DateTimeFormatter.ofPattern("EEEE, d MMM")),
                    style = MaterialTheme.typography.titleMedium, color = Steam.Parchment,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    } else {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            AnalogClock(hour, minute, Modifier.size(minOf(mw, mh * 0.68f)))
            Spacer(Modifier.height(4.dp))
            TimeLine(time, ampm, (mh.value * 0.14f).coerceIn(14f, 28f))
        }
    }
}

@Composable
private fun NixieContent(now: LocalDateTime, is24: Boolean) {
    val hour = if (is24) now.hour else (now.hour % 12).let { if (it == 0) 12 else it }
    val hh = if (is24) hour.toString().padStart(2, '0') else hour.toString().padStart(2, ' ')
    val mm = now.format(DateTimeFormatter.ofPattern("mm"))
    val prefix = if (is24) "" else (if (now.hour < 12) "AM" else "PM") + "  ·  "
    NixieClock(hh + mm, prefix + now.format(DateTimeFormatter.ofPattern("EEEE, d MMM")), Modifier.fillMaxSize())
}

@Composable
private fun DigitalContent(now: LocalDateTime, is24: Boolean) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val (time, ampm) = clockText(now, is24)
    val fs = minOf(maxWidth.value * 0.2f, maxHeight.value * 0.55f).coerceIn(20f, 72f)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        TimeLine(time, ampm, fs)
        Text(
            now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
            style = MaterialTheme.typography.titleMedium, color = Steam.Parchment,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GaugeContent(value: Float, dangerLow: Boolean, title: String, caption: String) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val mw = maxWidth
    val mh = maxHeight
    if (mw > mh * 1.35f) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            PressureGauge(value, Modifier.size(minOf(mh, mw * 0.45f)), dangerLow)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Steam.BrassLight, maxLines = 1)
                Text(caption, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
        }
    } else {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            PressureGauge(value, Modifier.size(minOf(mw, mh * 0.62f)), dangerLow)
            Spacer(Modifier.height(2.dp))
            Text(title, fontSize = 14.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Steam.BrassLight, maxLines = 1)
            Text(caption, fontSize = 12.sp, fontFamily = FontFamily.Serif, color = Steam.Steam, maxLines = 1)
        }
    }
}

/** "1.2M", "340K" or "0": a compact bytes-per-second figure. */
private fun shortRate(bps: Long): String = when {
    bps >= 1_000_000L -> "%.1fM".format(bps / 1_000_000f)
    bps >= 1_000L -> "${bps / 1_000L}K"
    else -> "0"
}

/** Three dials: memory in use, battery heat and network flow. Samples every two seconds while visible. */
@Composable
private fun EngineRoomContent() = BoxWithConstraints(Modifier.fillMaxSize()) {
    val ctx = LocalContext.current
    val mw = maxWidth
    val mh = maxHeight
    var mem by remember { mutableFloatStateOf(0f) }
    var heat by remember { mutableFloatStateOf(-1f) } // degrees C, -1 = unknown
    var rx by remember { mutableLongStateOf(-1L) }    // bytes per second, -1 = unknown
    var tx by remember { mutableLongStateOf(-1L) }

    LaunchedEffect(Unit) {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        var lastRx = TrafficStats.getTotalRxBytes()
        var lastTx = TrafficStats.getTotalTxBytes()
        var lastT = SystemClock.elapsedRealtime()
        var first = true
        while (true) {
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            mem = if (mi.totalMem > 0L) 1f - mi.availMem.toFloat() / mi.totalMem.toFloat() else 0f

            val t = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            heat = if (t == null || t == Int.MIN_VALUE) -1f else t / 10f

            val nowRx = TrafficStats.getTotalRxBytes()
            val nowTx = TrafficStats.getTotalTxBytes()
            val nowT = SystemClock.elapsedRealtime()
            val dt = (nowT - lastT) / 1000f
            if (nowRx == TrafficStats.UNSUPPORTED.toLong() || nowTx == TrafficStats.UNSUPPORTED.toLong()) {
                rx = -1L; tx = -1L
            } else if (first || dt <= 0f) {
                rx = 0L; tx = 0L
            } else {
                rx = ((nowRx - lastRx) / dt).toLong().coerceAtLeast(0L)
                tx = ((nowTx - lastTx) / dt).toLong().coerceAtLeast(0L)
            }
            first = false
            lastRx = nowRx; lastTx = nowTx; lastT = nowT
            delay(2000)
        }
    }

    val dial = maxOf(minOf((mw - 16.dp) / 3, mh - 34.dp), 30.dp)
    // network flow on a log scale: 0 to 100 MB/s
    val flow = if (rx < 0L) 0f else (ln(1f + rx / 1000f) / ln(1f + 100_000f)).coerceIn(0f, 1f)
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        EngineDial(Modifier.weight(1f), dial, mem, "Memory", "${(mem * 100).roundToInt()}% used")
        EngineDial(
            Modifier.weight(1f), dial, ((heat - 20f) / 40f).coerceIn(0f, 1f), "Heat",
            if (heat < 0f) "n/a" else "${heat.roundToInt()}°C",
        )
        EngineDial(
            Modifier.weight(1f), dial, flow, "Flow",
            if (rx < 0L) "n/a" else "↓${shortRate(rx)} ↑${shortRate(tx)}",
        )
    }
}

@Composable
private fun EngineDial(modifier: Modifier, dial: Dp, value: Float, title: String, caption: String) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PressureGauge(value, Modifier.size(dial))
        Text(title, fontSize = 12.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Steam.BrassLight, maxLines = 1)
        Text(caption, fontSize = 11.sp, fontFamily = FontFamily.Serif, color = Steam.Steam, maxLines = 1)
    }
}

@Composable
private fun AlmanacContent(now: LocalDateTime) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val ym = YearMonth.of(now.year, now.month)
    val offset = ym.atDay(1).dayOfWeek.value % 7 // Sunday = 0
    val days = ym.lengthOfMonth()
    val rows = (offset + days + 6) / 7
    val cellH = (maxHeight - 22.dp) / (rows + 1)
    val dot = minOf(cellH, maxWidth / 7) * 0.9f
    val fs = minOf(cellH.value * 0.6f, (maxWidth.value / 7f) * 0.55f).coerceIn(8f, 18f)
    Column(Modifier.fillMaxSize()) {
        Text(
            now.month.getDisplayName(JTextStyle.FULL, Locale.getDefault()) + " " + now.year,
            fontSize = 14.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Steam.BrassLight,
            modifier = Modifier.height(22.dp), maxLines = 1,
        )
        Row(Modifier.fillMaxWidth()) {
            "SMTWTFS".forEach { c ->
                Box(Modifier.weight(1f).height(cellH), contentAlignment = Alignment.Center) {
                    Text(c.toString(), fontSize = fs.sp, fontFamily = FontFamily.Serif, color = Steam.Steam)
                }
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val d = r * 7 + c - offset + 1
                    Box(Modifier.weight(1f).height(cellH), contentAlignment = Alignment.Center) {
                        if (d in 1..days) {
                            val today = d == now.dayOfMonth
                            Box(
                                if (today) Modifier.size(dot).clip(CircleShape).background(Steam.Brass) else Modifier,
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "$d", fontSize = fs.sp, fontFamily = FontFamily.Serif,
                                    fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
                                    color = if (today) Steam.Ink else Steam.Parchment,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoContent(id: Long, prefs: SharedPreferences, editing: Boolean) {
    var text by remember(id) { mutableStateOf(prefs.getString("note_$id", "") ?: "") }
    Column(Modifier.fillMaxSize()) {
        Text("Memorandum", style = MaterialTheme.typography.titleMedium, color = Steam.BrassLight, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it; prefs.edit().putString("note_$id", it).apply() },
            enabled = !editing,
            textStyle = TextStyle(color = Steam.Parchment, fontFamily = FontFamily.Serif, fontSize = 15.sp, lineHeight = 21.sp),
            cursorBrush = SolidColor(Steam.BrassLight),
            modifier = Modifier.fillMaxWidth().weight(1f),
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) Text("Tap to jot a note", style = MaterialTheme.typography.labelLarge, color = Steam.Steam.copy(alpha = 0.6f))
                    inner()
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun AppRowContent(
    title: String,
    list: List<AppInfo>,
    empty: String,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    iconSize: Dp = 48.dp,
) {
    Column(Modifier.fillMaxSize()) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Steam.BrassLight, fontSize = 14.sp, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        if (list.isEmpty()) {
            Text(empty, style = MaterialTheme.typography.labelLarge, color = Steam.Steam)
        } else {
            FlowRow(
                Modifier.fillMaxSize().clipToBounds(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                list.forEach { app ->
                    Porthole(
                        app.icon, size = iconSize,
                        modifier = Modifier.combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onLongPress(app) }),
                    )
                }
            }
        }
    }
}

// ───────────────────────── Folders ─────────────────────────

/** Folded view: a small 2x2 preview of the first apps plus the folder's name. Tap to open. */
@Composable
private fun FolderContent(name: String, list: List<AppInfo>, onOpen: () -> Unit) =
    BoxWithConstraints(Modifier.fillMaxSize().clickable(onClick = onOpen)) {
        val side = maxOf(minOf(maxWidth, maxHeight - 22.dp), 40.dp)
        val cell = (side - 18.dp) / 2
        val shape = RoundedCornerShape(14.dp)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                Modifier.size(side).clip(shape)
                    .background(Steam.Soot.copy(alpha = 0.6f))
                    .border(1.5.dp, Steam.BrassDark, shape)
                    .padding(6.dp),
            ) {
                if (list.isEmpty()) {
                    Text("Empty", style = MaterialTheme.typography.labelLarge, color = Steam.Steam, modifier = Modifier.align(Alignment.Center))
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        list.take(4).chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                pair.forEach { Porthole(it.icon, size = cell) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                name, fontSize = 14.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                color = Steam.BrassLight, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }

@Composable
private fun FolderDialog(
    id: Long,
    board: WidgetBoard,
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onClose: () -> Unit,
) {
    val name = board.folderNames[id] ?: "Folder"
    val list = board.folderApps[id].orEmpty().mapNotNull { p -> apps.firstOrNull { it.packageName == p } }
    var renaming by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(name) }

    Dialog(onDismissRequest = onClose) {
        RivetedPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (renaming) {
                        OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), singleLine = true, colors = steamFieldColors())
                    } else {
                        Text(
                            name, style = MaterialTheme.typography.titleLarge, color = Steam.BrassLight,
                            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    CogButton(if (renaming) "Save" else "Rename", diameter = 76.dp) {
                        if (renaming) {
                            board.renameFolder(id, draft.trim().ifEmpty { "Folder" })
                        } else {
                            draft = name
                        }
                        renaming = !renaming
                    }
                }
                if (list.isEmpty()) {
                    Text(
                        "This folder is empty. Long-press an app in the archive and choose Add to folder.",
                        style = MaterialTheme.typography.bodyMedium, color = Steam.Steam,
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.heightIn(max = 380.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(list, key = { it.component.flattenToString() }) { app -> AppCell(app, onLaunch, onLongPress) }
                    }
                }
                CogRow { CogButton("Close", onClick = onClose) }
            }
        }
    }
}

@Composable
private fun ChoiceRow(title: String, sub: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(Steam.Soot.copy(alpha = 0.6f))
            .border(1.dp, Steam.BrassDark, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Steam.BrassLight)
        if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodyMedium, color = Steam.Parchment)
    }
}

// ───────────────────────── Archive (app drawer) ─────────────────────────

@Composable
private fun steamFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Steam.Parchment, unfocusedTextColor = Steam.Parchment,
    focusedBorderColor = Steam.BrassLight, unfocusedBorderColor = Steam.Brass,
    cursorColor = Steam.BrassLight,
    focusedPlaceholderColor = Steam.Steam.copy(alpha = 0.6f),
    unfocusedPlaceholderColor = Steam.Steam.copy(alpha = 0.6f),
    focusedContainerColor = Steam.Soot, unfocusedContainerColor = Steam.Soot,
)

@Composable
private fun DrawerScreen(
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.mapNotNull { a -> fuzzyScore(a.label, query)?.let { a to it } }
            .sortedWith(compareByDescending<Pair<AppInfo, Int>> { it.second }.thenBy { it.first.label.lowercase() })
            .map { it.first }
    }
    val calc = remember(query) { calcResult(query) }
    val webSearch = {
        open(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(query.trim()))))
    }

    Box(Modifier.fillMaxSize()) {
        SteamBackdrop()
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("THE ARCHIVE", style = MaterialTheme.typography.headlineMedium, color = Steam.BrassLight, modifier = Modifier.weight(1f))
                CogButton("Close", diameter = 68.dp, onClick = onClose)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search apps, or type a sum") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    val first = shown.firstOrNull()
                    if (first != null) onLaunch(first) else if (calc == null && query.isNotBlank()) webSearch()
                }),
                colors = steamFieldColors(),
            )
            Spacer(Modifier.height(12.dp))
            if (calc != null) {
                RivetedPanel(
                    Modifier.fillMaxWidth(),
                    onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("result", calc))
                        Toast.makeText(ctx, "Copied $calc", Toast.LENGTH_SHORT).show()
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "= $calc", fontSize = 28.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                            color = Steam.BrassLight, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                        Text("Tap to copy", style = MaterialTheme.typography.labelLarge, color = Steam.Steam)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (calc == null && query.isNotBlank() && shown.isEmpty()) {
                RivetedPanel(Modifier.fillMaxWidth(), onClick = webSearch) {
                    Column {
                        Text("Search the web", style = MaterialTheme.typography.titleMedium, color = Steam.BrassLight)
                        Text(
                            "No app matches \u201c${query.trim()}\u201d. Tap to search DuckDuckGo.",
                            style = MaterialTheme.typography.bodyMedium, color = Steam.Parchment,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(shown, key = { it.component.flattenToString() }) { app -> AppCell(app, onLaunch, onLongPress) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCell(app: AppInfo, onLaunch: (AppInfo) -> Unit, onLongPress: (AppInfo) -> Unit) {
    Column(
        Modifier.fillMaxWidth().combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onLongPress(app) }).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Porthole(app.icon, size = 62.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            app.label, fontSize = 12.sp, fontFamily = FontFamily.Serif, color = Steam.Parchment,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}
