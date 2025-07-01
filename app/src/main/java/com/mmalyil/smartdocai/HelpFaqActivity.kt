package com.mmalyil.smartdocai

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mmalyil.smartdocai.R
// HelpFaqActivity.kt
import android.content.Intent
import android.net.Uri
import android.widget.Button




class HelpFaqActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help_faq)
        supportActionBar?.title = "Help & FAQ"
    }
}