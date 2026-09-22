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
