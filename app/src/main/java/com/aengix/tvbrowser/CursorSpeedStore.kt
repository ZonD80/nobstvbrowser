package com.aengix.tvbrowser

import android.content.Context

class CursorSpeedStore(context: Context) {
    enum class Speed(val multiplier: Float) {
        HALF(0.5f),
        NORMAL(1f),
        DOUBLE(2f);

        companion object {
            fun fromName(name: String?): Speed =
                entries.firstOrNull { it.name == name } ?: NORMAL
        }
    }

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(): Speed = Speed.fromName(prefs.getString(KEY_SPEED, Speed.NORMAL.name))

    fun set(speed: Speed) {
        prefs.edit().putString(KEY_SPEED, speed.name).apply()
    }

    companion object {
        private const val PREFS_NAME = "aengix_tv_browser"
        private const val KEY_SPEED = "cursor_speed"
    }
}
