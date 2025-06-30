package com.mmalyil.smartdocai


import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

import com.mmalyil.smartdocai.util.AlarmScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            AlarmScheduler.scheduleDailyReset(context)
        }
    }
}