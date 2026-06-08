package com.aengix.tvbrowser

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.view.inputmethod.EditorInfo
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.aengix.tvbrowser.databinding.FragmentBrowserBinding
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class BrowserFragment : Fragment() {

    interface Listener {
        fun onExitBrowser()
    }

    private var listener: Listener? = null
    private var _binding: FragmentBrowserBinding? = null
    private val binding get() = _binding!!
    private lateinit var bookmarkStore: BookmarkStore
    private val mainHandler = Handler(Looper.getMainLooper())
    private val timeoutExecutor = Executors.newSingleThreadScheduledExecutor()
    private var loadTimeoutFuture: ScheduledFuture<*>? = null
    private var pendingUrl: String? = null
    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? Listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBrowserBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bookmarkStore = (requireActivity() as MainActivity).bookmarkStore()

        val startUrl = requireArguments().getString(ARG_URL).orEmpty()
        setupWebView()
        setupToolbar()

        if (savedInstanceState == null) {
            loadUrl(startUrl)
        }
    }

    override fun onResume() {
        super.onResume()
        _binding?.webView?.onResume()
    }

    override fun onPause() {
        _binding?.webView?.onPause()
        super.onPause()
    }

    private fun setupWebView() {
        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true
        }

        binding.webView.isFocusableInTouchMode = true

        mainActivity().bindWebViewForEdgeScroll(binding.webView)

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = false

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                view.post {
                    if (!isAdded || _binding == null) return@post
                    onLoadStarted(url)
                }
            }

            override fun onPageFinished(view: WebView, url: String) {
                view.post {
                    if (!isAdded || _binding == null) return@post
                    onLoadFinished(url)
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    view.post {
                        if (!isAdded || _binding == null) return@post
                        onLoadFinished(view.url ?: pendingUrl.orEmpty())
                    }
                }
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail
            ): Boolean {
                view.post {
                    if (!isAdded || _binding == null) return@post
                    endPageLoad()
                    val recoverUrl = view.url?.takeIf { it.isNotBlank() } ?: pendingUrl
                    if (!recoverUrl.isNullOrBlank()) {
                        view.post { loadUrl(recoverUrl) }
                    }
                }
                return true
            }
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                showFullscreenPlayer(view, callback)
            }

            override fun onHideCustomView() {
                hideFullscreenPlayer()
            }

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                view.post {
                    if (!isAdded || _binding == null) return@post
                    binding.progressBar.progress = newProgress
                }
            }
        }

        binding.webView.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                handleBack()
                true
            } else {
                false
            }
        }
    }

    private fun setupToolbar() {
        binding.buttonBack.setOnClickListener { handleBack() }
        binding.buttonForward.setOnClickListener {
            binding.webView.post {
                if (binding.webView.canGoForward()) {
                    binding.webView.stopLoading()
                    binding.webView.goForward()
                }
            }
        }
        binding.buttonReload.setOnClickListener { reloadPage() }
        binding.buttonBookmarks.setOnClickListener { listener?.onExitBrowser() }

        binding.editUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                loadFromUrlBar()
                true
            } else {
                false
            }
        }

        binding.buttonAddBookmark.setOnClickListener { showAddBookmarkDialog() }
    }

    private fun reloadPage() {
        binding.webView.stopLoading()
        pauseForPendingLoad()
        binding.webView.post {
            binding.webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
            binding.webView.reload()
            binding.webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        }
    }

    private fun loadUrl(url: String) {
        pendingUrl = url
        binding.webView.stopLoading()
        pauseForPendingLoad()
        binding.editUrl.setText(url)
        binding.webView.post { binding.webView.loadUrl(url) }
    }

    private fun loadFromUrlBar() {
        val normalized = UrlUtils.normalize(binding.editUrl.text?.toString().orEmpty())
        if (normalized == null) {
            Toast.makeText(requireContext(), R.string.invalid_url, Toast.LENGTH_SHORT).show()
            return
        }
        loadUrl(normalized)
    }

    private fun onLoadStarted(url: String) {
        pauseForPendingLoad()
        binding.editUrl.setText(url)
        postUpdateNavButtons()
    }

    private fun onLoadFinished(url: String) {
        endPageLoad()
        binding.editUrl.setText(url)
        postUpdateNavButtons()
    }

    private fun pauseForPendingLoad() {
        mainActivity().setWebViewPageLoading(true)
        binding.progressBar.visibility = View.VISIBLE
        scheduleLoadTimeout()
    }

    private fun endPageLoad() {
        clearLoadTimeout()
        mainActivity().setWebViewPageLoading(false)
        binding.progressBar.visibility = View.GONE
    }

    private fun scheduleLoadTimeout() {
        clearLoadTimeout()
        loadTimeoutFuture = timeoutExecutor.schedule({
            mainHandler.post {
                if (!isAdded || _binding == null) return@post
                if (binding.progressBar.visibility != View.VISIBLE) return@post
                binding.webView.stopLoading()
                endPageLoad()
                Toast.makeText(requireContext(), R.string.page_load_timeout, Toast.LENGTH_SHORT)
                    .show()
            }
        }, LOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }

    private fun clearLoadTimeout() {
        loadTimeoutFuture?.cancel(false)
        loadTimeoutFuture = null
    }

    private fun postUpdateNavButtons() {
        binding.root.post {
            if (!isAdded || _binding == null) return@post
            binding.buttonBack.isEnabled = binding.webView.canGoBack() ||
                parentFragmentManager.backStackEntryCount > 0
            binding.buttonForward.isEnabled = binding.webView.canGoForward()
        }
    }

    private fun showAddBookmarkDialog() {
        binding.webView.post {
            if (!isAdded) return@post
            val currentUrl = binding.webView.url ?: return@post
            val pageTitle = binding.webView.title?.trim().orEmpty().ifEmpty { currentUrl }
            val existing = bookmarkStore.findByUrl(currentUrl)

            val bookmark = existing?.copy(title = pageTitle)
                ?: Bookmark(title = pageTitle, url = currentUrl)

            BookmarkEditDialogFragment(bookmark) { }.show(parentFragmentManager, "add_bookmark")
        }
    }

    fun handleBack() {
        if (fullscreenView != null) {
            hideFullscreenPlayer()
            return
        }
        binding.webView.stopLoading()
        binding.webView.post {
            if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            } else {
                listener?.onExitBrowser()
            }
        }
    }

    private fun showFullscreenPlayer(view: View?, callback: WebChromeClient.CustomViewCallback?) {
        if (view == null || _binding == null) {
            callback?.onCustomViewHidden()
            return
        }
        if (fullscreenView != null) {
            callback?.onCustomViewHidden()
            return
        }

        fullscreenView = view
        fullscreenCallback = callback

        binding.browserContent.visibility = View.GONE
        binding.fullscreenContainer.visibility = View.VISIBLE
        binding.fullscreenContainer.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        mainActivity().bindWebViewForEdgeScroll(null)
        enterImmersiveMode()
    }

    private fun hideFullscreenPlayer() {
        if (_binding == null) return

        fullscreenView?.let { binding.fullscreenContainer.removeView(it) }
        fullscreenView = null
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null

        if (binding.fullscreenContainer.visibility != View.VISIBLE) return

        binding.fullscreenContainer.visibility = View.GONE
        binding.browserContent.visibility = View.VISIBLE
        exitImmersiveMode()
        mainActivity().bindWebViewForEdgeScroll(binding.webView)
    }

    @Suppress("DEPRECATION")
    private fun enterImmersiveMode() {
        requireActivity().window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
    }

    @Suppress("DEPRECATION")
    private fun exitImmersiveMode() {
        requireActivity().window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }

    private fun mainActivity(): MainActivity = requireActivity() as MainActivity

    override fun onDestroyView() {
        clearLoadTimeout()
        timeoutExecutor.shutdownNow()
        hideFullscreenPlayer()
        endPageLoad()
        mainActivity().bindWebViewForEdgeScroll(null)
        binding.webView.apply {
            stopLoading()
            destroy()
        }
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_URL = "arg_url"
        private const val LOAD_TIMEOUT_MS = 20_000L

        fun newInstance(url: String): BrowserFragment = BrowserFragment().apply {
            arguments = Bundle().apply { putString(ARG_URL, url) }
        }
    }
}
