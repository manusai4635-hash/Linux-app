package com.linuxify

import android.os.Bundle
import android.webkit.WebView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ContainerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_container)
        val session = LinuxifyRuntime.lastSession
        val profile = session?.profile ?: DeviceDetector.detect(this)
        val terminal = findViewById<TextView>(R.id.terminalText)
        val webView = findViewById<WebView>(R.id.desktopWebView)

        if (profile.recommendedMode == LinuxMode.TERMINAL || session?.vncUrl == null) {
            terminal.visibility = android.view.View.VISIBLE
            terminal.text = buildString {
                appendLine("Linuxify terminal mode")
                appendLine(profile.summary())
                appendLine(session?.message ?: "Container session is not ready yet.")
                appendLine()
                appendLine("Production runtime requirement:")
                appendLine("- proot or droidspaces binary for ${profile.supportedAbis.joinToString()}")
                appendLine("- ${profile.recommendedMode.name.lowercase()} rootfs image")
            }
        } else {
            webView.settings.javaScriptEnabled = true
            webView.settings.domStorageEnabled = false
            webView.settings.databaseEnabled = false
            webView.loadUrl(session.vncUrl)
        }
    }
}
