package com.steampunk.brassworks

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Environment
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
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

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

// ───────────────────────── Widgets: model ─────────────────────────

enum class WType(
    val title: String, val blurb: String,
    val defW: Float, val defH: Float, val minW: Float, val minH: Float,
    val tip: String? = null,
) {
    CLOCK("Brass clock", "Analogue dial with the time", 340f, 170f, 150f, 120f, "Recommended: the classic centrepiece"),
    DIGITAL("Time plaque", "Large digital time and date", 340f, 100f, 150f, 80f),
    BOILER("Boiler pressure", "Battery level on a dial gauge", 164f, 140f, 110f, 110f, "Recommended: your charge at a glance"),
    CARGO("Cargo hold", "Storage used and free", 164f, 140f, 110f, 110f, "Recommended: spot a full phone early"),
    ALMANAC("Almanac", "Month calendar with today marked", 340f, 250f, 200f, 200f, "Recommended: the month at a glance"),
    MEMO("Memorandum", "A notepad you can type in", 164f, 140f, 150f, 100f, "Recommended: jot things down fast"),
    SHELF("App shelf", "Apps you choose, one tap away", 340f, 130f, 110f, 100f, "Recommended: replaces the old dock"),
    FOLDER("Folder", "A named bundle of apps. Tap to open it", 164f, 150f, 110f, 110f, "Recommended: keeps related apps tidy"),
    PICKS("Chief Engineer's picks", "Apps you open most, learned on this phone", 340f, 130f, 110f, 100f, "Recommended: suggestions from your habits"),
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

/** Holds every widget's position and size (in dp) and saves them. */
class WidgetBoard(private val prefs: SharedPreferences) {
    var items by mutableStateOf(decode(prefs.getString("widgets_v1", null)))
    var areaW by mutableFloatStateOf(0f)
    var areaH by mutableFloatStateOf(0f)

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

    fun ensureDefaults() {
        if (items != null || areaW <= 0f) return
        val w = areaW
        val half = (w - 12f) / 2f
        val t = System.currentTimeMillis()
        items = listOf(
            WidgetItem(t, WType.CLOCK, 0f, 0f, w, 170f),
            WidgetItem(t + 1, WType.BOILER, 0f, 182f, half, 140f),
            WidgetItem(t + 2, WType.CARGO, half + 12f, 182f, half, 140f),
            WidgetItem(t + 3, WType.SHELF, 0f, 334f, w, 130f),
        )
        persist()
    }

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

    fun add(type: WType): Long {
        val n = items?.size ?: 0
        val w = minOf(type.defW, areaW)
        val h = type.defH
        val x = ((areaW - w) / 2f).coerceAtLeast(0f)
        val y = (24f + (n % 6) * 28f).coerceAtMost(maxOf(0f, areaH - h))
        val id = System.currentTimeMillis()
        items = (items ?: emptyList()) + WidgetItem(id, type, x, y, w, h)
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

    // Pressing the Home button closes everything and returns to the main screen.
    LaunchedEffect(homeSignal) {
        drawer = false; menuApp = null; settings = false; fabMenu = false; picker = false; editing = false
        openFolder = null; menuFolder = null; folderPick = null
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

    val launch: (AppInfo) -> Unit = { app ->
        counts = counts + (app.packageName to ((counts[app.packageName] ?: 0) + 1))
        prefs.edit().putString("counts", counts.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()
        launchApp(ctx, app)
    }

    CompositionLocalProvider(LocalAnimations provides animations) {
        Surface(Modifier.fillMaxSize(), color = Steam.Soot, contentColor = Steam.Parchment) {
            Box(Modifier.fillMaxSize()) {
                HomeScreen(
                    board = board, apps = apps, pinned = pinned, counts = counts, tick = tick,
                    prefs = prefs, editing = editing,
                    onLaunch = launch,
                    onLongPress = { menuApp = it; menuFolder = null },
                    onOpenFolder = { openFolder = it },
                    onFabTap = { fabMenu = true },
                    onFabHold = { drawer = true },
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
                        apps = apps,
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
                                (if (menuFolder != null) " Eject takes it out of this folder." else ""),
                            style = MaterialTheme.typography.labelLarge, color = Steam.Steam, textAlign = TextAlign.Center,
                        )
                        CogRow {
                            CogButton(if (onShelf) "Unpin" else "Pin") {
                                setPinned(if (onShelf) pinned - app.packageName else pinned + app.packageName)
                                menuApp = null
                            }
                            CogButton("Folder") { folderPick = app; menuApp = null }
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
                        Text("Hold the gear to open the archive. Add places a new widget; Arrange moves and resizes them.", style = MaterialTheme.typography.labelLarge, color = Steam.Steam)
                        CogRow {
                            CogButton("Add") { fabMenu = false; picker = true }
                            CogButton("Arrange") { fabMenu = false; editing = true }
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
                                board.addToFolder(id, app.packageName)
                                Toast.makeText(ctx, "Created a new folder on your home screen", Toast.LENGTH_SHORT).show()
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
                                PickerRow(t) { board.add(t); picker = false; editing = true }
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
                        Text("Home sets your default home app. Device opens Android settings.", style = MaterialTheme.typography.labelLarge, color = Steam.Steam)
                        CogRow {
                            CogButton("Home") { open(ctx, Intent(Settings.ACTION_HOME_SETTINGS)) }
                            CogButton("Device") { open(ctx, Intent(Settings.ACTION_SETTINGS)) }
                            CogButton("Close") { settings = false }
                        }
                    }
                }
            }
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
            board.items.orEmpty().forEach { w ->
                key(w.id) {
                    WidgetHost(w, board, editing) {
                        WidgetBody(w, board, now, is24, battery, storage, apps, pinned, counts, prefs, editing, onLaunch, onLongPress, onOpenFolder)
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
                            onDragEnd = { board.persist() },
                            onDragCancel = { board.persist() },
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
                            onDragEnd = { board.persist() },
                            onDragCancel = { board.persist() },
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
    prefs: SharedPreferences,
    editing: Boolean,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onOpenFolder: (Long) -> Unit,
) {
    RivetedPanel(Modifier.fillMaxSize()) {
        when (w.type) {
            WType.CLOCK -> ClockContent(now, is24)
            WType.DIGITAL -> DigitalContent(now, is24)
            WType.BOILER -> GaugeContent(battery, true, "Boiler pressure", "${(battery * 100).toInt()}% charged")
            WType.CARGO -> GaugeContent(storage.first, false, "Cargo hold", "${(storage.first * 100).toInt()}% full")
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
                apps.filter { (counts[it.packageName] ?: 0) > 0 }.sortedByDescending { counts[it.packageName] ?: 0 }.take(8),
                "Open a few apps and I will learn which you favour.",
                onLaunch, onLongPress,
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
    if (maxWidth > maxHeight * 1.3f) {
        val dial = minOf(maxHeight, maxWidth * 0.5f)
        val textW = maxWidth - dial - 12.dp
        val fs = minOf(textW.value * 0.27f, maxHeight.value * 0.45f).coerceIn(18f, 48f)
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
            AnalogClock(hour, minute, Modifier.size(minOf(maxWidth, maxHeight * 0.68f)))
            Spacer(Modifier.height(4.dp))
            TimeLine(time, ampm, (maxHeight.value * 0.14f).coerceIn(14f, 28f))
        }
    }
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
    if (maxWidth > maxHeight * 1.35f) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            PressureGauge(value, Modifier.size(minOf(maxHeight, maxWidth * 0.45f)), dangerLow)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = Steam.BrassLight, maxLines = 1)
                Text(caption, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            }
        }
    } else {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            PressureGauge(value, Modifier.size(minOf(maxWidth, maxHeight * 0.62f)), dangerLow)
            Spacer(Modifier.height(2.dp))
            Text(title, fontSize = 14.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Steam.BrassLight, maxLines = 1)
            Text(caption, fontSize = 12.sp, fontFamily = FontFamily.Serif, color = Steam.Steam, maxLines = 1)
        }
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
                        app.icon, size = 48.dp,
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
        Text(sub, style = MaterialTheme.typography.bodyMedium, color = Steam.Parchment)
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
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
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
                placeholder = { Text("Search the archive") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { shown.firstOrNull()?.let(onLaunch) }),
                colors = steamFieldColors(),
            )
            Spacer(Modifier.height(12.dp))
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
