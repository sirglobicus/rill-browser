package com.rillbrowser.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var root: FrameLayout
    private lateinit var landing: LandingView
    private lateinit var webHost: FrameLayout
    private lateinit var cursor: CursorView
    private lateinit var panel: ListPanel
    private lateinit var browser: BrowserController
    private lateinit var cursorInput: CursorInput

    private val handler = Handler(Looper.getMainLooper())
    private val panelStack = ArrayList<() -> Unit>()
    private var customView: View? = null
    private var browsing = false
    private val panelOpen get() = panel.visibility == View.VISIBLE

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        webHost = FrameLayout(this).apply { visibility = View.GONE }
        cursor = CursorView(this).apply { visibility = View.GONE }
        panel = ListPanel(this).apply { visibility = View.GONE }
        landing = LandingView(
            this,
            onUrl = { promptText("Enter URL", "https://") { browse(UrlParser.toUrl(it)) } },
            onSearch = { promptText("Search or enter a URL", "") { browse(UrlParser.toUrl(it)) } },
            onBookmarks = { pushPanel { showBookmarks() } },
            onSettings = { pushPanel { showSettings() } },
        )

        root.addView(webHost, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        root.addView(cursor, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        root.addView(landing, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        root.addView(panel, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setContentView(root)

        browser = BrowserController(
            this, webHost, store, App.blocker,
            fullScreenChanged = { v -> setCustomView(v) },
            contextRequested = { link, src -> showContextMenu(link, src) },
            renderGone = {
                Toast.makeText(this, "The page ran out of memory and was closed", Toast.LENGTH_LONG).show()
                endBrowsing(clearCookies = false)
            },
        )
        cursorInput = CursorInput(
            cursor, resources.displayMetrics.density,
            target = { browser.webView },
            speed = { store.cursorSpeed },
            onClick = { browser.noteUserClick() },
            onMoved = { if (browsing && !panelOpen && cursor.visibility != View.VISIBLE) cursor.visibility = View.VISIBLE },
        )
    }

    override fun onStart() {
        super.onStart()
        showLanding()
        if (store.startUrl.isNotBlank()) browse(store.startUrl)
    }

    // Session rule: nothing stays open once the app is no longer visible.
    override fun onStop() {
        super.onStop()
        endBrowsing(clearCookies = true)
    }

    override fun onDestroy() {
        endBrowsing(clearCookies = true)
        super.onDestroy()
    }

    // ---- screens ----

    private fun showLanding() {
        browsing = false
        landing.visibility = View.VISIBLE
        webHost.visibility = View.GONE
        cursor.visibility = View.GONE
        panel.visibility = View.GONE
        landing.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS // a panel may have blocked it
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        landing.defaultFocus.requestFocus()
    }

    private fun browse(url: String) {
        if (url.isBlank()) return
        browsing = true
        closePanels()
        landing.visibility = View.GONE
        webHost.visibility = View.VISIBLE
        cursor.visibility = View.VISIBLE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        browser.open(url)
        browser.webView?.requestFocus()
    }

    private fun endBrowsing(clearCookies: Boolean) {
        if (!::cursorInput.isInitialized) return
        cursorInput.reset()
        panelStack.clear()
        browser.close(clearCookies)
        setCustomView(null)
        showLanding()
    }

    /** Fullscreen video from the page's own player button (or our Fullscreen menu item). */
    private fun setCustomView(v: View?) {
        customView?.let { root.removeView(it) }
        customView = v
        if (v != null) {
            root.addView(v, root.indexOfChild(cursor), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            cursor.visibility = View.GONE // hidden until the user moves it
        } else if (browsing && !panelOpen) {
            cursor.visibility = View.VISIBLE
        }
    }

    // ---- panels (menu, bookmarks, history, settings) ----

    private fun pushPanel(render: () -> Unit) {
        cursorInput.reset()
        cursor.visibility = View.GONE
        browser.webView?.clearFocus()
        setBackgroundFocusable(false)
        panelStack.add(render)
        if (!panelOpen) {
            panel.alpha = 0f
            panel.visibility = View.VISIBLE
            panel.animate().alpha(1f).setDuration(140).start()
        }
        render()
    }

    /** While a panel is open the D-pad must stay inside it, not wander to the page or landing tiles. */
    private fun setBackgroundFocusable(on: Boolean) {
        browser.webView?.let {
            it.isFocusable = on
            it.isFocusableInTouchMode = on
        }
        landing.descendantFocusability =
            if (on) ViewGroup.FOCUS_AFTER_DESCENDANTS else ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }

    private fun popPanel() {
        if (panelStack.isNotEmpty()) panelStack.removeAt(panelStack.lastIndex)
        if (panelStack.isEmpty()) {
            closePanels()
        } else {
            val render = panelStack[panelStack.lastIndex]
            render()
        }
    }

    private fun closePanels() {
        panelStack.clear()
        panel.visibility = View.GONE
        setBackgroundFocusable(true)
        if (browsing) {
            if (!browser.fullscreen) cursor.visibility = View.VISIBLE
            browser.webView?.requestFocus()
        } else {
            landing.defaultFocus.requestFocus()
        }
    }

    private fun showMenu() {
        val bookmarked = store.isBookmarked(browser.url)
        panel.show(
            title = null,
            items = listOf(
                ListPanel.Field(browser.url.ifBlank { "about:blank" }) {
                    promptText("Enter URL or search", browser.url) { browse(UrlParser.toUrl(it)) }
                },
                ListPanel.Duo(
                    ListPanel.Row("Previous", IconView.Kind.ARROW_LEFT, dim = !browser.canGoBack) { closePanels(); browser.goBack() },
                    ListPanel.Row("Next", IconView.Kind.ARROW_RIGHT, dim = !browser.canGoForward) { closePanels(); browser.goForward() },
                ),
                ListPanel.Row(if (browser.loading) "Stop Loading" else "Reload Page", IconView.Kind.RELOAD) { closePanels(); browser.reloadOrStop() },
                ListPanel.Row(if (bookmarked) "Remove Bookmark" else "Add to Bookmarks") {
                    store.toggleBookmark(browser.title, browser.url)
                    showMenu()
                },
                ListPanel.Row("View Bookmarks") { pushPanel { showBookmarks() } },
                ListPanel.Row("Home") { endBrowsing(clearCookies = false) },
                ListPanel.Row("Desktop Site: ${if (store.desktopMode) "On" else "Off"}") {
                    closePanels(); browser.setDesktopMode(!store.desktopMode)
                },
                ListPanel.Row(if (browser.fullscreen) "Exit Fullscreen" else "Fullscreen Video") {
                    closePanels(); toggleFullscreen()
                },
                ListPanel.Row("Settings") { pushPanel { showSettings() } },
                ListPanel.Row("Exit Application") { finish() },
            ),
            backLabel = "Close",
        ) { closePanels() }
    }

    /** Fullscreen needs a user gesture in the page; a real key press (handled by the page helper script) counts. */
    private fun toggleFullscreen() {
        if (browser.fullscreen) browser.exitFullScreen()
        else handler.postDelayed({ browser.sendKey(BrowserController.KEY_FULLSCREEN) }, 150)
    }

    private fun showBookmarks() {
        val rows = store.bookmarks().map { b -> ListPanel.Row(b.title.take(60)) { browse(b.url) } }
        panel.show("BOOKMARKS", rows.ifEmpty { listOf(ListPanel.Row("No bookmarks yet", dim = true) {}) }, backLabel = "Back", focusIndex = 0) { popPanel() }
    }

    private fun showHistory() {
        val rows = store.history().map { h -> ListPanel.Row(h.title.take(60)) { browse(h.url) } }
        panel.show("HISTORY", rows.ifEmpty { listOf(ListPanel.Row("No history", dim = true) {}) }, backLabel = "Back", focusIndex = 0) { popPanel() }
    }

    private fun applyBlockerSettings() = App.blocker.reload(store.adsEnabled, store.trackersEnabled)

    private fun showSettings(focus: Int = 0) {
        fun onOff(v: Boolean) = if (v) "ON" else "OFF"
        val speeds = listOf("Slow", "Medium", "Fast")
        panel.show(
            "SETTINGS",
            listOf(
                ListPanel.Row("Block ads: ${onOff(store.adsEnabled)}") {
                    store.adsEnabled = !store.adsEnabled; applyBlockerSettings(); showSettings(0)
                },
                ListPanel.Row("Block trackers: ${onOff(store.trackersEnabled)}") {
                    store.trackersEnabled = !store.trackersEnabled; applyBlockerSettings(); showSettings(1)
                },
                ListPanel.Row("Block redirects: ${onOff(store.blockRedirects)}") {
                    store.blockRedirects = !store.blockRedirects; showSettings(2)
                },
                ListPanel.Row("Update filters") {
                    Toast.makeText(this, "Updating filters...", Toast.LENGTH_SHORT).show()
                    App.blocker.update { ok ->
                        applyBlockerSettings()
                        handler.post {
                            Toast.makeText(this, if (ok) "Filters updated (${App.blocker.ruleCount} rules)" else "Some filters failed to download", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                ListPanel.Row("History") { pushPanel { showHistory() } },
                ListPanel.Row("Clear history") {
                    store.clearHistory(); Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
                },
                ListPanel.Row("Save history: ${onOff(store.historyEnabled)}") {
                    store.historyEnabled = !store.historyEnabled; showSettings(6)
                },
                ListPanel.Row("Cursor speed: ${speeds[store.cursorSpeed]}") {
                    store.cursorSpeed = (store.cursorSpeed + 1) % speeds.size; showSettings(7)
                },
                ListPanel.Row("Start page: ${if (store.startUrl.isBlank()) "Home" else store.startUrl.removePrefix("https://").removePrefix("http://").take(18)}") {
                    promptText("Start URL (leave empty for the landing page)", store.startUrl) {
                        store.startUrl = if (it.isBlank()) "" else UrlParser.toUrl(it)
                        showSettings(8)
                    }
                },
                ListPanel.Row("Clear cookies on exit: ${onOff(store.clearCookiesOnExit)}") {
                    store.clearCookiesOnExit = !store.clearCookiesOnExit; showSettings(9)
                },
            ),
            backLabel = "Back",
            focusIndex = focus,
        ) { popPanel() }
    }

    private fun showContextMenu(link: String?, src: String?) {
        cursorInput.reset()
        val items = ArrayList<Pair<String, () -> Unit>>()
        link?.let {
            items += "Open link" to { browser.open(it) }
            items += "Copy link" to {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("link", it))
            }
        }
        src?.let { items += "Open image in this tab" to { browser.open(it) } }
        if (items.isEmpty()) return
        AlertDialog.Builder(this)
            .setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second() }
            .show()
    }

    private fun promptText(title: String, initial: String, onDone: (String) -> Unit) {
        cursorInput.reset()
        val input = EditText(this).apply {
            setText(initial)
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_GO
            selectAll()
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Go") { _, _ -> onDone(input.text.toString()) }
            .setNegativeButton("Cancel", null)
            .create()
        input.setOnEditorActionListener { _, _, _ ->
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick(); true
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()
    }

    // ---- keys ----

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode == KeyEvent.KEYCODE_BACK) {
            if (e.action == KeyEvent.ACTION_UP) onBackKey()
            return true
        }
        if (browsing && !panelOpen) {
            val player = when (e.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> BrowserController.KEY_PLAY_PAUSE
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_NEXT -> BrowserController.KEY_FORWARD
                KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> BrowserController.KEY_BACK
                else -> 0
            }
            if (player != 0) {
                if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) browser.sendKey(player)
                return true
            }
            if (cursorInput.onKey(e)) return true
        }
        return super.dispatchKeyEvent(e)
    }

    private fun onBackKey() {
        when {
            panelOpen -> popPanel()
            browsing -> pushPanel { showMenu() }
            else -> finish()
        }
    }
}
