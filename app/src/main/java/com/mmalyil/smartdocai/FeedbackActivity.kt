package com.mmalyil.smartdocai


import android.os.Bundle
import android.webkit.WebView

import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

import android.webkit.WebSettings
// FeedbackActivity.kt
import com.mmalyil.smartdocai.R
// This activity displays a Google Form for user feedback in a secure WebView
// It ensures that the WebView is configured to prevent security issues


class FeedbackActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feedback)

        val webView = findViewById<WebView>(R.id.feedbackWebView)

        // Enable safe WebViewClient
        webView.webViewClient = WebViewClient()

        // Configure secure WebView settings
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true  // Required by Google Forms
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.domStorageEnabled = true  // Optional but helps with form loading

        // Load your Google Form
        webView.loadUrl("https://forms.gle/rGH1izKWpASZVc7TA")
        supportActionBar?.title = "Feedback"

    }
}
