package app.noctorium.android.ui

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.noctorium.auth.SOUNDCLOUD_OWN_LIKES
import app.noctorium.auth.permalinkFromBrowserUrl
import app.noctorium.android.WebViewSignIn
import app.noctorium.core.AppState
import app.noctorium.domain.ProviderType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The service's own sign-in page, inside Noctorium.
 *
 * The password is typed into Google's, SoundCloud's, VK's or Apple's real page, never into anything of ours, and the
 * session it produces is kept in this app's cookie store. That is the same bargain the desktop makes with
 * embedded Chromium; Android simply already has the browser.
 *
 * Finishing is a decision the listener makes, not one guessed from the URL. YouTube and SoundCloud bounce
 * through several addresses on the way in and land somewhere different depending on the account, so the
 * honest arrangement is a Done button — pressed when they can see they are signed in — which then checks
 * whether a usable session really was left behind rather than assuming it.
 *
 * VK is the exception, because its session cannot be mistaken: the two cookies it is made of are there
 * once VK has signed somebody in, and not before. So VK's is handed over the moment both appear, VK is asked
 * whether it is good, and the page closes itself once it is. Done is still there to ask again.
 *
 * Apple Music is the same kind of exception: its web player keeps the listener's token in one cookie,
 * media-user-token, once Apple has signed them in, and not before. Apple's sign-in finishes in a window of its own
 * which hands the answer back to the web player, so the cookie is looked for every few seconds as well as at each
 * page, rather than only when a page in this window finishes.
 */
@Composable
internal fun SignInScreen(provider: ProviderType, state: AppState, close: () -> Unit) {
    // Everything below is the page, the cookies and the session of one of the services it knows by name;
    // anything else would fall through to YouTube's. It is turned away before anything loads. Nothing leads
    // here with one; this keeps it that way.
    if (!hasSignInPage(provider)) {
        LaunchedEffect(provider) { close() }
        return
    }
    val scope = rememberCoroutineScope()
    val settings by state.settings.collectAsState()
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var popup by remember { mutableStateOf<WebView?>(null) }
    // The address the page last settled on, which is how SoundCloud is asked who just signed in.
    var lastUrl by remember { mutableStateOf<String?>(null) }

    val isVk = provider == ProviderType.VK
    val isAppleMusic = provider == ProviderType.APPLE_MUSIC
    // VK and Apple Music are handed over as soon as their cookies are there, and close the page themselves.
    val byItself = isVk || isAppleMusic
    val vk = settings.vk
    val appleMusic = settings.appleMusic
    // Whether the service has been asked about what was handed over and is asking still, has said yes, and what
    // it said otherwise.
    val checking = (isVk && vk.checking) || (isAppleMusic && appleMusic.checking)
    val accepted = (isVk && vk.connected) || (isAppleMusic && appleMusic.connected)
    val refusal = if (isVk) vk.message else appleMusic.message
    // The session last handed to Noctorium, so the same one is not handed over again with every page the sign-in
    // passes through. Held here and nowhere else: never shown, never logged.
    var handedOver by remember { mutableStateOf<String?>(null) }

    /** Hands the session over once the service has made one -- VK's two cookies, Apple's one -- and false before. */
    fun handOver(again: Boolean): Boolean {
        val session = (if (isVk) WebViewSignIn.vkSession() else WebViewSignIn.appleMusicToken()) ?: return false
        if (!again && session == handedOver) return true
        handedOver = session
        if (isVk) state.completeVkSignIn(session) else state.completeAppleMusicSignIn(session)
        return true
    }

    if (byItself) {
        // Signed in once the service has said the session is good, and not before.
        LaunchedEffect(accepted) { if (accepted) close() }
        // The WebView's copy of the session goes when this does, however it ends: Noctorium keeps its own,
        // and two holders of one session is how a copy dies. Only this service's cookies, never the whole store.
        DisposableEffect(Unit) { onDispose { WebViewSignIn.clearCookiesOf(provider) } }
    }
    if (isAppleMusic) {
        // Apple's window hands its answer to the web player behind it, whose page does not finish again: so the
        // cookie is looked for every few seconds, not only as pages here finish.
        LaunchedEffect(Unit) {
            while (true) {
                delay(APPLE_MUSIC_LOOK_EVERY_MS)
                handOver(again = false)
            }
        }
    }
    // The service's refusal, once it has been asked and has answered; Noctorium's own words otherwise.
    val shownProblem = problem ?: refusal.takeIf { byItself && handedOver != null && !checking && !accepted }

    // Back closes the popup first, the way a browser does: it is a window in front of the page, and the
    // page is still where the listener was.
    BackHandler {
        when {
            popup != null -> { popup?.destroy(); popup = null }
            webView?.canGoBack() == true -> webView?.goBack()
            else -> close()
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(
                WindowInsets.statusBars.union(WindowInsets.navigationBars),
            ),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(close) { Icon(Glyph.CLOSE.image, "Cancel") }
                Column(Modifier.weight(1f)) {
                    Text(
                        // The site, not the service: what is signed in to is VK itself.
                        if (isVk) "Sign in to VK" else "Sign in to ${provider.displayName}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                    Text(
                        when {
                            isVk && checking -> "Checking with VK…"
                            checking -> "Checking with Apple…"
                            byItself -> "Noctorium carries on by itself once you are in."
                            else -> "Press Done when you are signed in."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
                val busy = saving || checking
                SkinnedButton(
                    enabled = !busy,
                    onClick = {
                        problem = null
                        if (byItself) {
                            if (!handOver(again = true)) {
                                problem = if (isVk) {
                                    "VK has not given this page a session yet. Finish signing in, and Noctorium " +
                                        "carries on by itself."
                                } else {
                                    "Apple has not signed this page in yet. Finish signing in, and Noctorium " +
                                        "carries on by itself."
                                }
                            }
                            return@SkinnedButton
                        }
                        saving = true
                        scope.launch {
                            val saved = WebViewSignIn.saveSession(provider)
                            if (saved == null) {
                                problem = "You are not signed in yet — the page gave back no session. " +
                                    "Finish signing in, then press Done."
                                saving = false
                                return@launch
                            }
                            if (provider == ProviderType.SOUNDCLOUD) {
                                // No profile name from here: the phone is served m.soundcloud.com, which
                                // answers /you/likes itself instead of moving to /<profile>/likes the way
                                // the desktop site does, so there is nothing in the address to read. The
                                // session's own token is asked instead, which works on both.
                                state.completeSoundCloudSignIn(
                                    saved.toString(),
                                    WebViewSignIn.soundCloudToken(),
                                )
                            } else {
                                // With what the WebView called itself, so every later request says the same.
                                state.completeYouTubeSignIn(saved.toString(), webView?.settings?.userAgentString)
                                // The WebView's copy goes once Noctorium has its own, as SimpMusic does it:
                                // two holders of one session is how a copy dies, whichever renews the
                                // cookies turning the other's into yesterday's. Google's and YouTube's only.
                                WebViewSignIn.clearCookiesOf(provider)
                            }
                            saving = false
                            close()
                        }
                    },
                ) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Done")
                    }
                }
            }

            shownProblem?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))

            Box(Modifier.weight(1f)) {
                AndroidView(
                    factory = { context ->
                        WebView(context).also { view ->
                            WebViewSignIn.configure(
                                webView = view,
                                onPageFinished = { url ->
                                    loading = false
                                    lastUrl = url
                                    // VK has made its session by the time a page after the sign-in finishes, and
                                    // Apple's web player its token by the time it has gone back to itself.
                                    if (byItself) handOver(again = false)
                                },
                                onPopup = { popup = it },
                            )
                            // Always from a clean store. Reusing whatever was there opens a page already
                            // signed in as the previous account, asks the listener for nothing, and harvests
                            // the same stale cookies again — the loop this project has already had once, from
                            // the desktop browser. Cleared here, before the page is asked for, rather than in
                            // an effect that ran once it was already loading. This service's own cookies only:
                            // SoundCloud's writes are made from this store, and signing in to YouTube, VK or
                            // Apple Music should not sign anybody out of it.
                            WebViewSignIn.clearCookiesOf(provider)
                            view.loadUrl(WebViewSignIn.startUrlFor(provider))
                            webView = view
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                /*
                 * The second window, over the first rather than instead of it.
                 *
                 * A "continue with Google" popup hands its result back to the page that opened it, so
                 * that page has to still be there underneath, loaded and waiting. Covering it is the
                 * whole trick: the listener sees one screen at a time, and the conversation between the
                 * two windows carries on behind it.
                 */
                popup?.let { window ->
                    AndroidView(
                        factory = { window },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** How often Apple Music's sign-in looks for the token Apple's web player keeps once it is signed in. */
private const val APPLE_MUSIC_LOOK_EVERY_MS = 3_000L
