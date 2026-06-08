package com.aengix.tvbrowser

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView

object BrowsingDataCleaner {
    fun clear(context: Context) {
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }
        WebStorage.getInstance().deleteAllData()
        WebView(context.applicationContext).apply {
            clearCache(true)
            clearHistory()
            clearFormData()
        }
    }
}
