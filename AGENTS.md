# Working with ZFL Launcher

These instructions apply to the entire repository. Nested AGENTS.md files refine the rules for their directories; explicit user instructions take precedence.

## Working approach

- Work autonomously: take a clear task through to a working result, choosing a simple, practical solution yourself.
- Inspect the existing implementation first. Search with `rg` and trace data source → repository/model → ViewModel/`StateFlow` → Compose UI → device. Do not invent Android, AndroidX, or Compose capabilities; check the API level they require.
- If the project is under git, check `git status` before editing and preserve other people's uncommitted changes. Do not revert, reformat, or clean unrelated files.
- Fix the cause in the shared component, model, or repository. Do not hide defects with screen-specific workarounds, hardcoded offsets, or device-specific hacks.
- Avoid unnecessary scripts, backups, dependencies, and abstractions. Plan complex tasks, not every edit.
- Ask only when ambiguity materially affects the result or before actions with major, hard-to-reverse consequences: destroying data/history, wiping the user's layout or app data on the device, uninstalling apps, sending private data externally, or publishing secrets/signing keys.
- Communicate briefly. Finish with the result, checks performed, and specific remaining limitations.

## Project principles (non-negotiable)

- **No ads, trackers, analytics, crash reporters, Firebase, or Google Play Services.** Do not add SDKs that phone home, even "optional" or "disabled by default" ones.
- Everything that can be done locally is done locally. The `INTERNET` permission is not declared; it may only arrive together with the weather widget (Open-Meteo, no API keys) and must not be used for anything else.
- Every permission must be justified by a real feature and commented in `AndroidManifest.xml`. `MANAGE_EXTERNAL_STORAGE` is used only to read the wallpaper bitmap; never read, scan, or write the user's files with it.
- Dependencies: AndroidX, Kotlin, and Compose from `google()`/`mavenCentral()` only, with exact versions. Prefer the platform and existing code over a new library; justify every new dependency in the final report.
- The project is open source (GPL-3.0-or-later): code, comments, KDoc, identifiers, and commit messages are in English. Explicit types on public API.
- User-facing text lives in `res/values/strings.xml` (English) with translations in `values-xx/` (at least `values-ru`). No hardcoded UI strings in Kotlin.

## Project structure

ZFL Launcher is a single-module Android app (`:app`) in Kotlin + Jetpack Compose, not an AOSP Launcher3 fork. minSdk 31, target/compileSdk 37, JDK 17. See `app/build.gradle.kts` for current dependencies.

- `app/src/main/java/com/zfl/launcher/`: `MainActivity` (HOME activity), `SettingsActivity`, `LauncherViewModel`.
- `data/`: app list (`LauncherApps`), persisted layout (`layout.json`), pure layout operations, Launcher3 import.
- `settings/`: `LauncherSettings` (SharedPreferences → `StateFlow`), shared by the launcher and the settings app.
- `ui/home/`: pager, grid, dock, icons, folders, edit mode, drag & drop. `ui/menu/`: long-press menu.
- `ui/glass/`, `ui/shape/`, `ui/icon/`, `ui/theme/`: wallpaper blur and `Modifier.glass()`, squircle, icon rendering/cache, design tokens.
- `ui/settings/`: iOS-style settings screens, components, and push navigation.
- `docs/`: `DESIGN.md` (measured spec), `ARCHITECTURE.md`, `ROADMAP.md`, `reference/` (screenshots of the reference app).

## Code map

- `docs/ARCHITECTURE.md` is the navigation map (package table, glass pipeline, drag flow). Consult it before exploring directories.
- Keep it current: when you add, remove, rename, or move a package, screen, or major class, or change the data flow (layout persistence, settings, glass, drag), update `ARCHITECTURE.md` in the same change. Keep it a map, not a description of internals.
- When a roadmap item is finished or descoped, update `docs/ROADMAP.md`. Mark an item done only after it works on the device.

## Modularity and file size

- The target limit is **450 lines per file**, including comments and blank lines. Apply it to Kotlin sources, resources written by hand, and tests; generated files and the Gradle wrapper are exempt.
- **451–550 lines** are allowed only as a justified exception when splitting would reduce cohesion. Briefly explain the reason in the final report. **More than 550 lines is prohibited** for new or modified handwritten files.
- When substantially changing an existing oversized file (currently `ui/home/HomeScreen.kt`), extract the affected responsibility. Avoid unrelated bulk refactoring solely to meet the limit.
- Split by responsibility: state/model, gestures/input, layout/measurement, drawing, dialogs/overlays, previews. Do not mechanically divide code into arbitrary chunks to satisfy a counter.
- Keep composables focused: a screen composable coordinates; parts live in `*Parts.kt` files or dedicated composables. Business logic stays out of composables, in the ViewModel or pure functions in `data/`.
- Prefer small, composable APIs, shared primitives (`glass`, `Squircle`, settings components, `Tokens`), and focused parameters. Do not introduce a universal framework or a second implementation of something that already exists.

## Features and correctness

- A feature is ready when it works on the device in the real launcher, not when it appears in a `@Preview`.
- Every interactive element must perform its stated action and change real, persisted state. Do not present empty handlers, fake values, decorative functional-looking buttons, or settings toggles that change nothing as completed features. Unimplemented settings are omitted or listed in `ROADMAP.md`, not shown as working.
- Layout changes go through the pure operations in `data/LayoutModel` and are persisted via `LayoutRepository`; one user action produces one change and one save. Do not mutate layout state from several places.
- Check applicable behavior: tap, long-press, drag, edit mode, back gesture/Home press, opening/closing overlays, app install/uninstall/update while the screen is open, configuration and wallpaper changes, process death and restore, font scale, RTL, display density, light and dark wallpapers, and Android 12 vs 13+ (permissions, blur).
- Use stable keys in lazy lists and pagers (package + user, item id). Collect flows lifecycle-aware. Keep heavy work (package queries, icon loading, blur, file I/O) off the main thread.
- Use Compose animation APIs and respect the system animator duration scale. Settled or invisible animations must not keep recomposing or invalidating draw.
- Avoid allocations and state reads that trigger recomposition per frame; read fast-changing values (scroll, drag offset) in layout/draw phases.
- Bitmaps and caches (icons, blurred wallpaper) must have bounded size and correct invalidation on app updates, icon-pack/custom-icon changes, and wallpaper changes.
- Sizes, colors, radii, and durations come from `ui/theme/Tokens` and `docs/DESIGN.md`. Do not scatter magic numbers; add a token when the spec defines a new value.
- Update the relevant docs. If behavior is missing, state the limitation explicitly instead of presenting the feature as complete.

## UI and previews

- Screens show the feature and its controls. Do not add marketing copy, explanatory paragraphs, technical status text, debug counters, or filler. Diagnostics go to `Log` with a tag, not to the UI.
- Do not add controls without real functionality. Sample data in `@Preview` is fine; it must not leak into the real launcher.
- Use the existing components and styling (glass, squircle, settings components). Do not hand-draw substitutes or patch component bugs locally.
- Match the reference: compare with the matching screenshot in `docs/reference/` and the measurements in `DESIGN.md`. Do not overwrite reference screenshots unless the task is to capture a new reference.
- For visual changes, install on the device and inspect the result. A successful build is not visual verification.
- Test device: POCO X5 over USB adb (Android 14, 1080×2400, density override 375). Capture screenshots with `adb exec-out screencap -p > build/screens/<name>.png` (outside `docs/`), and screen recordings with `adb shell screenrecord` for animations.

## Validation

Run Gradle via the wrapper from the repository root. Choose checks appropriate to the change; do not repeat the entire suite without a reason.

```sh
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:installDebug
./gradlew :app:assembleRelease
```

- Pure logic (`LayoutModel` operations, layout generation, import parsing, settings migration) gets JVM unit tests in `app/src/test/`. Add regression coverage for changed behavior; avoid tests that merely repeat the implementation. When adding the first tests, add only the minimal test dependency (JUnit).
- Run `assembleRelease` when touching R8/ProGuard rules, reflection, serialization, or anything loaded by name; the release build shrinks code and resources.
- After `installDebug`, press Home to bring the launcher up. Check logcat for crashes: `adb logcat -b crash` and `adb logcat --pid=$(adb shell pidof com.zfl.launcher)`.
- Do not clear app data, uninstall the launcher, or reset the default home app on the device without asking: it wipes the user's real layout.
- Distinguish baseline failures and pre-existing lint warnings from regressions introduced by the change. Do not hide failures with `@Suppress`, lint baselines, or skipped checks without an explained reason, and do not describe partial coverage as full verification.
- Documentation-only changes do not need a Gradle build.

## Performance

- Home screen paging, drag & drop, folder opening, and glass rendering are hot paths. Every substantial change to them needs a measurement, not an impression.
- Measure on the device with a **release** build (debug Compose is not representative). Use the existing tools; do not build a parallel measurement system:

```sh
adb shell dumpsys gfxinfo com.zfl.launcher reset
adb shell dumpsys gfxinfo com.zfl.launcher
```

- Perform the same scripted interaction for each run (for example, `adb shell input swipe` with identical coordinates and duration, repeated N times). Report frame count, janky frames %, and p50/p90/p95/p99 frame times.
- Compare before/after with identical build type, device, density, wallpaper, page contents, settings (blur, transition effect), and interaction. One noisy run does not demonstrate an improvement.
- Use Layout Inspector recomposition counts or Perfetto/system tracing to locate a cause; label which tool produced which number. Memory figures from `dumpsys meminfo` are estimates of the process, not of one cache.
- Verify that the behavior is still correct after optimizing. A fast but broken screen is not a successful result.

## Completion criteria

The feature works in the real launcher on the device, persists its state, and its docs agree with the behavior. Affected logic has tests; visual changes have been inspected on the device, or the lack of visual verification is explicitly reported. In the final response, distinguish builds, lint, unit tests, on-device checks, visual inspection, and measured performance.
