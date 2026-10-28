package com.steampunk.brassworks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private class InfoSection(val heading: String, val body: String)

private val CREDITS = listOf(
    InfoSection(
        "Brassworks 1.0",
        "A steampunk home screen, written in Kotlin with Jetpack Compose. Brassworks has no internet permission of its own. " +
            "Links and web searches open in your browser, and your layouts, notes and settings stay on this phone.",
    ),
    InfoSection(
        "Victorian font",
        "Old Standard TT. Copyright 2011 The Old Standard Project Authors. Used under the SIL Open Font License, Version 1.1 " +
            "(openfontlicense.org). The font is downloaded when the app is built and is not changed.",
    ),
    InfoSection(
        "Planet positions",
        "Worked out on the phone from the orbital elements in \"Keplerian Elements for Approximate Positions of the Major Planets\" " +
            "by E. M. Standish, NASA Jet Propulsion Laboratory, Solar System Dynamics Group (Tables 1 and 2a).",
    ),
    InfoSection(
        "Sunrise and sunset",
        "Uses the method published in the Almanac for Computers (1990) by the Nautical Almanac Office, United States Naval Observatory.",
    ),
    InfoSection(
        "Moon phase",
        "Counted from the average length of a lunar month, starting from the new moon of 6 January 2000. It is good to about half a day.",
    ),
    InfoSection(
        "Software",
        "Jetpack Compose, AndroidX and Material 3 by Google, used under the Apache License, Version 2.0.",
    ),
    InfoSection(
        "Search",
        "If no app matches what you type in the Archive, you can search the web with DuckDuckGo. That happens in your browser, only when you tap.",
    ),
)

private val HELP = listOf(
    InfoSection(
        "The gear",
        "Tap it to open the Archive. Double-tap it for the Workshop menu. Press and hold it, then drag, to move it anywhere you like.",
    ),
    InfoSection(
        "Pages",
        "Swipe left or right on the home screen to change page, or tap the dots at the bottom. Workshop > Pages adds and removes pages. " +
            "Each page has its own widgets. Pressing Home takes you back to page 1.",
    ),
    InfoSection(
        "Arranging widgets",
        "Press and hold empty space, or choose Workshop > Arrange. Drag a widget to move it and pull its corner to resize. " +
            "The red cross removes it. Add puts a new one on the page you are on.",
    ),
    InfoSection(
        "Widget settings",
        "In Arrange mode, tap the cog on a widget. Depending on the widget you can choose a 12 or 24 hour clock, Celsius or Fahrenheit, " +
            "a title of your own, how solid its panel is, and which page it lives on. The Vials widget also lets you choose its readings.",
    ),
    InfoSection(
        "The Archive",
        "Every app, filed under A, B, C. Type to search: it finds initials (\"gm\" finds Google Maps) and does sums (try 18*4+6). " +
            "Long-press an app to pin it, put it in a folder, hide it, see its info or uninstall it.",
    ),
    InfoSection(
        "Folders and links",
        "Add a Folder or Link folder widget and tap it to open. To put an app in a folder, long-press it and choose Folder. " +
            "Link folders have an Add link button beside Close.",
    ),
    InfoSection(
        "Presets",
        "Workshop > Presets saves and applies layouts. A preset changes the page you are on, and your previous layout is kept so you can undo it. " +
            "Share sends a layout as a text code and Import adds one you received.",
    ),
    InfoSection(
        "Settings",
        "Workshop > Settings has the metal, nixie glow, gear speed, font, wallpaper, the fixed grid and its size (Auto, 5 × 10 or 4 × 9), hidden apps and your default home app.",
    ),
)

@Composable
private fun InfoDialog(title: String, sections: List<InfoSection>, onClose: () -> Unit, footer: String? = null) {
    Dialog(onDismissRequest = onClose) {
        RivetedPanel(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Steam.BrassLight)
                Column(
                    Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    sections.forEach { s ->
                        Column {
                            Text(s.heading, style = MaterialTheme.typography.titleMedium, color = Steam.Parchment)
                            Text(s.body, style = MaterialTheme.typography.bodyMedium, color = Steam.Steam)
                        }
                    }
                }
                if (footer != null) {
                    Text(
                        footer, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelLarge, fontSize = 11.sp,
                        fontStyle = FontStyle.Italic, color = Steam.Steam.copy(alpha = 0.6f),
                    )
                }
                CogRow { CogButton("Close", onClick = onClose) }
            }
        }
    }
}

/** Credits and licences. */
@Composable
fun CreditsDialog(onClose: () -> Unit) = InfoDialog(
    "CREDITS", CREDITS, onClose,
    footer = "Ruthlessness is mercy upon ourselves. Adapt to the times, and fight for what is right.",
)

/** A short guide to every gesture. */
@Composable
fun HelpDialog(onClose: () -> Unit) = InfoDialog("HELP", HELP, onClose)
