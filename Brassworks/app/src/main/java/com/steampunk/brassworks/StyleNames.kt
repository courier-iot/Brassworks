package com.steampunk.brassworks

/**
 * Widget names follow the style you are using. Steampunk keeps the original brass names (the ones in [WType.title]),
 * Dieselpunk uses workshop and field names, and Corp HUD uses readout names. A title you type yourself always wins.
 * Anything not listed here keeps its Steampunk name.
 */
private val DIESEL_NAMES: Map<WType, String> = mapOf(
    WType.ANALOG to "Steel dial",
    WType.DIGITAL to "Time plate",
    WType.BOILER to "Generator charge",
    WType.CARGO to "Cargo bay",
    WType.VIALS to "Coolant tubes",
    WType.MUSIC to "Wireless set",
    WType.ENGINE to "Engine bay",
    WType.COMPASS to "Heading dial",
    WType.BAROMETER to "Pressure dial",
    WType.MOON to "Moon dial",
    WType.SUN to "Daylight dial",
    WType.WEATHER to "Weather station",
    WType.SOLAR to "Planet map",
    WType.CHRONO to "Stopwatch dial",
    WType.TIMER to "Fuse timer",
    WType.ALMANAC to "Calendar board",
    WType.AGENDA to "Duty roster",
    WType.LANTERN to "Floodlight",
    WType.MEMO to "Field notes",
    WType.SHELF to "Tool rack",
    WType.FOLDER to "Crate",
    WType.LINKS to "Wire list",
    WType.PICKS to "Foreman's picks",
    WType.APPWIDGET to "Outside gauge",
    WType.WORLD to "Zone clocks",
    WType.CONTACTS to "Telephone board",
)

private val CORP_NAMES: Map<WType, String> = mapOf(
    WType.CLOCK to "Local time",
    WType.NIXIE to "Time readout",
    WType.ANALOG to "Chrono dial",
    WType.DIGITAL to "Time display",
    WType.BOILER to "Power cell",
    WType.CARGO to "Data storage",
    WType.VIALS to "Vitals",
    WType.MUSIC to "Audio deck",
    WType.ENGINE to "System load",
    WType.COMPASS to "Nav bearing",
    WType.BAROMETER to "Atmos pressure",
    WType.MOON to "Lunar phase",
    WType.SUN to "Daylight cycle",
    WType.WEATHER to "Atmos",
    WType.SOLAR to "Orbital map",
    WType.CHRONO to "Stopwatch",
    WType.TIMER to "Timer",
    WType.ALMANAC to "Calendar grid",
    WType.AGENDA to "Schedule feed",
    WType.LANTERN to "Light",
    WType.MEMO to "Notes",
    WType.SHELF to "Quick access",
    WType.FOLDER to "Data folder",
    WType.LINKS to "Link cache",
    WType.PICKS to "Priority apps",
    WType.APPWIDGET to "External widget",
    WType.WORLD to "World time",
    WType.CONTACTS to "Comms",
    WType.LOGBOOK to "Usage log",
)

/** The name to show for this widget in the style you are using now. */
fun WType.label(): String = when (Steam.style) {
    "diesel" -> DIESEL_NAMES[this] ?: title
    "corp" -> CORP_NAMES[this] ?: title
    else -> title
}
