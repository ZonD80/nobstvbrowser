package com.aengix.tvbrowser

import android.view.InputDevice
import android.view.MotionEvent
import android.view.View

object PointerScrollHelper {
    private const val SCROLL_FACTOR = 120f

    fun attach(view: View) {
        view.setOnGenericMotionListener { target, event ->
            if (event.action != MotionEvent.ACTION_SCROLL) return@setOnGenericMotionListener false
            if (!event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) return@setOnGenericMotionListener false

            val vertical = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
            val horizontal = event.getAxisValue(MotionEvent.AXIS_HSCROLL)
            var handled = false

            if (vertical != 0f) {
                target.scrollBy(0, (-vertical * SCROLL_FACTOR).toInt())
                handled = true
            }
            if (horizontal != 0f) {
                target.scrollBy((-horizontal * SCROLL_FACTOR).toInt(), 0)
                handled = true
            }
            handled
        }
    }
}
