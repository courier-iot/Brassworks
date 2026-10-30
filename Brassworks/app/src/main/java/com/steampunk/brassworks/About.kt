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
        "A steampunk home screen, written in Kotlin with Jetpack Compose. The only thing in Brassworks that goes online is the Weather widget. " +
            "Links and web searches open in your browser, and your layouts, notes and settings stay on this phone.",
    ),
    InfoSection(
        "Weather",
        "Weather data by Open-Meteo.com, used under the Creative Commons Attribution 4.0 licence (CC BY 4.0). " +
            "The Weather widget sends only your location, rounded to about a kilometre, to open-meteo.com, and keeps the last answer on the phone.",
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
        "Sound effects",
        "The sounds were made for Brassworks and are built from simple generated tones and noise. They are played on the phone and nothing is recorded.",
    ),
    InfoSection(
        "Software",
        "Jetpack Compose, AndroidX and Material 3 by Google, used under the Apache License, Version 2.0.",
    ),
    InfoSection(
        "Notification access",
        "Used only by the Music player. It lets Brassworks read which track is playing, so it can show the title and progress. " +
            "It reads only the media information and nothing else, and nothing leaves your phone. Play, pause, next and previous need no permission.",
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
        "Swipe left or right on the home screen to change page, or tap the dots at the bottom. Workshop > Pages adds, removes and reorders pages, and can give a page its own wallpaper. " +
            "Each page has its own widgets. Pressing Home takes you back to page 1. In Arrange mode, drag a widget past the left or right edge to send it to the next page.",
    ),
    InfoSection(
        "Arranging widgets",
        "Press and hold empty space, or choose Workshop > Arrange. Drag a widget to move it and pull its corner to resize. " +
            "The red cross removes it. Add puts a new one on the page you are on.",
    ),
    InfoSection(
        "Widget settings",
        "In Arrange mode, tap the cog on a widget. Depending on the widget you can choose a 12 or 24 hour clock, Celsius or Fahrenheit, " +
            "a title of your own, how solid its panel is, and which page it lives on. The Vials widget also lets you choose its readings and a colour for each. Tap a vial to see its exact figure.",
    ),
    InfoSection(
        "The Archive",
        "Every app, filed under A, B, C. Type to search: it finds initials (\"gm\" finds Google Maps) and does sums (try 18*4+6). " +
            "Long-press an app to pin it, put it in a folder, hide it, see its info or uninstall it.",
    ),
    InfoSection(
        "Music player",
        "Add a Music player and resize it: wide and short is a strip, taller and narrower is a square card. " +
            "The buttons control whatever music app is playing. For the track name and progress, tap the prompt on the widget and switch on Notification access once. " +
            "Tap the record or the title to open the music app. If several players are running, the widget's cog lets you choose which one to follow.",
    ),
    InfoSection(
        "Memorandum",
        "One widget can hold several notes. Tap the three dots in its heading to add a note or a checklist, switch the one you are on between Text and Check, or delete it with the cross, " +
            "and use the arrows to move between notes. In a list, tap a line to tick it and the cross to remove it. Notes you wrote before still open as they were.",
    ),
    InfoSection(
        "Countdown, Agenda and Lantern",
        "Countdown is a timer and an alarm clock: tap its title to switch between them. They use Android's alarm service, so they ring even when the launcher is closed. " +
            "Android asks once if the launcher may send notifications, which is how it rings. Coming back to the home screen, tapping the notification or pressing Quiet stops the sound. " +
            "Agenda lists what is coming up in your calendar after you tap Allow. Lantern switches your torch on and off with a tap and needs no permission. " +
            "A countdown has three slots (tap 1, 2 or 3 beside its title; a dot means it is running), and when one rings you can press Snooze for five minutes. " +
            "The cog on a Countdown chooses its ringtone. The cog on a Lantern can put the torch out by itself after a few minutes and, on phones with Android 13 or later that allow it, dim it.",
    ),
    InfoSection(
        "Weather",
        "The Weather widget shows now and the next three days. Tap Allow once so it can find roughly where you are; it uses the internet only for this, " +
            "and sends only your location rounded to about a kilometre. The cog sets Celsius or Fahrenheit.",
    ),
    InfoSection(
        "Widgets from other apps",
        "Add > Widget from another app lists the widgets your other apps offer. Android may ask once to let Brassworks show it, and some open their own set-up screen first. " +
            "They are drawn by the other app, so they keep its look rather than the brass one. Move and resize them in Arrange mode. " +
            "They belong to this phone, so presets and backups leave them out.",
    ),
    InfoSection(
        "Sound and steam",
        "Settings > Look has Sound effects and Steam puffs, both off until you switch them on. The sounds are soft brass clicks and clunks for opening an app, the gear, the Workshop, arranging, putting a widget down and changing page, " +
            "and they follow your phone's volume, so silent mode mutes them. Steam puffs rise from the gear when you open the Workshop or start arranging.",
    ),
    InfoSection(
        "Recent apps and Brass icons",
        "The Archive shows the apps you opened lately in a Recent row at the top. Settings > Apps turns the row off or clears it. " +
            "Settings > Look > Brass icons draws every app icon in the colour of your metal so they match the theme.",
    ),
    InfoSection(
        "Folders and links",
        "Add a Folder or Link folder widget and tap it to open. To put an app in a folder, long-press it and choose Folder. " +
            "Link folders have an Add link button beside Close.",
    ),
    InfoSection(
        "Presets",
        "Workshop > Presets saves and applies layouts. A preset changes the page you are on, and your previous layout is kept so you can undo it. " +
            "Neon Diesel, Gaslight Study, Sea Captain, Night Shift and Corp Blackout come with a colour scheme and style as well, but never touch your wallpaper. " +
            "A preset of yours can also save your colours and style and your wallpaper, using the switches when you save. " +
            "Share sends a layout and its colours as a text code, never the wallpaper, and Import adds one you received.",
    ),
    InfoSection(
        "Settings",
        "Workshop > Settings has the style (Steampunk, Dieselpunk or Corp HUD), the metal, nixie glow, gear speed, font, wallpaper, your own mixed colours, the fixed grid and its size (Auto, 5 × 10 or 4 × 9), hidden apps and your default home app.",
    ),
    InfoSection(
        "Backup and reset",
        "Settings > Backup and reset. Backup saves everything to one file wherever you choose, and Restore reads one back in. " +
            "Layout starts the widgets again from the Classic layout. Defaults puts the colours, style, font, wallpaper and gear speed back to how Brassworks came. " +
            "Each of them asks before it changes anything. About once a month Brassworks offers to make a backup; Settings has a switch to turn that off. " +
            "If Brassworks ever fails to start several times, it starts again from the Classic layout and keeps a copy of how things were. Rescued, in the same place, brings it back.",
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
