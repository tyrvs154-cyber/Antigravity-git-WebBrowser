package com.antigravity.webbrowser.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferencesベースのブラウザ設定管理クラス。
 * PC版モード、スワイプ距離、ホームURLなどの設定を永続化する。
 */
class BrowserSettings(context: Context) {

    companion object {
        private const val PREFS_NAME = "vr_browser_settings"
        private const val KEY_DESKTOP_MODE = "desktop_mode"
        private const val KEY_HOME_URL = "home_url"
        private const val KEY_SWIPE_DISTANCE = "swipe_distance"

        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        const val DEFAULT_HOME_URL = "https://www.google.com"
        const val DEFAULT_SWIPE_DISTANCE = 300
        const val MIN_SWIPE_DISTANCE = 100
        const val MAX_SWIPE_DISTANCE = 800
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** PC版（デスクトップ）モードが有効かどうか */
    var isDesktopMode: Boolean
        get() = prefs.getBoolean(KEY_DESKTOP_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DESKTOP_MODE, value).apply()

    /** ホームページURL */
    var homeUrl: String
        get() = prefs.getString(KEY_HOME_URL, DEFAULT_HOME_URL) ?: DEFAULT_HOME_URL
        set(value) = prefs.edit().putString(KEY_HOME_URL, value).apply()

    /** スワイプジェスチャーの移動距離（ピクセル） */
    var swipeDistance: Int
        get() = prefs.getInt(KEY_SWIPE_DISTANCE, DEFAULT_SWIPE_DISTANCE)
        set(value) {
            val clamped = value.coerceIn(MIN_SWIPE_DISTANCE, MAX_SWIPE_DISTANCE)
            prefs.edit().putInt(KEY_SWIPE_DISTANCE, clamped).apply()
        }
}
