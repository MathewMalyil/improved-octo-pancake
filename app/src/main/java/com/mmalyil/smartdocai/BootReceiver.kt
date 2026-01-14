package com.mmalyil.smartdocai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mmalyil.smartdocai.util.AlarmScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED != intent.action) return

        // ✅ Don’t do scheduling directly in receiver on Android 15 OEMs
        val pending = goAsync()
        try {
            BootWorker.enqueue(context.applicationContext)
        } finally {
            pending.finish()
        }
    }
}

class BootWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            AlarmScheduler.scheduleDailyReset(applicationContext)
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "boot_reschedule_alarm"

        fun enqueue(context: Context) {
            val req = OneTimeWorkRequestBuilder<BootWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_NAME,
                ExistingWorkPolicy.REPLACE,
                req
            )
        }
    }
}