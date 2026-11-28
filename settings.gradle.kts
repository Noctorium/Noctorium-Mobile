pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // NewPipeExtractor, which does on Android what yt-dlp does on the desktop. Published to JitPack
        // rather than Maven Central, and this is the only thing taken from there.
        maven("https://jitpack.io") {
            content { includeGroupByRegex("com\\.github\\.TeamNewPipe.*") }
        }
    }
}

rootProject.name = "noctorium"

/**
 * The shared half of the application, from Noctorium-Base.
 *
 * `core` is everything that does not care what it is running on -- the domain model, the library, the
 * queue, settings, scrobbling, lyrics, the account, Connect -- and it is shared byte for byte with the
 * desktop. It lives in its own repository and is included here by path rather than published as an
 * artifact, so an edit to it is a rebuild away instead of a release away.
 *
 * Where it is looked for, in order:
 *
 * 1. NOCTORIUM_BASE, when set: an explicit checkout, for a machine laid out some other way.
 * 2. ../Noctorium-Base: a checkout beside this one. This is the arrangement for working on both at once,
 *    since what is built is whatever is on disk there, with no pointer to move.
 * 3. base/: the submodule this repository carries. This is what a clean clone and the release pipeline
 *    use; `git clone --recursive`, or `git submodule update --init`, brings it in.
 */
val baseCheckout: File = listOfNotNull(
    System.getenv("NOCTORIUM_BASE")?.takeIf { it.isNotBlank() }?.let(::file),
    file("../Noctorium-Base"),
    file("base"),
).firstOrNull { File(it, "core/build.gradle.kts").isFile }
    ?: error(
        "Noctorium-Base was not found. Either check it out beside this repository as ../Noctorium-Base, " +
            "run `git submodule update --init` to fetch the base/ submodule, or set NOCTORIUM_BASE to a checkout.",
    )

include(":core")
project(":core").projectDir = File(baseCheckout, "core")

/**
 * Noctorium in the car, from Noctorium-Android-Auto: the menu Android Auto shows, built on this app's session. Found
 * as Noctorium-Base is: NOCTORIUM_AUTO, a checkout beside this one, or the auto/ submodule.
 */
val autoCheckout: File = listOfNotNull(
    System.getenv("NOCTORIUM_AUTO")?.takeIf { it.isNotBlank() }?.let(::file),
    file("../Noctorium-Android-Auto"),
    file("auto"),
).firstOrNull { File(it, "auto/build.gradle.kts").isFile }
    ?: error(
        "Noctorium-Android-Auto was not found. Either check it out beside this repository as ../Noctorium-Android-Auto, " +
            "run `git submodule update --init` to fetch the auto/ submodule, or set NOCTORIUM_AUTO to a checkout.",
    )

include(":auto")
project(":auto").projectDir = File(autoCheckout, "auto")

/**
 * Noctorium on a television, from Noctorium-Android-TV: the screens Android TV opens, over this app's state. Found as
 * the others are: NOCTORIUM_TV, a checkout beside this one, or the tv/ submodule.
 */
val tvCheckout: File = listOfNotNull(
    System.getenv("NOCTORIUM_TV")?.takeIf { it.isNotBlank() }?.let(::file),
    file("../Noctorium-Android-TV"),
    file("tv"),
).firstOrNull { File(it, "tv/build.gradle.kts").isFile }
    ?: error(
        "Noctorium-Android-TV was not found. Either check it out beside this repository as ../Noctorium-Android-TV, " +
            "run `git submodule update --init` to fetch the tv/ submodule, or set NOCTORIUM_TV to a checkout.",
    )

include(":tv")
project(":tv").projectDir = File(tvCheckout, "tv")

val launcherCheckout: File = listOfNotNull(
    System.getenv("NOCTORIUM_LAUNCHER")?.takeIf { it.isNotBlank() }?.let(::file),
    file("../Noctorium-Mp3-Like-Player-Android-Launcher-"),
    file("launcher"),
).firstOrNull { File(it, "launcher/build.gradle.kts").isFile }
    ?: error(
        "Noctorium-Mp3-Like-Player-Android-Launcher- was not found. Either check it out beside this repository, " +
            "run `git submodule update --init` to fetch the launcher/ submodule, or set NOCTORIUM_LAUNCHER to a checkout.",
    )

include(":launcher")
project(":launcher").projectDir = File(launcherCheckout, "launcher")
