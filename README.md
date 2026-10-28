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
- **Floating gear (bottom right):** tap to open the Archive, double-tap for the Workshop menu (Arrange, Presets, Pages, Settings). Press and hold the gear, then drag, to move it anywhere on screen (portrait and landscape remember their own spot). Add a widget from Arrange.
- **Archive:** every installed app, with search. Long-press an app to add it to the App shelf, view app info, or uninstall.
- **Widgets:** press and hold empty space (or use Workshop > Arrange widgets) to move, resize, add or remove them.
  Drag a widget to move it, pull its bottom-right corner to resize, tap the red ✕ to remove it.
- **Widgets available:** Brass clock, Time plaque, Boiler pressure (battery), Cargo hold (storage),
  Vials (glass tubes), Music player, Almanac (calendar), Memorandum (notepad), App shelf, Folder, Link folder, Chief Engineer's picks (your most-used apps).
- **Folders:** add a Folder widget, then long-press an app and choose *Add to folder*. Tap the folder to open it; rename it from inside. Each folder has its own apps, and you can have as many as you like.

- **Link folders:** add a Link folder widget and tap it to open. Tap Add link (beside Close) and type or paste a web address, with an optional title; a missing https:// is added for you. Tap a link to open it in your browser, the pencil edits it and the red cross deletes it. Rename the folder from inside. You can have as many as you like, each with its own links. This lives in its own file, LinkFolder.kt, which must sit next to Launcher.kt.

## Styles
Settings > Look > **Style** switches the whole look. Nothing is lost, and you can switch back at any time.
- **Steampunk** (the original): dark wood, brass and rivets.
- **Dieselpunk**: gunmetal panels with cut corners, a neon line set in from the edge, worn corners, scratches and oil streaks, steel bolts, and riveted steel plates on the background. The home gear and the cogs get a neon ring, and the Vials get a neon halo.
- The neon colour is your **Nixie glow** choice, and your **Metal** choice tints the steel (Cobalt gives blue steel, Copper warm steel). Your font, wallpaper and widget layout are untouched.
- The look is applied through the shared panel, so every widget, dialog and screen picks it up. The grime is different on each panel.

## Your own colours
Settings > Look > **Your own colours** has three sets of sliders (Hue, Strength, Brightness):
- **Metal** and **Background** together make a **Custom** theme: the metal colour, plus lighter and darker shades of it, on a background of your colour.
- **Neon** makes a **Custom** nixie glow, which colours the nixie tubes, the Music player and the neon edges in Dieselpunk.
- The sliders apply as you move them and are saved when you let go. They are limited to ranges that stay readable. Tapping one of the ready-made metals or glows switches back to it and the sliders start again from that colour, so your own colours are kept until you move a slider again.
- In Dieselpunk the metal only tints the steel, so the neon is where your own colour shows most.

## Presets that carry a look
A preset of yours can hold more than a layout. In **Presets > Save** there are two switches:
- **Save colours and style:** the style (Steampunk or Dieselpunk), metal, nixie colour and your own mixed colours.
- **Save wallpaper:** the picture behind your widgets and how dark it is. A private copy is kept, so changing or clearing your wallpaper later does not affect the preset. If no wallpaper is set when you save, applying the preset clears the wallpaper.
- Switch either off to leave that part alone when the preset is applied. Layout-only presets (including the built-in ones) never change your colours or wallpaper.
- A preset's look applies to the whole launcher, not just the page you are on. The Presets list shows "colours", "wallpaper" or both beside each preset that carries one.
- **Previous layout** (the undo point made every time you apply a preset) now also remembers your colours and wallpaper, so one tap puts everything back.
- **Share** sends the layout and the colours as a text code and **Import** brings them in. Wallpaper pictures are never shared. Colours from a shared code only replace your own custom colours if the code uses a Custom theme or glow.
- Deleting a preset also deletes its saved wallpaper copy. This lives in Look.kt, which must sit next to Launcher.kt.

## Pages
- **Swipe** left or right on the home screen to change page, or tap the dots at the bottom. Pressing Home returns you to page 1.
- **Workshop > Pages** lists your pages (up to 8), adds a new empty one and removes the page you are on (it asks first, and its widgets, notes and folders go with it).
- Each page has its own widgets. Add, Arrange and Presets work on the page you are on. A folder can be filled from any page.
- To move a widget to another page, use its cog in Arrange mode (see below). Notes and settings go with it.

## Widget settings
In Arrange mode every widget has a brass cog beside its red cross. Tap it for settings that belong to that widget only:
- **Hour format** (clocks and the Solar phase widget): follow the phone, 12-hour or 24-hour.
- **Temperature** (Engine room, and the Heat vial): Celsius or Fahrenheit.
- **Readings** (Vials): choose which tubes the widget shows, see below.
- **Title** (Boiler pressure, Cargo hold, Barometer, Memorandum, App shelf, Chief Engineer's picks): type your own. Blank goes back to the original. Folders and Link folders are renamed from inside.
- **Opacity:** how solid the panel behind the widget is, down to 20%, so a wallpaper can show through.
- **Move to page:** sends the widget to a free spot on another page.
Reset puts that widget back to its defaults. This lives in its own file, WidgetSettings.kt, which must sit next to Launcher.kt.

## Help and credits
Settings > System has **Help** (a short guide to every gesture) and **Credits** (the font licence and the sources for the planet, sunrise and moon maths). These live in About.kt, which must sit next to Launcher.kt.

## Layout
- **Fixed grid (default):** widgets snap into cells and cannot overlap. Turn it off in Settings to let widgets float anywhere.
- **Grid size** (Settings > Layout, when the fixed grid is on): **Auto** fits as many cells as the screen allows, **5 × 10** gives five columns by ten rows, and **4 × 9** gives four columns by nine rows. The fixed sizes turn with the phone, so 5 × 10 becomes 10 × 5 and 4 × 9 becomes 9 × 4 on its side, which keeps the cells roughly square. Changing size re-snaps the page you are on straight away, and your other pages and the other orientation the next time you visit them. Widgets keep their place on screen as closely as the new cells allow, and nothing is deleted.
- **Presets:** Workshop > Presets. They apply to the page you are on. Four built-in layouts (Classic, Minimal, Engineer, Planner), plus your own.
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
- **Music player widget:** a neon record player for whatever music app is playing. The neon colour follows your nixie colour (Settings > Look > Nixie glow).
  - **Shape:** resize it. Wide and short is a strip with the record on the left; taller and narrower is a square card with the record on top.
  - **Record:** it spins while music plays, and its label takes a new random tint every time the track changes. There is no album art, by design.
  - **Buttons:** previous, play/pause and next control the current music app and need no permission. The centre button shows what a tap will do: a pause symbol while music is playing, a play symbol when it is not.
  - **Track info:** the title, artist and the thin progress line need **Notification access**, which you switch on once. Until then the widget says "Now playing" with a "Tap to show track info" prompt that opens Android's Notification access screen (also reachable from the widget's cog). On some phones Android first asks you to allow restricted settings for Brassworks in its App info, because the app is sideloaded. Brassworks reads only the media information (track, artist, position), nothing else, and nothing leaves the phone.
  - **Progress line:** a thin neon line under the title that fills as the track plays. It is display-only, not a seek bar.
  - This lives in its own file, Music.kt, which must sit next to Launcher.kt, and it adds a service entry to AndroidManifest.xml (included).
- **Vials widget:** glass tubes that fill from left to right, in one compact widget. Add it, then in Arrange mode tap its cog and choose up to six readings, in the order you want: Boiler (battery charge), Cargo (storage used), Memory (RAM in use), Heat (battery temperature), Pressure (air pressure, needs a sensor) and Day (how much of today has passed). The liquid is green while things are healthy, turns amber, then copper as battery gets low or storage, memory or heat run high. Pressure and Day are always brass. No extra permissions are needed, and the pressure sensor is only switched on while a Pressure vial is chosen. This lives in its own file, Vials.kt, which must sit next to Launcher.kt.
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
Double-tap the gear for the quick Workshop menu: Arrange (where you also add widgets), Presets, Pages and Settings. Change the metal in Settings > Look.
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
