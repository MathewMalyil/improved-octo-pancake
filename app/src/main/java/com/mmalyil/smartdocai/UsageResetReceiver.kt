package com.mmalyil.smartdocai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkerParameters
import com.mmalyil.smartdocai.util.GPTUsageManager
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * BroadcastReceiver to reset GPT usage limits daily.
 * This receiver listens for a specific broadcast action to reset the usage counts.
 */



class UsageResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        UsageResetWorker.enqueue(context)
    }
}


class UsageResetWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        GPTUsageManager.resetAll(applicationContext)
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<UsageResetWorker>().build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}