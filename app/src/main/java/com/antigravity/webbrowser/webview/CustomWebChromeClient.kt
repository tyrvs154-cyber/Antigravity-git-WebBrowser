package com.antigravity.webbrowser.webview

import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout

/**
 * カスタムWebChromeClient。
 * ページ読み込みの進捗管理、タイトル更新、JavaScriptダイアログ対応、
 * フルスクリーン動画再生、DRM保護コンテンツの権限管理を行う。
 */
class CustomWebChromeClient(
    private val onProgressChanged: (Int) -> Unit = {},
    private val onTitleChanged: (String) -> Unit = {},
    private val onFullscreenChanged: ((View?, WebChromeClient.CustomViewCallback?) -> Unit)? = null,
    private val onExitFullscreen: (() -> Unit)? = null
) : WebChromeClient() {

    /** フルスクリーン表示中のコールバック（非表示にする際に使用） */
    private var fullscreenCallback: CustomViewCallback? = null

    /** フルスクリーン表示中のView */
    private var fullscreenView: View? = null

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChanged(newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        super.onReceivedTitle(view, title)
        title?.let { onTitleChanged(it) }
    }

    /**
     * DRM保護コンテンツ（YouTube等）の権限リクエストを処理する。
     * RESOURCE_PROTECTED_MEDIA_ID（Widevine DRM）と
     * RESOURCE_VIDEO_CAPTURE / RESOURCE_AUDIO_CAPTURE を許可する。
     */
    override fun onPermissionRequest(request: PermissionRequest?) {
        request?.let {
            // メインスレッドで権限を付与
            it.origin
            val resources = it.resources
            it.grant(resources)
        }
    }

    /**
     * フルスクリーン動画表示のリクエストを処理する。
     * YouTube等で動画をフルスクリーン再生する際に呼ばれる。
     * onFullscreenChangedコールバックを通じてActivityにViewの管理を委任する。
     */
    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        fullscreenView = view
        fullscreenCallback = callback
        onFullscreenChanged?.invoke(view, callback)
    }

    /**
     * フルスクリーン表示を終了する。
     */
    override fun onHideCustomView() {
        fullscreenView = null
        fullscreenCallback = null
        onExitFullscreen?.invoke()
    }

    /**
     * フルスクリーン表示中かどうかを返す。
     */
    fun isInFullscreen(): Boolean = fullscreenView != null

    /**
     * フルスクリーンを終了する（外部から呼び出し用）。
     */
    fun exitFullscreen() {
        fullscreenCallback?.onCustomViewHidden()
        fullscreenView = null
        fullscreenCallback = null
    }

    /**
     * デフォルトの動画ポスター画像を返す。
     * 動画のサムネイルが無い場合に表示される1x1の透明画像。
     */
    override fun getDefaultVideoPoster(): Bitmap? {
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.TRANSPARENT)
        }
    }

    // === JavaScript Dialogs ===

    override fun onJsAlert(view: WebView, url: String?, message: String?, result: JsResult?): Boolean {
        val context = view.context ?: return false
        AlertDialog.Builder(context)
            .setTitle("ページからのメッセージ")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> result?.confirm() }
            .setCancelable(false)
            .show()
        return true
    }

    override fun onJsConfirm(view: WebView, url: String?, message: String?, result: JsResult?): Boolean {
        val context = view.context ?: return false
        AlertDialog.Builder(context)
            .setTitle("確認")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> result?.confirm() }
            .setNegativeButton("キャンセル") { _, _ -> result?.cancel() }
            .setCancelable(false)
            .show()
        return true
    }

    override fun onJsPrompt(
        view: WebView, url: String?, message: String?,
        defaultValue: String?, result: JsPromptResult?
    ): Boolean {
        val context = view.context ?: return false
        val input = EditText(context).apply {
            setText(defaultValue)
        }
        AlertDialog.Builder(context)
            .setTitle("入力")
            .setMessage(message)
            .setView(input)
            .setPositiveButton("OK") { _, _ -> result?.confirm(input.text.toString()) }
            .setNegativeButton("キャンセル") { _, _ -> result?.cancel() }
            .setCancelable(false)
            .show()
        return true
    }
}
