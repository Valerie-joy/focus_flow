# Running FocusFlow in Android Studio

This zip now includes the full Gradle project scaffolding (previously it
was only the `com.focusflow` source tree). One thing I couldn't generate:
the Gradle wrapper's binary jar (`gradle/wrapper/gradle-wrapper.jar`) and
the `gradlew`/`gradlew.bat` scripts — those are binary/executable files,
not something I can write as text. Android Studio replaces them for you
automatically the first time you open the project, so this is a non-issue
in practice — just follow the steps below in order.

## Steps

1. **Unzip** this somewhere clean, e.g. `C:\Users\you\focusflow-complete`.
2. Open Android Studio → **File → Open** → select the unzipped
   `focusflow-complete` folder (the one containing `settings.gradle.kts`)
   → **OK**.
3. Android Studio will detect there's no Gradle wrapper and prompt you —
   accept its offer to **create/trust the Gradle wrapper**. If it doesn't
   prompt automatically, go to **File → Sync Project with Gradle Files**;
   Android Studio will fetch Gradle 8.7 per
   `gradle/wrapper/gradle-wrapper.properties` and generate the wrapper
   files itself.
4. Let the sync finish (first sync downloads dependencies — CameraX, ML
   Kit, Room, Compose — so it can take a few minutes on first run).
5. If prompted to fix a **JDK version**, use JDK 17 (Android Studio's
   bundled JDK works fine — `File → Project Structure → SDK Location →
   Gradle Settings → Gradle JDK`).
6. Once sync succeeds, run **Build → Make Project** first to confirm it
   compiles clean before running on a device.
7. Pick an emulator or physical device (API 26+, and a device/emulator
   with a **front camera** for Phase 4/5 — most emulator profiles have one
   by default) from the device dropdown next to the Run button.
8. Press **Run ▶** (or Shift+F10).

## What was missing and what I just added

| File | Why it's needed |
|---|---|
| `settings.gradle.kts`, `build.gradle.kts` (root) | Declares the project + plugin versions |
| `app/build.gradle.kts` | Every dependency from all 10 stages (Compose, Navigation, CameraX, ML Kit, Room) — this is the file the README's "Setup" section was describing piece by piece; it's now assembled in one place |
| `app/src/main/AndroidManifest.xml` | Camera permission, legacy storage permission, the `FileProvider` entry Stage 9's PDF/email export needs, and the app's entry point |
| `app/src/main/java/com/focusflow/MainActivity.kt` | **This didn't exist before** — it's the actual entry point: installs `FocusFlowTheme` and wires all 5 nav graphs (`authGraph`, `onboardingGraph`, `registrationGraph`, `assessmentGraph`, `profileGraph`) into one `NavHost`. Without this, there was nothing telling Android what screen to show first. |
| `res/values/strings.xml`, `themes.xml` | App name + a base XML theme the manifest requires (Compose's `FocusFlowTheme` handles the actual in-app look) |
| `res/mipmap-anydpi-v26/ic_launcher*.xml` + `res/drawable/ic_launcher_*.xml` | A simple vector-based launcher icon so the app has one at all — feel free to replace with a real icon via **Right-click `res` → New → Image Asset** |

## If Gradle sync still fails

The most common cause is an Android Gradle Plugin / Gradle version
mismatch with your installed Android Studio version. If sync complains
about AGP 8.5.2 or Gradle 8.7 being too new/old for your Android Studio,
tell me your Android Studio version and I'll adjust the versions in
`build.gradle.kts` / `gradle-wrapper.properties` to match.

**"Starting in Kotlin 2.0, the Compose Compiler Gradle plugin is
required"** — already fixed in this zip: since Kotlin 2.0, Compose
compilation is its own Gradle plugin
(`org.jetbrains.kotlin.plugin.compose`) rather than the old
`composeOptions { kotlinCompilerExtensionVersion = ... }` setting. Both
`build.gradle.kts` files now declare/apply it. If you'd pulled an earlier
copy of this zip before this fix, just re-download.
