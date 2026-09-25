package app.noctorium.android

import android.annotation.SuppressLint
import android.os.Message
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import app.noctorium.auth.HarvestedCookie
import app.noctorium.auth.SOUNDCLOUD_SESSION_URLS
import app.noctorium.auth.isSoundCloudSignedIn
import app.noctorium.auth.isYouTubeSignedIn
import app.noctorium.auth.YOUTUBE_SESSION_URLS
import app.noctorium.auth.writeCookieFile
import app.noctorium.domain.ProviderType
import app.noctorium.settings.AppDirectories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.time.Instant
import kotlin.coroutines.resume

/**
 * Signing in on a phone, in the service's own page.
 *
 * The desktop embeds Chromium for this; Android already has one. The listener types their password into
 * Google's or SoundCloud's real page inside a WebView, and the session that results lives in the app's own
 * cookie store — not in Chrome, not anywhere another app can read.
 *
 * What comes out is the same Netscape cookie file the desktop writes, because everything above this point
 * — the session checks, the signed requests, the library — was written against that file and does not care
 * which browser filled it.
 */
/** Where the sign-in says what it is doing, so a page that quietly refuses can be told from one that broke. */
private const val SIGN_IN_LOG = "NoctoriumSignIn"

object WebViewSignIn {

    /** Where each service's sign-in starts. */
    fun startUrlFor(provider: ProviderType): String = when (provider) {
        ProviderType.SOUNDCLOUD -> "https://soundcloud.com/signin"
        // Google's sign-in directly, rather than the music site's own button.
        //
        // Starting at music.youtube.com was the obvious thing and it does not work: the page loads,
        // its Sign in button highlights when pressed, and nothing whatever happens -- no navigation,
        // no popup, no error, nothing in the log. The button is script that decides for itself whether
        // the browser it is in deserves a login, and in a WebView it decides no, silently.
        //
        // ServiceLogin takes that decision away from the page. `continue` is where Google sends the
        // browser once the password is accepted, so the session lands on the music site exactly as it
        // would have, and `service=youtube` is what gets the YouTube cookies set rather than a bare
        // Google session.
        else -> "https://accounts.google.com/ServiceLogin" +
            "?service=youtube&continue=https%3A%2F%2Fmusic.youtube.com%2F"
    }

    /** Which hosts hold the cookies worth taking for this service. */
    fun sessionUrlsFor(provider: ProviderType): List<String> = when (provider) {
        ProviderType.SOUNDCLOUD -> SOUNDCLOUD_SESSION_URLS
        else -> YOUTUBE_SESSION_URLS
    }

    /**
     * Where the harvested session is written.
     *
     * The same folder and the same names the desktop uses, so the two builds describe a session the same
     * way even though nothing is shared between the devices.
     */
    fun cookieFileFor(provider: ProviderType): Path? {
        val name = if (provider == ProviderType.SOUNDCLOUD) "soundcloud.cookies" else "youtube.cookies"
        return AppDirectories.resolve("sessions", name)
    }

    /**
     * Everything the WebView is holding for the given hosts.
     *
     * Android hands cookies back as one `name=value; name=value` header per host and will not say more —
     * no expiry, no secure flag, no path. So each is written as a session cookie with the host it came
     * from, which is the truthful reading: a cookie the store still returns is one the store still holds.
     */
    fun harvest(urls: List<String>): List<HarvestedCookie> {
        val manager = CookieManager.getInstance()
        val seen = LinkedHashMap<String, HarvestedCookie>()
        urls.forEach { url ->
            val host = url.removePrefix("https://").removePrefix("http://").trimEnd('/')
            val header = manager.getCookie(url).orEmpty()
            header.split(';').forEach { pair ->
                val parts = pair.trim().split('=', limit = 2)
                if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) return@forEach
                val cookie = HarvestedCookie(
                    // A leading dot means "and every subdomain", which is how these are actually scoped and
                    // what lets one jar answer for music.youtube.com and www.youtube.com alike.
                    domain = if (host.startsWith("www.")) ".${host.removePrefix("www.")}" else ".$host",
                    path = "/",
                    name = parts[0],
                    value = parts[1],
                    secure = true,
                    // Android does not report expiry. Zero means a session cookie, which is the honest
                    // reading of "the store still has it and will not say for how long".
                    expiresEpochSeconds = 0,
                )
                // Last host wins for a repeated name: the more specific host is visited later in the list.
                seen["${cookie.domain}|${cookie.name}"] = cookie
            }
        }
        return seen.values.toList()
    }

    /**
     * Writes the session, but only if what was harvested is actually one.
     *
     * The check is the whole point. A signed-out YouTube page still hands over a pile of cookies —
     * VISITOR_INFO1_LIVE, PREF, a consent record — so "some cookies came back" proves nothing, and
     * accepting that is precisely the loop this project has already been round once: sign-in reports
     * success, the service answers 401, and pressing sign-in again harvests the same useless cookies and
     * reports success again. Nothing fails anywhere in that circle.
     *
     * What counts is a cookie that can sign a request: SAPISID for Google, oauth_token for SoundCloud.
     * Null means no session was left behind, and the caller says so instead of writing a file.
     */
    suspend fun saveSession(provider: ProviderType): Path? = withContext(Dispatchers.IO) {
        val cookies = harvest(sessionUrlsFor(provider))
        val signedIn = if (provider == ProviderType.SOUNDCLOUD) {
            isSoundCloudSignedIn(cookies)
        } else {
            isYouTubeSignedIn(cookies)
        }
        if (!signedIn) return@withContext null
        val destination = cookieFileFor(provider) ?: return@withContext null
        runCatching { writeCookieFile(cookies, destination) }.getOrNull()
    }

    /**
     * Forgets everything the WebView is holding.
     *
     * Run before a sign-in, always. Without it the page opens already signed in as whoever was there
     * before, the listener is never asked for anything, and the same dead cookies are harvested again and
     * reported as success — the loop this project has already had once, from the other browser.
     */
    suspend fun clearCookies(): Unit = suspendCancellableCoroutine { continuation ->
        val manager = CookieManager.getInstance()
        manager.removeAllCookies { manager.flush(); if (continuation.isActive) continuation.resume(Unit) }
    }

    /** The SoundCloud token, which its API needs in a header rather than as a cookie. */
    fun soundCloudToken(): String? = CookieManager.getInstance()
        .getCookie("https://soundcloud.com/").orEmpty()
        .split(';')
        .map(String::trim)
        .firstOrNull { it.startsWith("oauth_token=") }
        ?.removePrefix("oauth_token=")
        ?.takeIf(String::isNotBlank)

    /**
     * The user agent to sign in with: the phone's own, with the tell removed.
     *
     * Android's WebView announces itself by putting `wv` in its user agent, and Google refuses to serve
     * its sign-in to anything that does — the policy against embedded browsers is theirs and it is
     * deliberate. The refusal is not an error page; the button simply does nothing, which is a horrible
     * thing to debug and is exactly what this app did.
     *
     * Everything else is left alone. The phone's real Chrome version, its Android version and its model
     * stay, so the mobile layout is still served to a 6-inch screen: the only difference between this
     * string and the one Chrome sends on the same phone is the token that says "I am embedded".
     */
    fun signInUserAgent(deviceUserAgent: String): String =
        deviceUserAgent.replace("; wv)", ")").replace(" wv)", ")")

    /**
     * Prepares a WebView to host a real sign-in.
     *
     * JavaScript and DOM storage are on because neither Google's nor SoundCloud's login works without
     * them. Windows are supported because sign-in buttons open them: SoundCloud's "continue with"
     * choices are popups, and a WebView that has not been told to expect one silently drops it.
     */
    /** What every window in a sign-in needs, the popup as much as the page that opened it. */
    @SuppressLint("SetJavaScriptEnabled")
    private fun applySettings(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            userAgentString = signInUserAgent(userAgentString)
            // Both are needed for a popup to arrive at onCreateWindow at all.
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(
        webView: WebView,
        onPageFinished: (String?) -> Unit,
        /**
         * Called with the window a page has asked to open, and with null when it closes itself.
         *
         * The caller has to put it on the screen. That is not a detail of presentation: see
         * [WebChromeClient.onCreateWindow] below for why the window has to be real.
         */
        onPopup: (WebView?) -> Unit = {},
    ) {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        applySettings(webView)
        Log.i(SIGN_IN_LOG, "signing in as: ${webView.settings.userAgentString}")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                Log.i(SIGN_IN_LOG, "page: $url")
                CookieManager.getInstance().flush()
                onPageFinished(url)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                // Only the page's own failure is worth a line; a missing tracking pixel is not.
                if (request?.isForMainFrame == true) {
                    Log.w(SIGN_IN_LOG, "failed: ${request.url} -- ${error?.description}")
                }
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            /**
             * A real second window, because an OAuth popup is not a link.
             *
             * The obvious shortcut -- take the address the page wanted to open and load it in the window
             * already on screen -- is what this did first, and it fails in a way that looks like the
             * listener's fault. SoundCloud's "continue with Google" opens a popup which, when Google is
             * done, hands the result back to the page that opened it through `window.opener`. Loading it
             * in the main window destroys that page, so there is nothing left to hand anything back to:
             * Google signs in perfectly, SoundCloud never hears about it, and Done reports no session
             * with nothing in any log. The cookie store afterwards had a google_auth_nonce and no token,
             * which is exactly the shape of a conversation that was interrupted halfway.
             *
             * So the popup gets a WebView of its own, with the same settings and the same cookie store,
             * and the caller puts it on the screen. It closes itself when the exchange is done, which is
             * what onCloseWindow reports.
             */
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message,
            ): Boolean {
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                val popup = WebView(view.context)
                applySettings(popup)
                CookieManager.getInstance().setAcceptThirdPartyCookies(popup, true)
                popup.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(window: WebView?, url: String?) {
                        Log.i(SIGN_IN_LOG, "popup page: $url")
                        CookieManager.getInstance().flush()
                    }
                }
                popup.webChromeClient = object : WebChromeClient() {
                    override fun onCloseWindow(window: WebView) {
                        Log.i(SIGN_IN_LOG, "popup closed")
                        CookieManager.getInstance().flush()
                        onPopup(null)
                    }

                    override fun onConsoleMessage(message: ConsoleMessage?): Boolean = true
                }
                transport.webView = popup
                resultMsg.sendToTarget()
                onPopup(popup)
                return true
            }

            override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                message?.takeIf { it.messageLevel() == ConsoleMessage.MessageLevel.ERROR }
                    ?.let { Log.w(SIGN_IN_LOG, "page error: ${it.message()}") }
                return true
            }
        }
    }
}
