package com.aengix.tvbrowser

import android.net.Uri
import java.util.Locale

object UrlUtils {
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val withScheme = when {
            trimmed.contains("://") -> trimmed
            trimmed.startsWith("localhost", ignoreCase = true) -> "http://$trimmed"
            else -> "https://$trimmed"
        }

        val uri = Uri.parse(withScheme) ?: return null
        val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
        if (scheme !in setOf("http", "https")) return null

        val host = uri.host
        if (host.isNullOrBlank()) return null

        return withScheme
    }
}
