package com.mmalyil.smartdocai


import android.graphics.Color

import android.os.Bundle

import com.github.appintro.AppIntro
import com.github.appintro.AppIntroFragment
import androidx.core.content.ContextCompat
// Ensure you have the necessary imports for AppIntro and other components

// This is the IntroActivity for the SmartDocAI application

// IntroActivity.kt
// This activity serves as the introduction screen for the SmartDocAI application,
// showcasing the app's features and guiding users through the initial setup.

class IntroActivity : AppIntro() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isSkipButtonEnabled = true
        isWizardMode = true
        setIndicatorColor(Color.WHITE, Color.GRAY)

        addSlide(AppIntroFragment.newInstance(
            title = "Welcome to SmartDoc AI",
            description = "Scan, upload, and analyze documents with the power of AI.",
            imageDrawable = R.drawable.ic_doc_welcome, // Replace with your image
            backgroundColor = ContextCompat.getColor(this, R.color.teal_700)
        
        ))
        addSlide(AppIntroFragment.newInstance(
            title = "Scan or Upload Files",
            description = "Easily scan with your camera or upload PDFs, DOCX, or PPTX files.",
            imageDrawable = R.drawable.ic_scan_upload, // Replace with your image
            backgroundColor = ContextCompat.getColor(this, R.color.white)

        ))
        addSlide(AppIntroFragment.newInstance(
            title = "Smart AI Analysis",
            description = "Ask questions, summarize content, or extract insights with GPT & Mistral models.",
            imageDrawable = R.drawable.ic_ai_analysis, // Replace with your image
            backgroundColor = ContextCompat.getColor(this, R.color.surface)

        ))
        addSlide(AppIntroFragment.newInstance(
            title = "Export & Share",
            description = "Save or share AI-powered results in TXT or PDF formats instantly.",
            imageDrawable = R.drawable.ic_share_export, // Replace with your image
            backgroundColor = ContextCompat.getColor(this, R.color.yellow_500)

        ))

    }
}