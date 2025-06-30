package com.mmalyil.smartdocai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.mmalyil.smartdocai.util.GPTUsageManager

/**
 * BroadcastReceiver to reset GPT usage limits daily.
 * This receiver listens for a specific broadcast action to reset the usage counts.
 */






class UsageResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        GPTUsageManager.resetAll(context)
        Toast.makeText(context, "Daily usage limits reset", Toast.LENGTH_SHORT).show()
    }
}