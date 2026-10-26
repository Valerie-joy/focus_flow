# Play Store listing assets

Graphics for the Google Play store listing (Play Console → Grow users → Store
presence → Main store listing). Nothing here ships inside the APK.

| File | Play field | Size |
|---|---|---|
| `app-icon-512.png` | App icon | 512×512, ≤1 MB |
| `feature-graphic-1024x500.png` | Feature graphic | 1024×500, ≤15 MB |
| `screenshots/*.png` | Phone screenshots | 1080×1920, ≤8 MB each, 2–8 of them |

## App icon

Not a separate drawing. `tools/make_graphics.py` reproduces the real launcher
icon from its vectors — `res/drawable/ic_launcher_background.xml` (a `#CFE9E6`
square) and `ic_launcher_foreground.xml` (a `#1F6F6B` ring centred at 54,54
with outer radius 24 and inner radius 12 on a 108 viewport) — so the store
icon and the home-screen icon are the same mark. If the launcher vectors
change, re-run the script rather than editing the PNG.

## Feature graphic

Hand-built in the app's palette (`ui/theme/Color.kt`). Functional, not
art-directed — replace it freely; it is the one asset here with no source of
truth elsewhere in the repo.

## Screenshots

Captured from the running app on an emulator, not mocked up:

```bash
adb exec-out screencap -p > raw-shots/06-dashboard.png     # repeat per screen
python tools/make_screenshots.py raw-shots
```

The emulator panel is 1080×2400 (9:20), taller than the 9:16 Play asks for.
`make_screenshots.py` scales each capture to fit the 1920 height and fills the
side margins with the screen's own background colour, sampled from the capture
itself. It does not centre-crop: cropping to 9:16 would cut the app bar off the
top and the primary button off the bottom. The padding matches the app, so it
reads as part of the screen rather than as a letterbox.

**The current set was captured with every category skipped**, on an emulator
whose virtual camera has no face to track — so every score reads 49% and the
header says "Based on 0 of 5 assessments". Accurate, but it undersells the app.
Replacing these with captures from one real session on a physical device is
worth doing before the listing goes live; re-run the script on the new files
and the sizing is handled.

Play shows the first few screenshots in search results, so lead with the
dashboard, results and suggestions. Four or more at ≥1080 px on each side also
makes the listing promotion-eligible.

## Regenerating

Both scripts need Pillow and write back into this folder:

```bash
python tools/make_graphics.py
```
