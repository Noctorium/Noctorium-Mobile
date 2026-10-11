package app.noctorium.android

import android.content.Context

/**
 * The release build's answer to what the debug build's hooks provide, which is nothing.
 *
 * The debug build's DebugHooks (src/debug) lets adb point it at a Noctorium service on the computer and hand
 * it a session there; a release build cannot be pointed anywhere and has no receiver for anyone to send to.
 */
object DebugHooks {
    @Suppress("UNUSED_PARAMETER")
    fun serviceUrl(context: Context): String? = null
}
