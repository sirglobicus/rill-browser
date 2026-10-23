package com.rillbrowser.app

import java.net.URLEncoder

/** Turns whatever the user typed into a loadable URL: direct if it looks like one, otherwise a Google search. */
object UrlParser {
    private val scheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://\\S+$")
    private val domain = Regex("^([a-z0-9]([a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,}$", RegexOption.IGNORE_CASE)
    private val ipv4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    fun toUrl(input: String): String {
        val t = input.trim()
        if (t.isEmpty()) return ""
        if (scheme.matches(t) || t.startsWith("about:")) return t
        if (!t.any { it.isWhitespace() }) {
            val host = t.substringBefore('/').substringBefore('?').substringBefore('#').substringBefore(':')
            if (host.equals("localhost", true) || ipv4.matches(host)) return "http://$t"
            if (domain.matches(host)) return "https://$t"
        }
        return "https://www.google.com/search?q=" + URLEncoder.encode(t, "UTF-8")
    }
}
