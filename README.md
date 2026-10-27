# Yuva Launcher

Clean-room Android TV launcher for the Wzatco Yuva Go Max projector (Amlogic txhd2, 1 GB RAM, Android 13). No tracking, no ads, no paywalls.

## Design
- **Palette:** `#0C0A1C` canvas → `#171233` surface → `#FF8FB3` sakura-rose accent (60-30-10)
- **Structure:** single row of 8 app tiles + system row (HDMI / All Apps / Settings)
- **Focus:** 2dp sakura border + 1.08× scale, 120 ms (user-tunable)
- **Wallpaper:** user-picked image, downsampled to screen width to keep RAM low

## Build

Requires JDK 17 and either Android Studio or the Android command-line SDK.

```bash
cd C:\Users\bless\yuva_launcher
gradlew.bat assembleRelease
```

APK output: `app/build/outputs/apk/release/app-release.apk`

> First build will download Gradle 8.5 (~140 MB) and the Android build tools.
> If you don't have `gradlew.bat`, open the project in Android Studio once — it will generate the wrapper.

## Install

```bash
adb -s 192.168.68.104:5555 install -r app/build/outputs/apk/release/app-release.apk
adb -s 192.168.68.104:5555 shell cmd package set-home-activity com.yuva.launcher/.HomeActivity
```

Projectivy stays installed as a fallback. To revert:

```bash
adb -s 192.168.68.104:5555 shell cmd package set-home-activity com.spocky.projengmenu/.ui.home.MainActivity
```

## Customization keys

| What | Where |
|---|---|
| App order | long-press OK on a tile to move it right |
| Hide apps | Settings → Hidden apps |
| Wallpaper | Settings → Pick wallpaper (reads from `/sdcard/Pictures/` or anywhere via file picker) |
| Tile size | Settings → Tile size (0.8× – 1.2×) |
| Animation speed | Settings → Animation speed (0.3× – 1.5×) |

## File map

```
app/src/main/
├── AndroidManifest.xml        # HOME + LEANBACK_LAUNCHER category
├── java/com/yuva/launcher/
│   ├── HomeActivity.kt        # main screen, D-pad focus, wallpaper, clock
│   ├── AllAppsActivity.kt     # grid of every launchable app
│   ├── SettingsActivity.kt    # wallpaper picker, tile/anim sliders, hidden-apps list
│   ├── AppRepo.kt             # enumerate launchable apps (Leanback-first)
│   ├── TileAdapter.kt         # tile recycler with focus animation
│   ├── SystemAdapter.kt       # small system-row tiles
│   └── Prefs.kt               # SharedPreferences store, no DB
└── res/                       # colors, drawables, layouts (all original)
```

## Memory footprint target
On this 1 GB device, launcher PSS should stay under 25 MB at idle:
- No Firebase, no analytics, no Crashlytics
- No live-wallpaper engine; one bitmap, downsampled
- `itemAnimator = null` on recyclers for snappier focus
- `ndk abiFilters 'armeabi-v7a'` only — no 64-bit libs that this CPU can't use

## License
Original code, MIT. Wallpaper art (`projector_research/wallpaper_sakura_dusk.jpg`) is also original.
