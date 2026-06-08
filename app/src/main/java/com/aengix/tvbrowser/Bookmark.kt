package com.aengix.tvbrowser

import org.json.JSONObject
import java.util.UUID

data class Bookmark(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("url", url)
        .put("createdAt", createdAt)

    companion object {
        fun fromJson(json: JSONObject): Bookmark = Bookmark(
            id = json.getString("id"),
            title = json.getString("title"),
            url = json.getString("url"),
            createdAt = json.optLong("createdAt", System.currentTimeMillis())
        )
    }
}
