package com.antigravity.webbrowser.webview

import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.app.AlertDialog
import android.widget.EditText

/**
 * カスタムWebChromeClient。
 * ページ読み込みの進捗管理、タイトル更新、JavaScriptダイアログ対応を行う。
 */
class CustomWebChromeClient(
    private val onProgressChanged: (Int) -> Unit = {},
    private val onTitleChanged: (String) -> Unit = {}
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChanged(newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        super.onReceivedTitle(view, title)
        title?.let { onTitleChanged(it) }
    }

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
