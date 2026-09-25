package app.noctorium.android

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import app.noctorium.net.BrowserReply
import app.noctorium.net.BrowserRequester
import app.noctorium.net.Http
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

/**
 * Makes a request from inside the phone's own browser, on SoundCloud's origin.
 *
 * SoundCloud's bot protection will not accept a like from anything that is not a browser. Not from
 * OkHttp with Chrome's user agent and its client hints and the clearance cookie the sign-in had already
 * earned, and not from a command line either -- I tried every combination against the real endpoint and
 * every one came back 403 with a captcha page. Reading likes from the same session, with none of those
 * headers, is answered immediately. It is the client being judged, not the request.
 *
 * The phone has a browser that passes: the WebView the listener signed in through. It holds the
 * clearance, it has Chromium's own network stack underneath it, and from a page served by
 * soundcloud.com its `fetch` sends the cookies, the origin and the referer without being told to. So the
 * write is made there. Nothing about this is a disguise -- it is Chromium making the request Chromium
 * would make.
 *
 * The page it loads is the site itself, and that turned out to matter: a near-empty page on the same
 * origin, with the same cookies, was still refused. The protection wants its own script to have run.
 */
class WebViewRequester(private val context: Context) : BrowserRequester {

    private var webView: WebView? = null
    private val pending = HashMap<String, CompletableDeferred<BrowserReply>>()
    private val nextId = AtomicLong(1)

    /** One request at a time. Each holds the page it is waiting on, and there is only one page. */
    private val turn = Mutex()

    /** Completed when the origin page finishes loading, so a request waits for the page it runs on. */
    private var loaded: CompletableDeferred<Unit>? = null

    /** Whether the protection has answered this browser at all yet. Set by the first request it allows. */
    private var cleared = false

    override suspend fun send(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
    ): BrowserReply? = turn.withLock {
        withTimeoutOrNull(REQUEST_TIMEOUT_MILLIS) {
            withContext(Dispatchers.Main) { request(method, url, headers, body) }
        }
    }

    private suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
    ): BrowserReply? {
        // Settled on something harmless first, when this is a request that must not be made twice.
        if (!cleared && method.uppercase() !in REPEATABLE) settle(headers)

        val first = attempt(method, url, headers, body) ?: return null
        if (first.status != REFUSED) return first.also { cleared = true }
        // Asking a second time is how a refusal on the first request of a run is cured -- the page has
        // loaded but the protection's own script has not finished deciding about this browser, and a
        // reload settles it. Only for a request that repeating cannot harm.
        //
        // Creating a playlist is not one. The first attempt at one came back 403 and the retry came back
        // 201, and the account ended up with two playlists of the same name: the refusal was handed out
        // after SoundCloud had already made the first. Hence the settling above, and this guard.
        if (method.uppercase() !in REPEATABLE) return first
        Log.i(LOG, "refused; reloading $ORIGIN_PAGE and asking once more")
        reload()
        return attempt(method, url, headers, body)?.also { cleared = it.status != REFUSED } ?: first
    }

    /**
     * Earns the clearance on a request that costs nothing to repeat, so the one that follows need not be.
     *
     * The account endpoint, because it is a plain read on the same protected host, is answered by the same
     * session the caller is already using, and changes nothing whether it succeeds, fails or happens twice.
     */
    private suspend fun settle(headers: Map<String, String>) {
        val session = headers.filterKeys { it.equals("Authorization", ignoreCase = true) }
        repeat(2) { round ->
            if (round > 0) reload()
            val reply = attempt("GET", SETTLING_URL, session, null) ?: return
            if (reply.status != REFUSED) {
                cleared = true
                return
            }
        }
    }

    private suspend fun attempt(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
    ): BrowserReply? {
        val view = runCatching { browser() }.getOrElse {
            Log.w(LOG, "no WebView to make the request with", it)
            return null
        }
        val id = nextId.getAndIncrement().toString()
        val answer = CompletableDeferred<BrowserReply>()
        pending[id] = answer
        view.evaluateJavascript(script(id, method, url, headers, body), null)
        return try {
            // Logged because this is the one request in the application that nothing else can make, and a
            // status is the difference between "SoundCloud said no" and "the page never ran it".
            answer.await().also { Log.i(LOG, "$method ${url.substringBefore('?')} answered ${it.status}") }
        } finally {
            pending.remove(id)
        }
    }

    /**
     * The page the request is made from, loaded once and kept.
     *
     * The site itself, not something small on the same host, and that is the difference between a like
     * that works and one that does not. A page with nothing on it puts the script on the right origin --
     * `soundcloud.com`, with the right cookies -- and the write was still refused. The protection wants
     * its own script to have run, which only happens on a page that actually loads the site.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun browser(): WebView {
        webView?.let { return it }
        val view = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // A desktop agent, because a phone's is redirected to `m.soundcloud.com` -- a different
            // origin from the one the website makes this call from.
            settings.userAgentString = Http.DESKTOP_USER_AGENT
            addJavascriptInterface(Bridge(), BRIDGE)
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    loaded?.takeIf { !it.isCompleted }?.complete(Unit)
                }
            }
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        webView = view
        load(view)
        return view
    }

    private suspend fun reload() = webView?.let { load(it) }

    private suspend fun load(view: WebView) {
        val finished = CompletableDeferred<Unit>()
        loaded = finished
        view.loadUrl(ORIGIN_PAGE)
        // A page that never finishes still has its origin and its cookies, and a request from it may well
        // be answered; waiting past this point would cost the listener their like for nothing.
        withTimeoutOrNull(LOAD_TIMEOUT_MILLIS) { finished.await() }
    }

    /**
     * Runs the request and hands the answer back through [Bridge].
     *
     * `evaluateJavascript` reports the value of an expression, and a fetch has none to report yet, so the
     * reply comes back the other way rather than as this call's result.
     */
    private fun script(
        id: String,
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String?,
    ): String {
        val headerJson = JSONObject(headers.toMap()).toString()
        // Absent rather than null: `fetch` refuses a body on a GET at all, and gives a DELETE one it does
        // not need. Quoted, because what goes in is JSON and what this builds is a JavaScript literal.
        val bodyLine = body?.let { "body: ${quote(it)}," }.orEmpty()
        return """
            (function () {
              var deliver = function (status, body) {
                try { $BRIDGE.deliver(${quote(id)}, status, body); } catch (ignored) {}
              };
              try {
                fetch(${quote(url)}, {
                  method: ${quote(method)},
                  headers: $headerJson,
                  $bodyLine
                  credentials: 'include'
                }).then(function (reply) {
                  return reply.text().then(function (body) { deliver(reply.status, body); });
                }).catch(function (error) { deliver(0, String(error)); });
              } catch (error) { deliver(0, String(error)); }
            })();
        """.trimIndent()
    }

    private fun quote(value: String) = JSONObject.quote(value)

    private inner class Bridge {
        @JavascriptInterface
        fun deliver(id: String, status: Int, body: String) {
            // Off the JavaScript thread and onto the map's own, which is the main thread everywhere else.
            webView?.post { pending.remove(id)?.complete(BrowserReply(status, body)) }
        }
    }

    /** Lets go of the browser. Safe to call more than once; the next request builds another. */
    fun close() {
        webView?.let { view -> view.post { view.destroy() } }
        webView = null
    }

    private companion object {
        const val LOG = "NoctoriumBrowser"
        const val BRIDGE = "NoctoriumBridge"

        /** The site itself: the origin the website makes this call from, with its own scripts running. */
        const val ORIGIN_PAGE = "https://soundcloud.com/"

        /** What the bot protection answers a browser it has not cleared. */
        const val REFUSED = 403

        /**
         * The methods a refusal may be retried on, because sending one twice changes nothing.
         *
         * Liking is a PUT and unliking a DELETE, both of which land on the same state either way. Creating
         * a playlist is a POST and lands on two.
         */
        val REPEATABLE = setOf("GET", "HEAD", "PUT", "DELETE")

        /** A read on the protected host, used to earn the clearance where a retry would not be safe. */
        const val SETTLING_URL = "https://api-v2.soundcloud.com/me"

        const val LOAD_TIMEOUT_MILLIS = 15_000L
        const val REQUEST_TIMEOUT_MILLIS = 30_000L
    }
}
