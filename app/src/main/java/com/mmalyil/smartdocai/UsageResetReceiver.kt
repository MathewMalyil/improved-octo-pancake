package com.mmalyil.smartdocai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mmalyil.smartdocai.util.GPTUsageManager

class UsageResetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.mmalyil.smartdocai.action.DAILY_USAGE_RESET") return
        val pending = goAsync()
        try { UsageResetWorker.enqueue(context.applicationContext) }
        finally { pending.finish() }
    }
}

class UsageResetWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            GPTUsageManager.resetAll(applicationContext)
            Result.success()
        } catch (_: Throwable) {
            // if something transient happens, retry
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "usage_reset_once"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<UsageResetWorker>().build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}