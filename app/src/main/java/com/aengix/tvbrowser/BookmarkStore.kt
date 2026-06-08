package com.aengix.tvbrowser

import android.content.Context
import org.json.JSONArray

class BookmarkStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(): List<Bookmark> {
        val raw = prefs.getString(KEY_BOOKMARKS, null) ?: return emptyList()
        val array = JSONArray(raw)
        return buildList {
            for (i in 0 until array.length()) {
                add(Bookmark.fromJson(array.getJSONObject(i)))
            }
        }.sortedByDescending { it.createdAt }
    }

    fun add(bookmark: Bookmark) {
        val bookmarks = getAll().toMutableList()
        bookmarks.add(0, bookmark)
        save(bookmarks)
    }

    fun update(bookmark: Bookmark) {
        val bookmarks = getAll().map {
            if (it.id == bookmark.id) bookmark else it
        }
        save(bookmarks)
    }

    fun delete(id: String) {
        save(getAll().filterNot { it.id == id })
    }

    fun findByUrl(url: String): Bookmark? =
        getAll().firstOrNull { it.url.equals(url, ignoreCase = true) }

    private fun save(bookmarks: List<Bookmark>) {
        val array = JSONArray()
        bookmarks.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_BOOKMARKS, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "aengix_tv_browser"
        private const val KEY_BOOKMARKS = "bookmarks"
    }
}
