package com.antigravity.webbrowser.gesture

/**
 * ジェスチャーモードの列挙型。
 * 操作バーで選択し、WebView上をタップした際に発火するジェスチャーの種類を定義する。
 *
 * @param displayName 操作バーに表示するラベル
 * @param modeStringRes strings.xmlに対応するモード名のキー
 */
enum class GestureMode(val displayName: String, val buttonLabel: String) {
    TAP("タップ 👆", "👆\nタップ"),
    SWIPE_UP("上スワイプ ⬆", "⬆\n上"),
    SWIPE_DOWN("下スワイプ ⬇", "⬇\n下"),
    SWIPE_LEFT("左スワイプ ⬅", "⬅\n左"),
    SWIPE_RIGHT("右スワイプ ➡", "➡\n右"),
    LONG_PRESS("長押し 👇", "👇\n長押"),
    PINCH_IN("縮小 🔍", "🔍\n縮小"),
    PINCH_OUT("拡大 🔎", "🔎\n拡大");
}
