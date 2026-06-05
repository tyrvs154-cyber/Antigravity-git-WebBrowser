package com.antigravity.webbrowser.gesture

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.WebView

/**
 * ジェスチャーシミュレーター。
 * ハイブリッド方式でジェスチャーを再現する:
 * - タップ/長押し: MotionEvent dispatchで正確なクリック操作
 * - スワイプ: JavaScript window.scrollBy() + CSS scroll + タッチイベントのハイブリッド
 * - ピンチ: WebView組み込みzoom API
 *
 * VRブラウザ統合時には、VRコントローラーの入力をこのクラス経由でWebViewに伝達できる。
 */
class GestureSimulator {

    private val handler = Handler(Looper.getMainLooper())

    /** シミュレーション中フラグ（外部から参照可能） */
    @Volatile
    var isSimulating = false
        private set

    /** コールバック: ジェスチャー実行完了時 */
    var onGestureCompleted: (() -> Unit)? = null

    /**
     * 指定されたジェスチャーモードに対応するイベントシーケンスを
     * WebViewに対してシミュレーションする。
     *
     * @param webView 対象のWebView
     * @param mode シミュレーションするジェスチャーの種類
     * @param x タッチ座標X（WebView内の相対座標）
     * @param y タッチ座標Y（WebView内の相対座標）
     * @param swipeDistance スワイプの移動距離（ピクセル）
     */
    fun simulate(webView: WebView, mode: GestureMode, x: Float, y: Float, swipeDistance: Int = 300) {
        isSimulating = true
        when (mode) {
            GestureMode.TAP -> simulateTap(webView, x, y)
            GestureMode.SWIPE_UP -> simulateScrollJS(webView, 0, swipeDistance, x, y)
            GestureMode.SWIPE_DOWN -> simulateScrollJS(webView, 0, -swipeDistance, x, y)
            GestureMode.SWIPE_LEFT -> simulateScrollJS(webView, swipeDistance, 0, x, y)
            GestureMode.SWIPE_RIGHT -> simulateScrollJS(webView, -swipeDistance, 0, x, y)
            GestureMode.LONG_PRESS -> simulateLongPress(webView, x, y)
            GestureMode.PINCH_IN -> simulatePinch(webView, zoomIn = false)
            GestureMode.PINCH_OUT -> simulatePinch(webView, zoomIn = true)
        }
    }

    /**
     * タップシミュレーション: MotionEvent dispatchによる正確なクリック。
     * ACTION_DOWN → 短い待機 → ACTION_UP のシーケンスで
     * WebView内のリンクやボタンをクリックする。
     */
    private fun simulateTap(webView: WebView, x: Float, y: Float) {
        handler.post {
            val downTime = SystemClock.uptimeMillis()

            val downEvent = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0
            )
            webView.dispatchTouchEvent(downEvent)
            downEvent.recycle()

            handler.postDelayed({
                val upEvent = MotionEvent.obtain(
                    downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0
                )
                webView.dispatchTouchEvent(upEvent)
                upEvent.recycle()
                completeGesture()
            }, 60)
        }
    }

    /**
     * JavaScriptベースのスクロールシミュレーション。
     * Chromium WebViewはdispatchTouchEventのシンセティックイベントで
     * スクロールしないため、JSでの直接制御が必要。
     *
     * 3段階のフォールバック戦略:
     * 1. タッチ座標直下の要素のscrollBy（スクロール可能な子要素向け）
     * 2. window.scrollBy（ページ全体のスクロール）
     * 3. TouchEventのJSディスパッチ（SPA等のカスタムスクロール対応）
     *
     * @param webView 対象のWebView
     * @param scrollX 横方向のスクロール量（正: 右、負: 左）
     * @param scrollY 縦方向のスクロール量（正: 下、負: 上）
     * @param touchX タッチされた座標X（CSS px）
     * @param touchY タッチされた座標Y（CSS px）
     */
    private fun simulateScrollJS(webView: WebView, scrollX: Int, scrollY: Int, touchX: Float, touchY: Float) {
        handler.post {
            // density を考慮してCSSピクセルに変換
            val density = webView.resources.displayMetrics.density
            val cssTouchX = (touchX / density).toInt()
            val cssTouchY = (touchY / density).toInt()
            val cssScrollX = (scrollX / density).toInt()
            val cssScrollY = (scrollY / density).toInt()

            val js = """
            (function() {
                var x = $cssTouchX;
                var y = $cssTouchY;
                var scrollX = $cssScrollX;
                var scrollY = $cssScrollY;
                
                // Strategy 1: Find scrollable element at touch point and scroll it
                var el = document.elementFromPoint(x, y);
                var scrolled = false;
                
                if (el) {
                    var current = el;
                    while (current && current !== document.body && current !== document.documentElement) {
                        var style = window.getComputedStyle(current);
                        var overflowY = style.overflowY;
                        var overflowX = style.overflowX;
                        
                        var isScrollableY = (overflowY === 'auto' || overflowY === 'scroll') && 
                                           current.scrollHeight > current.clientHeight;
                        var isScrollableX = (overflowX === 'auto' || overflowX === 'scroll') && 
                                           current.scrollWidth > current.clientWidth;
                        
                        if ((isScrollableY && scrollY !== 0) || (isScrollableX && scrollX !== 0)) {
                            var beforeTop = current.scrollTop;
                            var beforeLeft = current.scrollLeft;
                            current.scrollBy({left: scrollX, top: scrollY, behavior: 'smooth'});
                            // Check if scroll actually happened (after a small delay)
                            setTimeout(function() {
                                if (current.scrollTop !== beforeTop || current.scrollLeft !== beforeLeft) {
                                    // Scrolled successfully in child element
                                }
                            }, 50);
                            scrolled = true;
                            break;
                        }
                        current = current.parentElement;
                    }
                }
                
                // Strategy 2: Scroll the page (window level)
                var beforeScrollY = window.pageYOffset || document.documentElement.scrollTop;
                var beforeScrollX = window.pageXOffset || document.documentElement.scrollLeft;
                window.scrollBy({left: scrollX, top: scrollY, behavior: 'smooth'});
                
                // Strategy 3: Also dispatch touch events for SPAs with custom scroll handling
                if (el) {
                    try {
                        var touch1 = new Touch({
                            identifier: Date.now(),
                            target: el,
                            clientX: x,
                            clientY: y,
                            screenX: x,
                            screenY: y
                        });
                        
                        el.dispatchEvent(new TouchEvent('touchstart', {
                            cancelable: true, bubbles: true,
                            touches: [touch1], targetTouches: [touch1], changedTouches: [touch1]
                        }));
                        
                        var touch2 = new Touch({
                            identifier: touch1.identifier,
                            target: el,
                            clientX: x - (scrollX > 0 ? 30 : scrollX < 0 ? -30 : 0),
                            clientY: y - (scrollY > 0 ? 30 : scrollY < 0 ? -30 : 0),
                            screenX: x - (scrollX > 0 ? 30 : scrollX < 0 ? -30 : 0),
                            screenY: y - (scrollY > 0 ? 30 : scrollY < 0 ? -30 : 0)
                        });
                        
                        el.dispatchEvent(new TouchEvent('touchmove', {
                            cancelable: true, bubbles: true,
                            touches: [touch2], targetTouches: [touch2], changedTouches: [touch2]
                        }));
                        
                        el.dispatchEvent(new TouchEvent('touchend', {
                            cancelable: true, bubbles: true,
                            touches: [], targetTouches: [], changedTouches: [touch2]
                        }));
                    } catch(e) {
                        // Touch constructor may not be available on some pages
                    }
                }
            })();
            """.trimIndent()

            webView.evaluateJavascript(js) {
                handler.postDelayed({
                    completeGesture()
                }, 100)
            }
        }
    }

    /**
     * 長押しシミュレーション: ACTION_DOWN → 600ms待機 → ACTION_UP
     * Android標準の長押し検出しきい値（500ms）を超える時間保持する。
     */
    private fun simulateLongPress(webView: WebView, x: Float, y: Float) {
        handler.post {
            val downTime = SystemClock.uptimeMillis()

            val downEvent = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0
            )
            webView.dispatchTouchEvent(downEvent)
            downEvent.recycle()

            // Hold for 600ms (longer than the 500ms long-press threshold)
            handler.postDelayed({
                val upEvent = MotionEvent.obtain(
                    downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0
                )
                webView.dispatchTouchEvent(upEvent)
                upEvent.recycle()
                completeGesture()
            }, 600)
        }
    }

    /**
     * ピンチシミュレーション: WebViewの組み込みzoomIn/zoomOutを使用。
     * MotionEventによるマルチタッチシミュレーションは複雑でChromium WebViewとの
     * 互換性が不安定なため、組み込みAPI使用を選択。
     */
    private fun simulatePinch(webView: WebView, zoomIn: Boolean) {
        handler.post {
            if (zoomIn) {
                webView.zoomIn()
            } else {
                webView.zoomOut()
            }
            completeGesture()
        }
    }

    private fun completeGesture() {
        isSimulating = false
        onGestureCompleted?.invoke()
    }
}
