package com.antigravity.webbrowser

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.antigravity.webbrowser.settings.BrowserSettings

/**
 * 設定画面Activity。
 * PC版モードの切り替え、スワイプ距離の調整、ホームURLの設定を行う。
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: BrowserSettings
    private lateinit var switchDesktopMode: Switch
    private lateinit var seekBarSwipeDistance: SeekBar
    private lateinit var tvSwipeDistanceValue: TextView
    private lateinit var etHomeUrl: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settings = BrowserSettings(this)

        initViews()
        loadSettings()
    }

    private fun initViews() {
        switchDesktopMode = findViewById(R.id.switchDesktopMode)
        seekBarSwipeDistance = findViewById(R.id.seekBarSwipeDistance)
        tvSwipeDistanceValue = findViewById(R.id.tvSwipeDistanceValue)
        etHomeUrl = findViewById(R.id.etHomeUrl)

        // スワイプ距離シークバーのリスナー
        seekBarSwipeDistance.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvSwipeDistanceValue.text = "${progress}px"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 保存ボタン
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveSettings()
        }
    }

    /**
     * 現在の設定値をUIに反映する。
     */
    private fun loadSettings() {
        switchDesktopMode.isChecked = settings.isDesktopMode
        seekBarSwipeDistance.progress = settings.swipeDistance
        tvSwipeDistanceValue.text = "${settings.swipeDistance}px"
        etHomeUrl.setText(settings.homeUrl)
    }

    /**
     * UIの設定値をSharedPreferencesに保存する。
     */
    private fun saveSettings() {
        settings.isDesktopMode = switchDesktopMode.isChecked
        settings.swipeDistance = seekBarSwipeDistance.progress
        
        val homeUrl = etHomeUrl.text.toString().trim()
        if (homeUrl.isNotEmpty()) {
            settings.homeUrl = if (homeUrl.startsWith("http://") || homeUrl.startsWith("https://")) {
                homeUrl
            } else {
                "https://$homeUrl"
            }
        }

        Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        setResult(RESULT_CANCELED)
        super.onBackPressed()
    }
}
