package com.aengix.tvbrowser

import android.app.Dialog
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputLayout
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import kotlin.math.roundToInt

class DpadCursorController(
    private val activity: AppCompatActivity,
    private val contentRoot: ViewGroup
) {
    private val handler = Handler(Looper.getMainLooper())
    private val cursorView = ImageView(activity).apply {
        setImageResource(R.drawable.ic_cursor)
        isFocusable = false
        isClickable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        elevation = 100f
    }

    private var cursorScreenX = 0f
    private var cursorScreenY = 0f
    private var moveStepPx = 0f
    private var cursorHost: ViewGroup? = null
    private var keyInterceptedDialog: Dialog? = null
    private var originalWindowCallback: Window.Callback? = null
    private var edgeScrollHelper: WebViewPointerHelper? = null
    private var pageLoading = false
    private var repeatDirection = 0
    private var repeating = false

    private val repeatRunnable = object : Runnable {
        override fun run() {
            if (repeatDirection == 0) {
                repeating = false
                return
            }
            if (moveCursor(repeatDirection)) {
                handler.postDelayed(this, KEY_REPEAT_INTERVAL_MS)
            } else {
                repeating = false
                repeatDirection = 0
            }
        }
    }

    fun attach() {
        moveStepPx = MOVE_STEP_DP * activity.resources.displayMetrics.density
        attachCursorToHost(activity.window.decorView as ViewGroup)
        contentRoot.doOnLayout { placeCursorInitially() }
    }

    fun onDialogVisibilityChanged() {
        contentRoot.post { syncCursorHost(repositionOnDialog = true) }
    }

    fun detach() {
        stopRepeating()
        restoreDialogKeyInterception()
        edgeScrollHelper?.detach()
        edgeScrollHelper = null
        (cursorView.parent as? ViewGroup)?.removeView(cursorView)
        cursorHost = null
    }

    fun bindWebViewForEdgeScroll(webView: WebView?) {
        edgeScrollHelper?.detach()
        edgeScrollHelper = webView?.let { WebViewPointerHelper(it).also { helper -> helper.attach() } }
    }

    fun setWebViewPageLoading(loading: Boolean) {
        pageLoading = loading
        edgeScrollHelper?.setPageLoading(loading)
        if (loading) edgeScrollHelper?.clearVirtualCursor()
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        syncCursorHost(repositionOnDialog = false)
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val direction = when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> DIR_UP
                    KeyEvent.KEYCODE_DPAD_DOWN -> DIR_DOWN
                    KeyEvent.KEYCODE_DPAD_LEFT -> DIR_LEFT
                    KeyEvent.KEYCODE_DPAD_RIGHT -> DIR_RIGHT
                    else -> 0
                }
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (event.repeatCount == 0) {
                            moveCursor(direction)
                            startRepeating(direction)
                        }
                        return true
                    }

                    KeyEvent.ACTION_UP -> {
                        if (repeatDirection == direction) stopRepeating()
                        return true
                    }
                }
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event.action == KeyEvent.ACTION_UP) {
                    contentRoot.post { performClick() }
                }
                return true
            }
        }
        return false
    }

    private fun placeCursorInitially() {
        val rootLoc = IntArray(2)
        contentRoot.getLocationOnScreen(rootLoc)
        cursorScreenX = rootLoc[0] + contentRoot.width / 2f
        cursorScreenY = rootLoc[1] + contentRoot.height / 2f
        updateCursorViewPosition()
    }

    private fun startRepeating(direction: Int) {
        repeatDirection = direction
        if (repeating) return
        repeating = true
        handler.postDelayed(repeatRunnable, KEY_REPEAT_INITIAL_MS)
    }

    private fun stopRepeating() {
        handler.removeCallbacks(repeatRunnable)
        repeating = false
        repeatDirection = 0
    }

    private fun moveCursor(direction: Int): Boolean {
        val bounds = activeBoundsOnScreen()
        val previousX = cursorScreenX
        val previousY = cursorScreenY

        when (direction) {
            DIR_UP -> cursorScreenY -= moveStepPx
            DIR_DOWN -> cursorScreenY += moveStepPx
            DIR_LEFT -> cursorScreenX -= moveStepPx
            DIR_RIGHT -> cursorScreenX += moveStepPx
        }

        cursorScreenX = cursorScreenX.coerceIn(bounds.left, bounds.right)
        cursorScreenY = cursorScreenY.coerceIn(bounds.top, bounds.bottom)

        updateCursorViewPosition()
        if (!pageLoading) updateEdgeScroll()

        return cursorScreenX != previousX || cursorScreenY != previousY
    }

    private fun updateCursorViewPosition() {
        val host = cursorHost ?: return
        val hostLoc = IntArray(2)
        host.getLocationOnScreen(hostLoc)
        cursorView.x = cursorScreenX - hostLoc[0] - CURSOR_HOTSPOT_X
        cursorView.y = cursorScreenY - hostLoc[1] - CURSOR_HOTSPOT_Y
        cursorView.bringToFront()
    }

    private fun syncCursorHost(repositionOnDialog: Boolean) {
        val topDialogDecor = findTopDialogDecorView()
        val targetHost = topDialogDecor ?: (activity.window.decorView as ViewGroup)
        val hostChanged = cursorHost !== targetHost
        if (hostChanged) {
            attachCursorToHost(targetHost)
            if (topDialogDecor != null) {
                topDialogContentPanel(topDialogDecor)?.let { centerCursorOnView(it) }
            }
        } else if (repositionOnDialog && topDialogDecor != null) {
            topDialogContentPanel(topDialogDecor)?.let { centerCursorOnView(it) }
        }
        updateDialogKeyInterception()
        updateCursorViewPosition()
    }

    private fun updateDialogKeyInterception() {
        val dialog = findTopShowingDialog()
        if (dialog === keyInterceptedDialog) return

        restoreDialogKeyInterception()
        if (dialog == null) return

        val window = dialog.window ?: return
        val original = window.callback
        originalWindowCallback = original
        keyInterceptedDialog = dialog
        window.callback = object : Window.Callback by original {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (handleKeyEvent(event)) return true
                return original.dispatchKeyEvent(event)
            }
        }
    }

    private fun restoreDialogKeyInterception() {
        val dialog = keyInterceptedDialog ?: return
        val original = originalWindowCallback ?: return
        dialog.window?.callback = original
        keyInterceptedDialog = null
        originalWindowCallback = null
    }

    private fun findTopShowingDialog(): Dialog? =
        findTopShowingDialogFragment()?.dialog?.takeIf { it.isShowing }

    private fun findTopShowingDialogFragment(): DialogFragment? {
        val fragments = mutableListOf<DialogFragment>()
        collectDialogFragments(activity.supportFragmentManager, fragments)
        return fragments.lastOrNull()
    }

    private fun collectDialogFragments(
        fragmentManager: FragmentManager,
        out: MutableList<DialogFragment>
    ) {
        fragmentManager.fragments.forEach { fragment ->
            if (fragment is DialogFragment && fragment.dialog?.isShowing == true) {
                out.add(fragment)
            }
            collectDialogFragments(fragment.childFragmentManager, out)
        }
    }

    private fun attachCursorToHost(host: ViewGroup) {
        if (cursorView.parent !== host) {
            (cursorView.parent as? ViewGroup)?.removeView(cursorView)
            val cursorSizePx = (CURSOR_SIZE_DP * activity.resources.displayMetrics.density).roundToInt()
            host.addView(
                cursorView,
                FrameLayout.LayoutParams(cursorSizePx, cursorSizePx)
            )
        }
        cursorHost = host
        cursorView.elevation = CURSOR_ELEVATION
        cursorView.bringToFront()
    }

    private fun centerCursorOnView(view: View) {
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        cursorScreenX = loc[0] + view.width / 2f
        cursorScreenY = loc[1] + view.height / 2f
    }

    private fun topDialogContentPanel(dialogDecor: ViewGroup): View? {
        val content = dialogDecor.findViewById<ViewGroup>(android.R.id.content)
        if (content != null && content.width > 0 && content.height > 0) {
            for (i in 0 until content.childCount) {
                val child = content.getChildAt(i)
                if (child !== cursorView && child.visibility == View.VISIBLE &&
                    child.width > 0 && child.height > 0
                ) {
                    return child
                }
            }
            return content
        }
        for (i in 0 until dialogDecor.childCount) {
            val child = dialogDecor.getChildAt(i)
            if (child === cursorView) continue
            if (child.visibility == View.VISIBLE && child.width > 0 && child.height > 0) {
                return child
            }
        }
        return null
    }

    private fun findTopDialogDecorView(): ViewGroup? {
        val dialogs = mutableListOf<ViewGroup>()
        collectDialogRoots(activity, dialogs)
        return dialogs.lastOrNull()
    }

    private fun updateEdgeScroll() {
        if (pageLoading) return
        val helper = edgeScrollHelper ?: return
        val target = helper.webView
        val loc = IntArray(2)
        target.getLocationOnScreen(loc)
        val localX = cursorScreenX - loc[0]
        val localY = cursorScreenY - loc[1]
        if (localX < 0f || localY < 0f ||
            localX > target.width || localY > target.height
        ) {
            helper.clearVirtualCursor()
        } else {
            helper.updateVirtualCursor(localX, localY)
        }
    }

    private fun performClick() {
        for (root in clickSearchRoots()) {
            val deepest = findDeepestViewAt(root, cursorScreenX, cursorScreenY) ?: continue
            if (!isPointInside(deepest, cursorScreenX, cursorScreenY)) continue
            val target = resolveClickTarget(deepest) ?: continue
            dispatchClick(target, cursorScreenX, cursorScreenY)
            return
        }
    }

    private fun clickSearchRoots(): List<ViewGroup> {
        val dialogs = mutableListOf<ViewGroup>()
        collectDialogRoots(activity, dialogs)
        return dialogs.reversed() + contentRoot
    }

    private fun collectDialogRoots(activity: FragmentActivity, roots: MutableList<ViewGroup>) {
        collectDialogRoots(activity.supportFragmentManager, roots)
    }

    private fun collectDialogRoots(fragmentManager: FragmentManager, roots: MutableList<ViewGroup>) {
        fragmentManager.fragments.forEach { fragment ->
            if (fragment is DialogFragment) {
                fragment.dialog?.takeIf { it.isShowing }?.window?.decorView?.let { decor ->
                    if (decor is ViewGroup) roots.add(decor)
                }
            }
            collectDialogRoots(fragment.childFragmentManager, roots)
        }
    }

    private fun resolveClickTarget(view: View): View? {
        var current: View? = view
        var fallback: View? = null
        while (current != null) {
            if (current is TextInputLayout) {
                current.editText?.let { return it }
            }
            if (current is EditText) return current
            if (current is WebView) return current
            if (current.isClickable && current.isShown && current.isEnabled) {
                fallback = current
            }
            current = current.parent as? View
        }
        return fallback ?: view
    }

    private fun activeBoundsOnScreen(): Bounds {
        findTopDialogDecorView()?.let { decor ->
            topDialogContentPanel(decor)?.let { return boundsOfView(it) }
            return boundsOfView(decor)
        }
        return contentBoundsOnScreen()
    }

    private fun contentBoundsOnScreen(): Bounds = boundsOfView(contentRoot)

    private fun boundsOfView(view: View): Bounds {
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        return Bounds(
            left = loc[0].toFloat(),
            top = loc[1].toFloat(),
            right = loc[0] + view.width.toFloat(),
            bottom = loc[1] + view.height.toFloat()
        )
    }

    private fun findDeepestViewAt(root: View, screenX: Float, screenY: Float): View? {
        if (root === cursorView || !root.isShown || root.visibility != View.VISIBLE) return null
        if (!isPointInside(root, screenX, screenY)) return null

        // WebView embeds a huge Chromium view tree — never walk inside it.
        if (root is WebView) return root

        if (root is ViewGroup) {
            for (i in root.childCount - 1 downTo 0) {
                val child = root.getChildAt(i)
                val found = findDeepestViewAt(child, screenX, screenY)
                if (found != null) return found
            }
        }
        return root
    }

    private fun isPointInside(view: View, screenX: Float, screenY: Float): Boolean {
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        return screenX >= loc[0] && screenX < loc[0] + view.width &&
            screenY >= loc[1] && screenY < loc[1] + view.height
    }

    private fun dispatchClick(target: View, screenX: Float, screenY: Float) {
        when (target) {
            is EditText -> activateTextField(target, screenX, screenY)
            is WebView -> dispatchWebViewClick(target, screenX, screenY)
            else -> dispatchViewClick(target, screenX, screenY)
        }
    }

    private fun dispatchViewClick(target: View, screenX: Float, screenY: Float) {
        val loc = IntArray(2)
        target.getLocationOnScreen(loc)
        val localX = screenX - loc[0]
        val localY = screenY - loc[1]
        val downTime = SystemClock.uptimeMillis()
        val down = createTouchEvent(downTime, downTime, MotionEvent.ACTION_DOWN, localX, localY)
        val up = createTouchEvent(
            downTime,
            downTime + CLICK_DURATION_MS,
            MotionEvent.ACTION_UP,
            localX,
            localY
        )
        val downHandled = target.dispatchTouchEvent(down)
        val upHandled = target.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()

        if (!downHandled && !upHandled && target.isClickable && target.isEnabled) {
            target.performClick()
        }
    }

    private fun dispatchWebViewClick(webView: WebView, screenX: Float, screenY: Float) {
        val loc = IntArray(2)
        webView.getLocationOnScreen(loc)
        val localX = screenX - loc[0]
        val localY = screenY - loc[1]
        webView.isFocusableInTouchMode = true
        dispatchTouchToView(webView, localX, localY)
        focusWebViewTextInput(webView, localX, localY)
    }

    private fun focusWebViewTextInput(webView: WebView, localX: Float, localY: Float) {
        val (cssX, cssY) = webViewCssPoint(webView, localX, localY)
        val script = """
            (function() {
                var el = document.elementFromPoint($cssX, $cssY);
                if (!el) return false;
                if (el.closest) {
                    var field = el.closest('input, textarea, select, [contenteditable=""], [contenteditable="true"]');
                    if (field) { field.focus(); return true; }
                }
                if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.tagName === 'SELECT') {
                    el.focus();
                    return true;
                }
                if (el.isContentEditable) {
                    el.focus();
                    return true;
                }
                var active = document.activeElement;
                if (!active) return false;
                if (active.tagName === 'INPUT' || active.tagName === 'TEXTAREA' ||
                    active.tagName === 'SELECT' || active.isContentEditable) {
                    active.focus();
                    return true;
                }
                return false;
            })();
        """.trimIndent()

        webView.postDelayed({
            webView.evaluateJavascript(script) { result ->
                if (result != "true") return@evaluateJavascript
                webView.post { showKeyboardFor(webView) }
            }
        }, WEB_INPUT_FOCUS_DELAY_MS)
    }

    private fun webViewCssPoint(webView: WebView, localX: Float, localY: Float): Pair<Float, Float> {
        @Suppress("DEPRECATION")
        val scale = webView.scale.takeIf { it > 0f }
            ?: webView.resources.displayMetrics.density
        return localX / scale to localY / scale
    }

    private fun showKeyboardFor(view: View) {
        view.isFocusableInTouchMode = true
        if (!view.requestFocusFromTouch()) {
            val restoreFocusable = view.isFocusable
            view.isFocusable = true
            view.requestFocus()
            view.isFocusable = restoreFocusable
        }
        val imm = activity.getSystemService(InputMethodManager::class.java) ?: return
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun activateTextField(editText: EditText, screenX: Float, screenY: Float) {
        (editText.parent as? TextInputLayout)?.let { layout ->
            layout.isEnabled = true
            layout.requestFocus()
        }

        editText.isFocusableInTouchMode = true
        val loc = IntArray(2)
        editText.getLocationOnScreen(loc)
        val localX = (screenX - loc[0]).coerceIn(0f, editText.width.toFloat().coerceAtLeast(1f))
        val localY = (screenY - loc[1]).coerceIn(0f, editText.height.toFloat().coerceAtLeast(1f))
        dispatchTouchToView(editText, localX, localY)

        if (!editText.hasFocus() && !editText.requestFocusFromTouch()) {
            val restoreFocusable = editText.isFocusable
            editText.isFocusable = true
            editText.requestFocus()
            editText.isFocusable = restoreFocusable
        }

        val textLength = editText.text?.length ?: 0
        if (textLength > 0) {
            editText.setSelection(textLength)
        }

        editText.post {
            val imm = activity.getSystemService(InputMethodManager::class.java)
            imm?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun dispatchTouchToView(target: View, localX: Float, localY: Float) {
        val downTime = SystemClock.uptimeMillis()
        val down = createTouchEvent(downTime, downTime, MotionEvent.ACTION_DOWN, localX, localY)
        val up = createTouchEvent(
            downTime,
            downTime + CLICK_DURATION_MS,
            MotionEvent.ACTION_UP,
            localX,
            localY
        )
        target.dispatchTouchEvent(down)
        target.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    private fun createTouchEvent(
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float
    ): MotionEvent {
        val properties = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        })
        val coords = arrayOf(MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
        })
        return MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            1,
            properties,
            coords,
            0,
            0,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0
        )
    }

    private data class Bounds(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    companion object {
        private const val MOVE_STEP_DP = 14f
        private const val KEY_REPEAT_INITIAL_MS = 350L
        private const val KEY_REPEAT_INTERVAL_MS = 40L
        private const val CLICK_DURATION_MS = 100L
        private const val WEB_INPUT_FOCUS_DELAY_MS = 80L
        private const val CURSOR_SIZE_DP = 28f
        private const val CURSOR_HOTSPOT_X = 6f
        private const val CURSOR_HOTSPOT_Y = 6f
        private const val CURSOR_ELEVATION = 10_000f

        private const val DIR_UP = 1
        private const val DIR_DOWN = 2
        private const val DIR_LEFT = 4
        private const val DIR_RIGHT = 8
    }
}
