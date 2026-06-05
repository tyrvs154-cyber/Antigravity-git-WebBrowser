package com.antigravity.webbrowser.webview

import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * カスタムWebViewClient。
 * ページナビゲーションの制御、URL更新コールバック、SSL状態管理を行う。
 */
class CustomWebViewClient(
    private val onPageStarted: (String) -> Unit = {},
    private val onPageFinished: (String) -> Unit = {},
    private val onUrlChanged: (String) -> Unit = {},
    private val onError: (String) -> Unit = {},
    private val onSslStatusChanged: (Boolean) -> Unit = {}
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        // アプリ内でナビゲーションを処理する（外部ブラウザに遷移しない）
        onUrlChanged(url)
        return false
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        url?.let {
            onPageStarted(it)
            onUrlChanged(it)
            onSslStatusChanged(it.startsWith("https://"))
        }
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        url?.let { onPageFinished(it) }
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError
    ) {
        super.onReceivedError(view, request, error)
        if (request.isForMainFrame) {
            onError("エラー: ${error.description} (${error.errorCode})")
        }
    }
}
