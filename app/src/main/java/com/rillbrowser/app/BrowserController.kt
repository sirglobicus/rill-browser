package com.rillbrowser.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.io.ByteArrayInputStream

/** Owns the single WebView (the "tab"), its clients, and the content blocker hookup. */
class BrowserController(
    private val context: Context,
    private val container: FrameLayout,
    private val store: Store,
    private val blocker: AdBlocker,
    private val fullScreenChanged: (View?) -> Unit,
    private val contextRequested: (link: String?, src: String?) -> Unit,
    private val renderGone: () -> Unit,
) {
    var webView: WebView? = null
        private set
    var url = ""
        private set
    var title = ""
        private set
    var canGoBack = false
        private set
    var canGoForward = false
        private set
    var loading = false
        private set
    var fullscreen = false
        private set

    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var lastUserClick = 0L
    private val pageHost get() = Uri.parse(url).host

    // ---- lifecycle ----

    fun open(url: String) {
        ensureWebView().loadUrl(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView(): WebView {
        webView?.let { return it }
        val w = WebView(context)
        w.setBackgroundColor(Color.BLACK)
        w.isFocusable = true
        w.isFocusableInTouchMode = true
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false // autoplay allowed
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(true) // so we can refuse pop-ups instead of letting them replace the page
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            setSupportZoom(false)
            builtInZoomControls = false
            applyMode(this, store.desktopMode)
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(w, true)
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(w, HELPER_JS, setOf("*"))
        }
        w.setOnLongClickListener { longPress(w) }
        w.webViewClient = client
        w.webChromeClient = chrome
        container.addView(w, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        webView = w
        return w
    }

    /** Close the tab and forget all state, so the next launch starts clean. */
    fun close(clearCookies: Boolean = true) {
        exitFullScreen()
        webView?.let {
            it.stopLoading()
            container.removeView(it)
            it.destroy()
        }
        webView = null
        url = ""; title = ""; canGoBack = false; canGoForward = false; loading = false; fullscreen = false
        if (clearCookies && store.clearCookiesOnExit) CookieManager.getInstance().removeAllCookies(null)
    }

    // ---- actions ----

    fun goBack() = webView?.goBack()
    fun goForward() = webView?.goForward()
    fun reloadOrStop() { if (loading) webView?.stopLoading() else webView?.reload() }

    fun exitFullScreen() {
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
    }

    fun setDesktopMode(desktop: Boolean) {
        store.desktopMode = desktop
        webView?.let { applyMode(it.settings, desktop); it.reload() }
    }

    private fun applyMode(s: WebSettings, desktop: Boolean) {
        if (defaultUa == null) defaultUa = s.userAgentString
        val base = defaultUa ?: return
        s.userAgentString = if (desktop) {
            base.replace(Regex("\\(Linux; Android[^)]*\\)"), "(X11; Linux x86_64)").replace(" Mobile", "")
        } else base
        s.useWideViewPort = desktop
        s.loadWithOverviewMode = desktop
    }

    /** Called when the user presses the cursor, so we can tell user-opened tabs from pop-ups. */
    fun noteUserClick() { lastUserClick = SystemClock.uptimeMillis() }

    private fun recentClick() = SystemClock.uptimeMillis() - lastUserClick < 1500

    /** Player keys are sent into the page as real key presses; [HELPER_JS] turns them into actions. */
    fun sendKey(keyCode: Int) {
        val w = webView ?: return
        val now = SystemClock.uptimeMillis()
        w.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        w.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }

    private fun longPress(w: WebView): Boolean {
        val hit = w.hitTestResult
        val isImage = hit.type == WebView.HitTestResult.IMAGE_TYPE || hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
        val src = if (isImage) hit.extra else null
        return when (hit.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE,
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE,
            WebView.HitTestResult.IMAGE_TYPE -> {
                val h = Handler(Looper.getMainLooper()) { m: Message ->
                    contextRequested(m.data.getString("url"), src)
                    true
                }
                w.requestFocusNodeHref(h.obtainMessage())
                true
            }
            else -> false
        }
    }

    // ---- clients ----

    private val client = object : WebViewClient() {
        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (!blocker.shouldBlock(request.url, pageHost)) return null
            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val scheme = request.url.scheme ?: return true
            if (scheme != "http" && scheme != "https" && scheme != "about" && scheme != "blob" && scheme != "data") return true
            // Redirect trick: a page navigating itself to another site with no click behind it.
            if (store.blockRedirects && request.isForMainFrame && !request.hasGesture() && !recentClick() && !loading) {
                val to = request.url.host
                val from = pageHost
                if (to != null && from != null && AdBlocker.base(to) != AdBlocker.base(from)) return true
            }
            return false
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            loading = true
            this@BrowserController.url = url
            hideAds(view)
            sync(view)
        }

        override fun onPageFinished(view: WebView, url: String) {
            loading = false
            this@BrowserController.url = url
            if (url.startsWith("http")) store.addHistory(title, url)
            hideAds(view)
            sync(view)
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            this@BrowserController.url = url
            sync(view)
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Handler(Looper.getMainLooper()).post { renderGone() }
            return true
        }
    }

    private fun sync(view: WebView) {
        canGoBack = view.canGoBack()
        canGoForward = view.canGoForward()
    }

    private fun hideAds(view: WebView) {
        val css = blocker.cssFor(pageHost)
        if (css.isEmpty()) return
        view.evaluateJavascript(
            "(function(){var s=document.getElementById('__rill_css');if(!s){s=document.createElement('style');s.id='__rill_css';" +
                "(document.head||document.documentElement).appendChild(s);}s.textContent=${JSONObject.quote(css)};})()",
            null,
        )
    }

    private val chrome = object : WebChromeClient() {
        override fun onReceivedTitle(view: WebView, title: String?) {
            this@BrowserController.title = title ?: ""
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            customViewCallback = callback
            fullscreen = true
            fullScreenChanged(view)
        }

        override fun onHideCustomView() {
            customViewCallback = null
            fullscreen = false
            fullScreenChanged(null)
        }

        // Pop-ups are only honoured (in this same tab) right after a real click.
        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            if (!recentClick()) return false
            val temp = WebView(context)
            temp.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                    view.loadUrl(request.url.toString())
                    Handler(Looper.getMainLooper()).post { temp.destroy() }
                    return true
                }
            }
            (resultMsg.obj as WebView.WebViewTransport).webView = temp
            resultMsg.sendToTarget()
            return true
        }

        // DRM (Widevine) is allowed; camera, microphone etc. are denied.
        override fun onPermissionRequest(request: PermissionRequest) {
            val drm = arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)
            if (request.resources.all { it in drm }) request.grant(request.resources) else request.deny()
        }
    }

    companion object {
        private var defaultUa: String? = null

        // Private key codes the app sends; see HELPER_JS.
        const val KEY_FULLSCREEN = KeyEvent.KEYCODE_F9
        const val KEY_PLAY_PAUSE = KeyEvent.KEYCODE_F10
        const val KEY_FORWARD = KeyEvent.KEYCODE_F11
        const val KEY_BACK = KeyEvent.KEYCODE_F12

        /** Runs at document start in every frame (iframe players included) and acts on the biggest <video>. */
        private val HELPER_JS = """
            (function(){
              if (window.__rill) return; window.__rill = 1;
              function area(e){ var r=e.getBoundingClientRect(); return r.width*r.height; }
              function biggest(sel){ return [].slice.call(document.querySelectorAll(sel)).sort(function(a,b){return area(b)-area(a);})[0]; }
              function wrapper(v){
                var el=v, r=v.getBoundingClientRect();
                while (el.parentElement && el.parentElement!==document.body) {
                  var p=el.parentElement.getBoundingClientRect();
                  if (Math.abs(p.width-r.width)>6 || Math.abs(p.height-r.height)>6) break;
                  el=el.parentElement;
                }
                return el;
              }
              document.addEventListener('keydown', function(e){
                if (!e.isTrusted) return;
                var v = biggest('video');
                switch (e.key) {
                  case 'F9':
                    if (document.fullscreenElement || document.webkitFullscreenElement) { (document.exitFullscreen||document.webkitExitFullscreen).call(document); break; }
                    var t = v ? wrapper(v) : (window===window.top ? biggest('iframe') : null);
                    if (t) { var f = t.requestFullscreen || t.webkitRequestFullscreen; if (f) { var p=f.call(t); if (p && p.catch) p.catch(function(){}); } }
                    break;
                  case 'F10': if (v) { v.paused ? v.play() : v.pause(); } break;
                  case 'F11': if (v) v.currentTime += 10; break;
                  case 'F12': if (v) v.currentTime -= 10; break;
                  default: return;
                }
                e.preventDefault(); e.stopPropagation();
              }, true);
            })();
        """.trimIndent()
    }
}
