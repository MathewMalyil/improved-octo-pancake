package com.mmalyil.smartdocai


import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class TermsOfServiceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_policy)

        title = "Terms of Service"
        val webView = findViewById<WebView>(R.id.policyWebView)

        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.loadUrl("https://docs.google.com/document/d/1AeuZk0xLW6qHw9VABC4Ei37iL0pCsRO4WxXe-P-3RWw/edit?usp=sharing")
    }
}