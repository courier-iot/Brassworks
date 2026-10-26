# Brassworks: a steampunk Android home screen (launcher)

Replaces your device's home screen with a brass-and-leather look. Every installed app
is still available, shown in a riveted porthole.

## Get the APK without Android Studio
1. Create a new GitHub repo and upload this whole folder (keep the hidden `.github` folder).
2. Open the **Actions** tab and run **Build APK** (it also runs on every push).
3. Download the `brassworks-apk` artifact, unzip it, and install `app-debug.apk` (allow "install unknown apps").

## Make it your home screen
Press the Home button and choose **Brassworks > Always**, or go to
Settings > Apps > Default apps > Home app. To go back, pick your old launcher there.

## Using it
- **Floating gear (bottom right):** tap to open the Archive, double-tap for the Workshop menu (Arrange, Presets, Settings). Press and hold the gear, then drag, to move it anywhere on screen (portrait and landscape remember their own spot). Add a widget from Arrange.
- **Archive:** every installed app, with search. Long-press an app to add it to the App shelf, view app info, or uninstall.
- **Widgets:** press and hold empty space (or use Workshop > Arrange widgets) to move, resize, add or remove them.
  Drag a widget to move it, pull its bottom-right corner to resize, tap the red ✕ to remove it.
- **Widgets available:** Brass clock, Time plaque, Boiler pressure (battery), Cargo hold (storage),
  Almanac (calendar), Memorandum (notepad), App shelf, Folder, Chief Engineer's picks (your most-used apps).
- **Folders:** add a Folder widget, then long-press an app and choose *Add to folder*. Tap the folder to open it; rename it from inside. Each folder has its own apps, and you can have as many as you like.

## Layout
- **Fixed grid (default):** widgets snap to a 4-column grid and cannot overlap. Turn it off in Settings to let widgets float anywhere.
- **Presets:** Workshop > Presets. Four built-in layouts (Classic, Minimal, Engineer, Planner), plus your own.
  Save stores the current layout under a name (reuse a name to update it). Applying a preset keeps your notes and folder contents,
  and your previous layout is saved automatically as "Previous layout" so you can undo.
- **Nixie clock:** the clock widget is now a glowing nixie-tube display. The analogue dial is still available as "Brass dial".

## Power-user touches
- **Fuzzy search:** the Archive finds apps by prefix, word start, initials ("gm" finds Google Maps) and in-order letters ("clndr" finds Calendar).
- **Calculator:** type a sum in the Archive search box (for example `18*4+6`, `(2+3)^2`, `20%`) and tap the answer to copy it.
- **Web fallback:** if no app matches, tap to search DuckDuckGo. Pressing search on the keyboard does the same.
- **Hidden apps:** long-press an app and choose Hide. Hidden apps stay out of the Archive, search and picks.
  Settings > Hidden lists them so you can open or show them again. This tidies the view; it is not a lock.
- **App shortcuts:** long-press an app to see its shortcuts (for example "New message"). Android only provides these to the
  default home app, so Brassworks must be set as your home app.

## More tools
- **Engine room widget:** three dials for memory in use, battery heat and network flow (no extra permissions needed).
- **Share and import layouts:** Presets > Share sends your current layout as a text code. On another phone, Presets > Import adds it to that phone's presets.
- **Archive search:** fuzzy matching, a built-in calculator (type a sum, tap the result to copy) and a web-search fallback.
- **Hide apps and shortcuts:** long-press an app to hide it (Settings > Hidden brings it back) and to see its own shortcuts when Brassworks is the home app.
- **Themes:** Settings > Look. Four metals (Brass, Copper, Silver, Verdigris) restyle the whole home screen, and gear speed can be Slow, Normal or Fast.

## Fonts
Settings > Look. Choose Classic, Script, Plain or Typewriter, or upload your own .ttf/.otf (Custom). Free Victorian-style fonts to try: IM Fell English, Cinzel, Playfair Display, Almendra.

## Fonts and wallpaper
- Settings > Look. Fonts: Classic, Victorian, Script, Plain, Typewriter, or upload your own .ttf/.otf file.
- **Victorian** is Old Standard TT. The GitHub workflow downloads it into `app/src/main/assets/fonts/` when it builds
  (`OldStandard-Regular.ttf`, `OldStandard-Bold.ttf`, `OldStandard-Italic.ttf`). If you prefer, drop those three files in that folder yourself.
  Without them the Victorian button explains the font is missing and Classic stays on.
- **Wallpaper:** Picture sets your own image behind the widgets and the archive. The slider darkens it so text stays readable. Clear removes it.
- Credit: Old Standard TT, Copyright 2011 The Old Standard Project Authors, used under the SIL Open Font License 1.1.

## Nixie tubes
- **Nixie tubes** widget: just the four glowing tubes, no date, short enough for a single grid row. In 12-hour mode a small AM/PM tag glows beside the tubes.
- **Nixie clock** widget: the same tubes with the date underneath.
- **Glow colour:** Settings > Look > Nixie glow. Orange, Amber, Red, Pink, Violet, Blue, Green or White. It changes every tube at once.

## Renaming presets
Presets dialog > tap the pencil beside one of your own presets to rename it. Built-in presets keep their names, and a new name cannot match another preset.

## Archive sections and more colours
- The Archive groups apps under A, B, C ... (digits and symbols under #). A letter strip down the right edge jumps straight to a letter. While you are searching, the headers go away and you see ranked results.
- Four more metals in Theme: Amethyst (purple), Rose (pink), Cobalt (blue) and Ember (orange on charcoal), making eight in all.

## Settings screen
Double-tap the gear for the quick Workshop menu: Arrange (where you also add widgets), Presets and Settings. Change the metal in Settings > Look.
Settings opens a full screen with four sections:
- Look: metal, nixie glow, gear speed and turning gears, font, wallpaper.
- Layout: fixed grid, presets, share and import.
- Apps: hidden apps.
- System: default home app, Android settings, Brassworks' permissions, and a credit line.

## Solar system widget
A top-down map of the planets where they really are today, worked out on the phone with no internet.
It uses two of JPL's published orbit tables: Table 1 (best for 1800 to 2050) for today and the coming years, and Table 2a
(valid 3000 BC to 3000 AD, with extra correction terms for Jupiter, Saturn, Uranus and Neptune) from 2055 onward, blended in
smoothly between 2045 and 2055. Checked against each other the tables agree to about 0.4 degrees from 1900 to 2250, so positions are
good to about half a degree. This lives in its own file, SolarSystem.kt, which must sit next to Launcher.kt.
Tap a planet for its distance from the Sun and from Earth, how long light takes, and the length of its year.
Tap the Sun to switch between the inner system (Mercury to Mars, to scale) and the whole system (square-root scale so Neptune fits).
