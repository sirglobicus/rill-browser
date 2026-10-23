package com.rillbrowser.app

import android.content.Context
import android.net.Uri
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Small content blocker for WebView (which has no extension support).
 *
 * Understands the parts of Adblock Plus syntax that matter most and are cheap to evaluate:
 *  - `||host^` (optionally `$third-party` etc.)  -> block requests to that host and its subdomains
 *  - `@@||host^`                                 -> exceptions
 *  - `##selector` / `site.com##selector`         -> element hiding (injected as CSS)
 *  - plain host lines (Peter Lowe's list)
 * Path-based and regex rules are skipped: weaker than uBlock Origin, but fast on a 2 GB stick.
 */
class AdBlocker(private val context: Context) {
    private class Rules {
        val always = HashSet<String>()
        val thirdParty = HashSet<String>()
        val allow = HashSet<String>()
        val generic = ArrayList<String>()
        val perSite = HashMap<String, ArrayList<String>>()
    }

    @Volatile private var rules = Rules()
    @Volatile private var genericCss = ""
    @Volatile var ruleCount = 0
        private set

    /** Parses the enabled lists on a background thread. Blocking is off until the first load completes. */
    fun reload(ads: Boolean, trackers: Boolean, done: () -> Unit = {}) {
        Thread {
            val r = Rules()
            if (ads) {
                parse(open("easylist.txt"), r)
                parse(open("peterlowe.txt"), r)
            }
            if (trackers) parse(open("easyprivacy.txt"), r)
            genericCss = cssFor(r.generic)
            ruleCount = r.always.size + r.thirdParty.size
            rules = r
            done()
        }.apply { priority = Thread.MIN_PRIORITY }.start()
    }

    /** Re-downloads the lists into app storage; they then take precedence over the bundled copies. */
    fun update(onDone: (Boolean) -> Unit) {
        Thread {
            var ok = true
            val dir = File(context.filesDir, "filters").apply { mkdirs() }
            for ((name, url) in LISTS) {
                try {
                    val c = URL(url).openConnection() as HttpURLConnection
                    c.connectTimeout = 15000
                    c.readTimeout = 30000
                    val tmp = File(dir, "$name.tmp")
                    c.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    tmp.renameTo(File(dir, name))
                } catch (e: Exception) {
                    ok = false
                }
            }
            onDone(ok)
        }.start()
    }

    fun shouldBlock(url: Uri, pageHost: String?): Boolean {
        val r = rules
        val host = url.host?.lowercase() ?: return false
        if (matches(r.allow, host)) return false
        if (matches(r.always, host)) return true
        return matches(r.thirdParty, host) && pageHost != null && base(host) != base(pageHost)
    }

    /** CSS hiding the page's ad containers; empty when there is nothing to hide. */
    fun cssFor(pageHost: String?): String {
        val r = rules
        val sb = StringBuilder(genericCss)
        var h = pageHost?.lowercase()
        while (h != null && h.contains('.')) {
            r.perSite[h]?.let { sb.append(cssFor(it)) }
            h = h.substringAfter('.', "")
        }
        return sb.toString()
    }

    private fun cssFor(selectors: List<String>): String {
        val sb = StringBuilder()
        for (s in selectors) sb.append(s).append("{display:none!important}\n")
        return sb.toString()
    }

    private fun open(name: String) =
        File(context.filesDir, "filters/$name").takeIf { it.exists() }?.inputStream()
            ?: context.assets.open("filters/$name")

    private fun parse(input: java.io.InputStream, r: Rules) {
        input.bufferedReader().useLines { lines ->
            for (raw in lines) {
                val line = raw.trim()
                if (line.isEmpty() || line[0] == '!' || line[0] == '[' || line[0] == '#') continue
                val cosmetic = line.indexOf("##")
                when {
                    cosmetic >= 0 -> parseCosmetic(line, cosmetic, r)
                    line.startsWith("@@||") -> hostRule(line.substring(4), r.allow, r.allow, allowRule = true)
                    line.startsWith("||") -> hostRule(line.substring(2), r.always, r.thirdParty, allowRule = false)
                    line[0] != '|' && line[0] != '/' && line[0] != '@' && HOST.matches(line) -> r.always.add(line.lowercase())
                }
            }
        }
    }

    private fun hostRule(body: String, always: HashSet<String>, thirdParty: HashSet<String>, allowRule: Boolean) {
        val caret = body.indexOf('^')
        val end = if (caret >= 0) caret else return // needs the ^ separator right after the host
        val host = body.substring(0, end)
        if (!HOST.matches(host)) return
        val rest = body.substring(end + 1)
        if (rest.isEmpty()) {
            always.add(host.lowercase())
        } else if (rest.startsWith("$")) {
            val opts = rest.substring(1).split(',')
            if (opts.any { it.startsWith("~") || it.startsWith("domain=") || it == "document" || it == "generichide" || it == "elemhide" }) return
            if (allowRule) return
            thirdParty.add(host.lowercase())
        }
    }

    private fun parseCosmetic(line: String, at: Int, r: Rules) {
        if (line.length > at + 2 && line[at + 2] == '#') return
        if (at > 0 && (line[at - 1] == '@' || line[at - 1] == '?' || line[at - 1] == '$' || line[at - 1] == '%')) return
        val selector = line.substring(at + 2)
        if (selector.isEmpty() || UNSUPPORTED.any { selector.contains(it) }) return
        if (at == 0) {
            r.generic.add(selector)
        } else {
            for (d in line.substring(0, at).split(',')) {
                if (d.startsWith("~")) continue
                r.perSite.getOrPut(d.lowercase()) { ArrayList() }.add(selector)
            }
        }
    }

    private fun matches(set: HashSet<String>, host: String): Boolean {
        var h = host
        while (h.contains('.')) { // stops before the bare TLD
            if (h in set) return true
            h = h.substringAfter('.')
        }
        return false
    }

    companion object {
        private val HOST = Regex("^[a-zA-Z0-9][a-zA-Z0-9.-]*\\.[a-zA-Z]{2,}$")
        private val UNSUPPORTED = listOf(":-abp-", ":has-text(", ":xpath(", ":contains(", ":matches-", ":upward(", ":remove(", "+js(", ":style(", ":nth-ancestor(")
        private val LISTS = mapOf(
            "easylist.txt" to "https://easylist.to/easylist/easylist.txt",
            "easyprivacy.txt" to "https://easylist.to/easylist/easyprivacy.txt",
            "peterlowe.txt" to "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=nohtml&showintro=0&mimetype=plaintext",
        )

        /** Registrable-domain approximation: last two labels, or three for names like foo.co.uk. */
        fun base(host: String): String {
            val p = host.split('.')
            if (p.size >= 3 && p.last().length == 2 && p[p.size - 2].length <= 3) return p.takeLast(3).joinToString(".")
            return p.takeLast(2).joinToString(".")
        }
    }
}
