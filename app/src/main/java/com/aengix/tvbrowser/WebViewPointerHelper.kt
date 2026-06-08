package com.aengix.tvbrowser

import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.MotionEvent
import android.webkit.WebView

class WebViewPointerHelper(val webView: WebView) {

    private val handler = Handler(Looper.getMainLooper())
    private var edgeSizePx = 0f
    private var pointerInside = false
    private var lastX = 0f
    private var lastY = 0f
    private var ticking = false
    private var pageLoading = false

    private val tickRunnable = Runnable { tick() }

    fun attach() {
        edgeSizePx = EDGE_SIZE_DP * webView.resources.displayMetrics.density
        webView.setOnGenericMotionListener { _, event ->
            if (!event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) return@setOnGenericMotionListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_MOVE,
                MotionEvent.ACTION_MOVE -> {
                    updateVirtualCursor(event.x, event.y)
                    event.actionMasked == MotionEvent.ACTION_HOVER_MOVE
                }

                MotionEvent.ACTION_HOVER_ENTER -> {
                    updateVirtualCursor(event.x, event.y)
                    false
                }

                MotionEvent.ACTION_HOVER_EXIT,
                MotionEvent.ACTION_OUTSIDE -> {
                    clearVirtualCursor()
                    false
                }

                MotionEvent.ACTION_SCROLL -> {
                    if (!pageLoading && isInside(event.x, event.y)) {
                        updateVirtualCursor(event.x, event.y)
                        handleWheelScroll(event)
                    }
                    true
                }

                else -> false
            }
        }

        webView.setOnHoverListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_MOVE,
                MotionEvent.ACTION_HOVER_ENTER -> updateVirtualCursor(event.x, event.y)
                MotionEvent.ACTION_HOVER_EXIT -> clearVirtualCursor()
            }
            false
        }
    }

    fun detach() {
        stopTicking()
        webView.setOnGenericMotionListener(null)
        webView.setOnHoverListener(null)
    }

    fun setPageLoading(loading: Boolean) {
        pageLoading = loading
        if (loading) clearVirtualCursor()
    }

    fun updateVirtualCursor(localX: Float, localY: Float) {
        if (pageLoading) return
        pointerInside = isInside(localX, localY)
        lastX = localX
        lastY = localY
        if (pointerInside) ensureTicking() else stopTicking()
    }

    fun clearVirtualCursor() {
        pointerInside = false
        stopTicking()
    }

    private fun handleWheelScroll(event: MotionEvent) {
        val vertical = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
        val horizontal = event.getAxisValue(MotionEvent.AXIS_HSCROLL)

        if (vertical != 0f) {
            val delta = (-vertical * WHEEL_SCROLL_FACTOR).toInt()
            if (delta < 0 && webView.canScrollVertically(-1)) {
                webView.scrollBy(0, delta)
            } else if (delta > 0 && webView.canScrollVertically(1)) {
                webView.scrollBy(0, delta)
            }
        }
        if (horizontal != 0f) {
            val delta = (-horizontal * WHEEL_SCROLL_FACTOR).toInt()
            if (delta < 0 && webView.canScrollHorizontally(-1)) {
                webView.scrollBy(delta, 0)
            } else if (delta > 0 && webView.canScrollHorizontally(1)) {
                webView.scrollBy(delta, 0)
            }
        }
    }

    private fun tick() {
        ticking = false
        if (pageLoading || !pointerInside || !isInside(lastX, lastY)) {
            pointerInside = false
            return
        }

        val edges = edgesAt(lastX, lastY)
        if (edges == Edge.NONE) return

        val step = scrollStepFor(lastX, lastY, edges)
        var scrolled = false

        if (hasEdge(edges, Edge.TOP) && webView.canScrollVertically(-1)) {
            webView.scrollBy(0, -step)
            scrolled = true
        }
        if (hasEdge(edges, Edge.BOTTOM) && webView.canScrollVertically(1)) {
            webView.scrollBy(0, step)
            scrolled = true
        }
        if (hasEdge(edges, Edge.LEFT) && webView.canScrollHorizontally(-1)) {
            webView.scrollBy(-step, 0)
            scrolled = true
        }
        if (hasEdge(edges, Edge.RIGHT) && webView.canScrollHorizontally(1)) {
            webView.scrollBy(step, 0)
            scrolled = true
        }

        if (scrolled && pointerInside && edges != Edge.NONE) {
            ensureTicking(TICK_INTERVAL_MS)
        }
    }

    private fun scrollStepFor(x: Float, y: Float, edges: Int): Int {
        var intensity = 0f
        if (hasEdge(edges, Edge.TOP)) {
            intensity = maxOf(intensity, 1f - (y / edgeSizePx))
        }
        if (hasEdge(edges, Edge.BOTTOM)) {
            intensity = maxOf(intensity, 1f - ((webView.height - y) / edgeSizePx))
        }
        if (hasEdge(edges, Edge.LEFT)) {
            intensity = maxOf(intensity, 1f - (x / edgeSizePx))
        }
        if (hasEdge(edges, Edge.RIGHT)) {
            intensity = maxOf(intensity, 1f - ((webView.width - x) / edgeSizePx))
        }
        intensity = intensity.coerceIn(0.2f, 1f)
        return (MIN_SCROLL_STEP + (MAX_SCROLL_STEP - MIN_SCROLL_STEP) * intensity).toInt()
    }

    private fun edgesAt(x: Float, y: Float): Int {
        if (!isInside(x, y)) return Edge.NONE

        var edges = Edge.NONE
        if (y <= edgeSizePx) edges = edges or Edge.TOP
        if (y >= webView.height - edgeSizePx) edges = edges or Edge.BOTTOM
        if (x <= edgeSizePx) edges = edges or Edge.LEFT
        if (x >= webView.width - edgeSizePx) edges = edges or Edge.RIGHT
        return edges
    }

    private fun isInside(x: Float, y: Float): Boolean {
        return x >= 0f && y >= 0f && x <= webView.width && y <= webView.height
    }

    private fun ensureTicking(delayMs: Long = TICK_INTERVAL_MS) {
        if (ticking || pageLoading) return
        ticking = true
        handler.postDelayed(tickRunnable, delayMs)
    }

    private fun stopTicking() {
        handler.removeCallbacks(tickRunnable)
        ticking = false
    }

    private fun hasEdge(edges: Int, flag: Int): Boolean = edges and flag != 0

    private object Edge {
        const val NONE = 0
        const val TOP = 1
        const val BOTTOM = 2
        const val LEFT = 4
        const val RIGHT = 8
    }

    companion object {
        private const val EDGE_SIZE_DP = 56f
        private const val WHEEL_SCROLL_FACTOR = 120f
        private const val MIN_SCROLL_STEP = 8
        private const val MAX_SCROLL_STEP = 36
        private const val TICK_INTERVAL_MS = 32L
    }
}
