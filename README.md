# Noctorium for Android

The phone. Compose for the interface, Media3 for audio and for behaving like a music app — lock screen,
notification, headset buttons, a foreground service so the music survives the screen going off —
NewPipeExtractor for reading the services, and the system WebView for sign-in. Everything that is the same
on the desktop — the library, the queue, settings, scrobbling, lyrics, the account, Connect — comes from
[Noctorium-Base](https://github.com/Noctorium/Noctorium-Base), which this repository includes as `core`.

> Noctorium is an independent third-party client. It is not affiliated with Google, YouTube, SoundCloud,
> Spotify, Bandcamp, VK, Apple, Deezer, TIDAL, Last.fm, ListenBrainz or Discord.

## Getting the code

```bash
git clone --recursive https://github.com/Noctorium/Noctorium-Mobile.git
```

The `base/` submodule is Noctorium-Base. If you also have Noctorium-Base checked out beside this
repository as `../Noctorium-Base`, that checkout is used instead and edits there are seen by the next
build here — see `settings.gradle.kts` for the lookup order.

## Building

JDK 21 and an Android SDK, named in `local.properties` (`sdk.dir=...`) or by `ANDROID_HOME`.

```bash
./gradlew assembleDebug        # build/outputs/apk/debug/
./gradlew installDebug         # onto a connected phone
```

A debug build is signed with the debug key. It installs beside nothing and updates nothing: an APK signed
with the release key will not replace it, so uninstall a debug build before installing a release.

## Screenshots without a phone

The screens can be drawn on the JVM and saved as PNGs, by Robolectric with Android's own graphics and
Roborazzi, in the app's real theme and from made-up music, so nothing goes over the network:

```bash
./gradlew testDebugUnitTest -Pscreenshots --tests "app.noctorium.android.screenshots.*"
./gradlew testDebugUnitTest -Pscreenshots -PscreenshotDir=/some/folder --tests "app.noctorium.android.screenshots.*"
```

They land in `build/outputs/screenshots` unless `-PscreenshotDir` says otherwise. They are opt-in: the first
run downloads Android itself for Robolectric, and without `-Pscreenshots` neither the tests in
`src/screenshots/` nor their libraries are part of the build, so `testDebugUnitTest` stays quick.

`UnchangedScreenshots` draws every player bar, seek bar and layout there was before the Toolbar, the Slider
and the Playlist layout, in a dark theme, a light one and the three Windows skins: drawn before a change and
after it, the two sets should be the same to the pixel. To draw only some of the pictures while working on
them, name the start of theirs in `NOCTORIUM_SHOTS` (comma-separated), e.g. `NOCTORIUM_SHOTS=new-toolbar`.

## Icons

Every icon goes through one mapping (`ui/Glyphs.kt`), which draws it in the icon set chosen under Settings ›
Look › Icons. Standard is the icons the app has always drawn — Compose's Material icons, Material's filled
set — and Rounded, Outlined, Sharp and Two-tone are Google's Material icons in those styles (Apache License
2.0). **Line** is drawn from [Lucide](https://lucide.dev)'s shapes (lucide-react 1.50.0) under the ISC licence,
with the icons Lucide took from [Feather](https://feathericons.com) under the MIT licence; both notices are
reproduced in `ui/LineGlyphs.kt`, where the shapes are kept.

The badges on the covers that say which service a song comes from carry each service's mark, from
[Simple Icons](https://simpleicons.org) (simple-icons 16.35.0, CC0 1.0), unless they are set back to letters on
the same page. The marks are trademarks of the services they stand for, drawn only to say which service a song is
from; music on the phone has Material Icons' folder (Apache License 2.0). The shapes are kept in Noctorium-Base,
`core/src/main/kotlin/app/noctorium/domain/ServiceMarks.kt`, which the desktop draws them from too.

## Releases

Signed APKs are built and published by [Noctorium-Installer](https://github.com/Noctorium/Noctorium-Installer),
which holds the release key. The application checks there for updates and hands a downloaded APK to
Android's own installer.

## Testing on a phone

Playback state, independent of what the screen shows:

```bash
adb shell dumpsys media_session | grep -A12 package=app.noctorium
```

The launcher activity is `app.noctorium/.android.MainActivity` (a debug build's package is
`app.noctorium.debug`).

The volume booster is Android's loudness enhancer on the player's audio session. Whether it is on, and on
which session, is in AudioFlinger's effect chains; the gain the effect holds is logged, read back from it:

```bash
adb shell dumpsys media.audio_flinger | grep -A6 "effects for session"
adb logcat -s NoctoriumPlayer | grep "Volume boost"
```
