package com.rillbrowser.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Entry(val title: String, val url: String)

/** All persisted data: settings, bookmarks, history. Local only. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("rill", Context.MODE_PRIVATE)

    var cursorSpeed: Int // 0 slow, 1 medium, 2 fast
        get() = prefs.getInt("cursorSpeed", 1)
        set(v) = prefs.edit().putInt("cursorSpeed", v).apply()
    var adsEnabled: Boolean
        get() = prefs.getBoolean("ads", true)
        set(v) = prefs.edit().putBoolean("ads", v).apply()
    var trackersEnabled: Boolean
        get() = prefs.getBoolean("trackers", true)
        set(v) = prefs.edit().putBoolean("trackers", v).apply()
    var blockRedirects: Boolean
        get() = prefs.getBoolean("redirects", true)
        set(v) = prefs.edit().putBoolean("redirects", v).apply()
    var desktopMode: Boolean
        get() = prefs.getBoolean("desktop", false)
        set(v) = prefs.edit().putBoolean("desktop", v).apply()
    /** Empty = start on the landing page; otherwise open this URL straight away on launch. */
    var startUrl: String
        get() = prefs.getString("startUrl", "") ?: ""
        set(v) = prefs.edit().putString("startUrl", v).apply()
    var clearCookiesOnExit: Boolean
        get() = prefs.getBoolean("clearCookies", false)
        set(v) = prefs.edit().putBoolean("clearCookies", v).apply()
    var historyEnabled: Boolean
        get() = prefs.getBoolean("historyOn", true)
        set(v) = prefs.edit().putBoolean("historyOn", v).apply()

    fun bookmarks(): List<Entry> = read("bookmarks")
    fun isBookmarked(url: String) = bookmarks().any { it.url == url }

    /** Returns true if the page is now bookmarked, false if it was removed. */
    fun toggleBookmark(title: String, url: String): Boolean {
        val list = bookmarks().toMutableList()
        val removed = list.removeAll { it.url == url }
        if (!removed) list.add(0, Entry(title.ifBlank { url }, url))
        write("bookmarks", list)
        return !removed
    }

    fun history(): List<Entry> = read("history")
    fun addHistory(title: String, url: String) {
        if (!historyEnabled) return
        val list = history().toMutableList()
        list.removeAll { it.url == url }
        list.add(0, Entry(title.ifBlank { url }, url))
        write("history", list.take(500))
    }
    fun clearHistory() = prefs.edit().remove("history").apply()

    private fun read(key: String): List<Entry> {
        val arr = JSONArray(prefs.getString(key, "[]"))
        return (0 until arr.length()).map { val o = arr.getJSONObject(it); Entry(o.getString("t"), o.getString("u")) }
    }

    private fun write(key: String, list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("t", it.title).put("u", it.url)) }
        prefs.edit().putString(key, arr.toString()).apply()
    }
}
