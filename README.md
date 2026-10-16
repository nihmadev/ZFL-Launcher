<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png" alt="ZFL Launcher logo" width="160" height="160">
</p>

<h1 align="center">ZFL Launcher</h1>

<p align="center">
  <strong>Zero-Fee Launcher</strong>
  A free, open-source Android launcher with an elegant, iOS-inspired home screen.<br>
  Frosted glass, soft corners, and room to make it yours. No ads. No tracking.
</p>

## A familiar frustration

You find an iOS-style launcher that looks beautiful in screenshots. You install it,
arrange your apps, and try to settle in. Then come the ads, the awkward workarounds,
the glitches, and the crashes. It works—until it doesn't. Sound familiar?

ZFL started with that same frustration. Built by someone tired of the same things
you are, it aims to make a beautiful home screen feel like a comfortable part of
your phone. A launcher is something you use every day; it should be dependable,
pleasant, and leave you alone to enjoy it.

**ZFL stands for Zero-Fee Launcher.** Free to use, open to explore, and built without
ads or trackers. It brings the visual character of iOS to your Android home screen:
translucent glass, softly rounded icons, and familiar ways to organize your apps.

The project is still in early development. There is more to polish, and reliability
is a goal we keep working toward.

## Make yourself at home

- **A softer look.** Frosted glass surfaces, rounded icons, and a translucent dock
  give your wallpaper room to shine.
- **Your own arrangement.** Move apps between pages, keep favorites in the dock,
  and bring related apps together in folders.
- **Apps within reach.** Find an app through home screen search or browse the
  App Library when you want to see everything.
- **Useful little details.** Add clocks, calendars, photos, weather, or widgets
  from your installed apps.
- **Personal touches.** Customize icons, labels, and page transitions to make the
  home screen feel like yours.

## On a real phone

Captured on a POCO X5

<table>
  <tr>
    <td align="center"><img src="etc/01-home-screen.png" alt="Home screen with clock and calendar widgets" width="240"><br>Home screen</td>
    <td align="center"><img src="etc/05-today-widgets.png" alt="Today page with Warsaw weather and widgets" width="240"><br>Today widgets</td>
    <td align="center"><img src="etc/06-app-library.png" alt="App Library with categorized apps" width="240"><br>App Library</td>
  </tr>
  <tr>
    <td align="center"><img src="etc/02-folder.png" alt="Open Tools folder" width="240"><br>Folders</td>
    <td align="center"><img src="etc/12-widget-gallery.png" alt="Gallery of available widgets" width="240"><br>Widget gallery</td>
    <td align="center"><img src="etc/20-glass-settings.png" alt="Glass and blur customization controls" width="240"><br>Make it yours</td>
  </tr>
</table>

Explore the [full screenshot gallery](etc/README.md) for search, menus, editing,
widget sizes, and settings.

### Page transitions

Five ways to move between your home screen pages, recorded on the same phone.

<table>
  <tr>
    <th>Default</th><th>Rotation</th><th>Flip</th><th>Zoom In</th><th>Alpha</th>
  </tr>
  <tr>
    <td><img src="etc/transitions/default.gif" alt="Default page transition" width="140"></td>
    <td><img src="etc/transitions/rotation.gif" alt="Rotation page transition" width="140"></td>
    <td><img src="etc/transitions/flip.gif" alt="Flip page transition" width="140"></td>
    <td><img src="etc/transitions/zoom-in.gif" alt="Zoom In page transition" width="140"></td>
    <td><img src="etc/transitions/alpha.gif" alt="Alpha page transition" width="140"></td>
  </tr>
</table>

[Watch the 60 fps videos](etc/README.md#page-transitions) for smoother playback.

## Privacy comes with it

No advertising SDKs, trackers, analytics, crash reporters, Firebase, or Google Play
Services. Your layout and settings are stored on your phone.

The optional weather feature uses Open-Meteo for city searches and forecasts,
sending the city query or forecast coordinates needed for those requests. It needs
no account or API key. Choosing **Current Location** requires approximate location
access; you can choose a city manually instead. Web searches open in an external app.

On Android 13+, wallpaper blur needs **All files access** to read the wallpaper
bitmap. ZFL uses that permission only for the wallpaper; it does not scan, read,
or write your personal files. Without it, glass surfaces use a translucent tint.
Calendar access is requested when you choose to show upcoming events in a widget.

## Try it

ZFL runs on **Android 12 or newer**. To build it, use JDK 17 and the Android SDK,
or open the project in Android Studio. With your Android device connected, run:

```bash
./gradlew :app:installDebug
```

Press Home and choose **ZFL Launcher** as your home app.

### Build modes

- `./gradlew :app:assembleDebug` keeps debugging and Compose tooling available,
  skips R8, resource shrinking, and PNG crunching. Gradle caches task outputs and
  configuration for repeated builds; avoid `clean` during normal development.
- `./gradlew :app:assembleRelease` enables R8 full mode with strict keep rules,
  code shrinking, obfuscation, optimization, integrated resource shrinking, and
  PNG crunching. Classes may be repackaged and access widened for optimization.
  Release also removes Kotlin Intrinsics null checks: invalid nulls passed from
  Java/platform code may fail later, or no longer fail at the original boundary.
  Debug retains those checks. See the [R8 global options](https://developer.android.com/topic/performance/app-optimization/global-options).

The R8 modes above are already defaults in AGP 9.4; the project pins them
explicitly. Dependency keep rules and release stack-trace mapping are retained.
The release APK currently uses the development debug signing key.

Gradle uses up to two workers and the Kotlin daemon has a 1 GiB heap limit to
bound memory use. On a larger machine, override these in your user-level
`~/.gradle/gradle.properties` or use `--max-workers=4`.

## Help shape ZFL

Bug reports, thoughtful suggestions, translations, and pull requests are welcome.
If something feels awkward or breaks in everyday use, that is worth fixing.

For code contributions, keep comments and identifiers in English and put interface
text in `res/values/strings.xml`, with translations in `values-xx/`. Preserve the
project's privacy principles: contributions adding ads, analytics, or trackers
will not be accepted.

## License

ZFL Launcher is free software under the
[GNU General Public License v3.0 or later](LICENSE) (`GPL-3.0-or-later`).
