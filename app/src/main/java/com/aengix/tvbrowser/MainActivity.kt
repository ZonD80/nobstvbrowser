package com.aengix.tvbrowser

import android.os.Bundle
import android.view.KeyEvent
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit

class MainActivity : AppCompatActivity(),
    BookmarksFragment.Listener,
    BrowserFragment.Listener {

    private lateinit var bookmarkStore: BookmarkStore
    private lateinit var dpadCursor: DpadCursorController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bookmarkStore = BookmarkStore(this)

        val contentRoot = findViewById<FrameLayout>(R.id.root_container)
        dpadCursor = DpadCursorController(this, contentRoot)
        dpadCursor.attach()
        registerDialogCursorCallbacks()

        if (savedInstanceState == null) {
            val launchUrl = intent.getStringExtra(EXTRA_URL) ?: intent.data?.toString()
            if (!launchUrl.isNullOrBlank()) {
                showBrowser(launchUrl)
            } else {
                showBookmarks()
            }
        }
    }

    override fun onDestroy() {
        dpadCursor.detach()
        super.onDestroy()
    }

    override fun onOpenUrl(url: String) {
        openBrowser(url)
    }

    fun openBrowser(url: String) {
        showBrowser(url)
    }

    override fun onOpenSettings() {
        showSettings()
    }

    override fun onExitBrowser() {
        bindWebViewForEdgeScroll(null)
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (dpadCursor.handleKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        val current = supportFragmentManager.findFragmentById(R.id.fragment_container)
        when (current) {
            is BrowserFragment -> current.handleBack()
            else -> {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                } else {
                    @Suppress("DEPRECATION")
                    super.onBackPressed()
                }
            }
        }
    }

    private fun registerDialogCursorCallbacks() {
        supportFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentStarted(fm: FragmentManager, f: androidx.fragment.app.Fragment) {
                    if (f is DialogFragment) dpadCursor.onDialogVisibilityChanged()
                }

                override fun onFragmentStopped(fm: FragmentManager, f: androidx.fragment.app.Fragment) {
                    if (f is DialogFragment) dpadCursor.onDialogVisibilityChanged()
                }
            },
            true
        )
    }

    private fun showBookmarks() {
        bindWebViewForEdgeScroll(null)
        supportFragmentManager.commit {
            replace(R.id.fragment_container, BookmarksFragment.newInstance())
        }
    }

    private fun showBrowser(url: String) {
        supportFragmentManager.commit {
            replace(R.id.fragment_container, BrowserFragment.newInstance(url))
            addToBackStack("browser")
        }
    }

    private fun showSettings() {
        bindWebViewForEdgeScroll(null)
        supportFragmentManager.commit {
            replace(R.id.fragment_container, SettingsFragment.newInstance())
            addToBackStack("settings")
        }
    }

    fun bookmarkStore(): BookmarkStore = bookmarkStore

    fun bindWebViewForEdgeScroll(webView: android.webkit.WebView?) {
        dpadCursor.bindWebViewForEdgeScroll(webView)
    }

    fun setWebViewPageLoading(loading: Boolean) {
        dpadCursor.setWebViewPageLoading(loading)
    }

    companion object {
        const val EXTRA_URL = "url"
    }
}
