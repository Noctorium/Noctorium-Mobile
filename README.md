# Noctorium for Android

The phone. Compose for the interface, Media3 for audio and for behaving like a music app — lock screen,
notification, headset buttons, a foreground service so the music survives the screen going off —
NewPipeExtractor for reading the services, and the system WebView for sign-in. Everything that is the same
on the desktop — the library, the queue, settings, scrobbling, lyrics, the account, Connect — comes from
[Noctorium-Base](https://github.com/Noctorium/Noctorium-Base), which this repository includes as `core`.

> Noctorium is an independent third-party client. It is not affiliated with Google, YouTube, SoundCloud,
> Spotify, Last.fm, ListenBrainz or Discord.

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

## Releases

Signed APKs are built and published by [Noctorium-Installer](https://github.com/Noctorium/Noctorium-Installer),
which holds the release key. The application checks there for updates and hands a downloaded APK to
Android's own installer.

## Testing on a phone

Playback state, independent of what the screen shows:

```bash
adb shell dumpsys media_session | grep -A12 package=app.noctorium
```

The launcher activity is `app.noctorium/.android.MainActivity`.
