package com.antigravity.webbrowser

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.antigravity.webbrowser.gesture.GestureMode
import com.antigravity.webbrowser.gesture.GestureSimulator
import com.antigravity.webbrowser.settings.BrowserSettings
import com.antigravity.webbrowser.webview.CustomWebChromeClient
import com.antigravity.webbrowser.webview.CustomWebViewClient

/**
 * メインActivity。
 * WebView、URLバー、ジェスチャー操作バーを統合し、
 * タップ操作のみでWebページを操作できるブラウザ画面を提供する。
 *
 * 透明オーバーレイでタッチイベントをインターセプトし、
 * 選択中のジェスチャーモードに応じてGestureSimulatorへ座標を渡す。
 */
class MainActivity : AppCompatActivity() {

    // Views
    private lateinit var webView: WebView
    private lateinit var urlEditText: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var modeLabel: TextView
    private lateinit var touchOverlay: View
    private lateinit var gestureButtonsContainer: LinearLayout
    private lateinit var btnSsl: ImageButton

    // Components
    private lateinit var settings: BrowserSettings
    private lateinit var gestureSimulator: GestureSimulator

    // State
    private var currentMode: GestureMode = GestureMode.TAP
    private val gestureButtons = mutableMapOf<GestureMode, Button>()

    companion object {
        private const val SETTINGS_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = BrowserSettings(this)
        gestureSimulator = GestureSimulator()

        initViews()
        initWebView()
        initGestureToolbar()
        initTouchOverlay()

        // Load home page
        webView.loadUrl(settings.homeUrl)
    }

    /**
     * ViewのバインドとURLバーのイベントハンドリングを初期化する。
     */
    private fun initViews() {
        webView = findViewById(R.id.webView)
        urlEditText = findViewById(R.id.urlEditText)
        progressBar = findViewById(R.id.progressBar)
        modeLabel = findViewById(R.id.modeLabel)
        touchOverlay = findViewById(R.id.touchOverlay)
        gestureButtonsContainer = findViewById(R.id.gestureButtonsContainer)
        btnSsl = findViewById(R.id.btnSsl)

        // Go button
        findViewById<ImageButton>(R.id.btnGo).setOnClickListener {
            navigateToUrl()
        }

        // Refresh button
        findViewById<ImageButton>(R.id.btnRefresh).setOnClickListener {
            webView.reload()
        }

        // Settings button
        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            @Suppress("DEPRECATION")
            startActivityForResult(intent, SETTINGS_REQUEST_CODE)
        }

        // URL bar - Enter key handling
        urlEditText.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                navigateToUrl()
                true
            } else {
                false
            }
        }
    }

    /**
     * WebViewの各種設定を初期化する。
     * JavaScript、DOMストレージ、ズームを有効化し、
     * PC版モードに応じてUser-Agentとビューポートを設定する。
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = false
            allowContentAccess = false

            // PC版モード設定
            if (settings.isDesktopMode) {
                userAgentString = BrowserSettings.DESKTOP_USER_AGENT
                useWideViewPort = true
                loadWithOverviewMode = true
            }
        }

        // WebViewClient: ページナビゲーション管理
        webView.webViewClient = CustomWebViewClient(
            onPageStarted = { url ->
                runOnUiThread {
                    progressBar.visibility = View.VISIBLE
                }
            },
            onPageFinished = { url ->
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    progressBar.progress = 0
                }
            },
            onUrlChanged = { url ->
                runOnUiThread {
                    urlEditText.setText(url)
                    urlEditText.setSelection(url.length)
                }
            },
            onError = { errorMsg ->
                runOnUiThread {
                    Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show()
                }
            },
            onSslStatusChanged = { isSecure ->
                runOnUiThread {
                    updateSslIndicator(isSecure)
                }
            }
        )

        // WebChromeClient: 進捗とタイトル管理
        webView.webChromeClient = CustomWebChromeClient(
            onProgressChanged = { progress ->
                runOnUiThread {
                    progressBar.progress = progress
                    if (progress >= 100) {
                        progressBar.visibility = View.GONE
                    } else {
                        progressBar.visibility = View.VISIBLE
                    }
                }
            },
            onTitleChanged = { title ->
                // タイトルが必要な場合にここで処理
            }
        )

        // デバッグ用（開発時のみ）
        WebView.setWebContentsDebuggingEnabled(true)
    }

    /**
     * ジェスチャー操作バーを動的に生成する。
     * 各GestureModeに対応するボタンをLinearLayoutに追加し、
     * タップでモードが切り替わるようにする。
     */
    private fun initGestureToolbar() {
        GestureMode.values().forEach { mode ->
            val button = Button(this).apply {
                text = mode.buttonLabel
                textSize = 12f
                setTextColor(resources.getColor(R.color.text_primary, theme))
                gravity = Gravity.CENTER
                minWidth = 0
                minimumWidth = 0
                setPadding(16, 8, 16, 8)

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 6
                    marginStart = if (mode == GestureMode.TAP) 0 else 0
                }
                layoutParams = params

                setBackgroundResource(R.drawable.gesture_button_background)

                setOnClickListener {
                    setGestureMode(mode)
                }
            }

            gestureButtons[mode] = button
            gestureButtonsContainer.addView(button)
        }

        // デフォルトモード設定
        setGestureMode(GestureMode.TAP)
    }

    /**
     * 透明オーバーレイのタッチイベントハンドリングを設定する。
     * タッチ座標をWebView内の相対座標に変換し、
     * 現在選択中のジェスチャーモードでGestureSimulatorに渡す。
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun initTouchOverlay() {
        touchOverlay.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                // タッチ座標をWebViewの座標空間に変換
                val overlayLocation = IntArray(2)
                val webViewLocation = IntArray(2)
                touchOverlay.getLocationOnScreen(overlayLocation)
                webView.getLocationOnScreen(webViewLocation)

                val webViewX = event.rawX - webViewLocation[0]
                val webViewY = event.rawY - webViewLocation[1]

                // 座標がWebView内にあることを確認
                if (webViewX >= 0 && webViewX <= webView.width &&
                    webViewY >= 0 && webViewY <= webView.height
                ) {
                    gestureSimulator.simulate(
                        webView, currentMode, webViewX, webViewY, settings.swipeDistance
                    )
                }
                true
            } else {
                true
            }
        }
    }

    /**
     * ジェスチャーモードを変更し、ボタンのハイライト状態とラベルを更新する。
     */
    private fun setGestureMode(mode: GestureMode) {
        currentMode = mode

        // 全ボタンの選択状態をリセット
        gestureButtons.forEach { (m, btn) ->
            btn.isSelected = (m == mode)
        }

        // モードラベルを更新
        modeLabel.text = "モード: ${mode.displayName}"
    }

    /**
     * URLバーに入力されたURLへナビゲーションする。
     * http(s)プロトコルが未指定の場合は自動補完する。
     */
    private fun navigateToUrl() {
        var url = urlEditText.text.toString().trim()
        if (url.isEmpty()) return

        // プロトコル未指定の場合は補完
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            // URLっぽい文字列（ドットを含む）ならhttps://を付与、そうでなければ検索
            url = if (url.contains(".") && !url.contains(" ")) {
                "https://$url"
            } else {
                "https://www.google.com/search?q=${java.net.URLEncoder.encode(url, "UTF-8")}"
            }
        }

        webView.loadUrl(url)
        hideKeyboard()
    }

    /**
     * SSL状態インジケーターの表示を更新する。
     */
    private fun updateSslIndicator(isSecure: Boolean) {
        if (isSecure) {
            btnSsl.setColorFilter(resources.getColor(R.color.ssl_secure, theme))
        } else {
            btnSsl.setColorFilter(resources.getColor(R.color.ssl_insecure, theme))
        }
    }

    /**
     * ソフトキーボードを非表示にする。
     */
    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(urlEditText.windowToken, 0)
        urlEditText.clearFocus()
    }

    /**
     * 戻るボタンの処理。WebViewで戻れる場合は戻り、そうでなければアプリを終了する。
     */
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    /**
     * 設定画面から戻った際に設定を再適用する。
     */
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SETTINGS_REQUEST_CODE) {
            applySettings()
        }
    }

    /**
     * 設定画面で変更された内容をWebViewに反映する。
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun applySettings() {
        val wasDesktop = webView.settings.userAgentString == BrowserSettings.DESKTOP_USER_AGENT
        val isDesktop = settings.isDesktopMode

        webView.settings.apply {
            if (isDesktop) {
                userAgentString = BrowserSettings.DESKTOP_USER_AGENT
                useWideViewPort = true
                loadWithOverviewMode = true
            } else {
                userAgentString = null // デフォルトに戻す
                useWideViewPort = false
                loadWithOverviewMode = false
            }
        }

        // モードが変わった場合はリロード
        if (wasDesktop != isDesktop) {
            webView.reload()
            val message = if (isDesktop) {
                getString(R.string.desktop_mode_enabled)
            } else {
                getString(R.string.desktop_mode_disabled)
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
