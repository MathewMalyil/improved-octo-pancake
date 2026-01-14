package com.mmalyil.smartdocai.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.mmalyil.smartdocai.UsageResetReceiver
import java.util.Calendar

object AlarmScheduler {

    private const val ACTION_DAILY_RESET = "com.mmalyil.smartdocai.action.DAILY_USAGE_RESET"
    private const val REQ_CODE_RESET = 101

    fun scheduleDailyReset(context: Context) {
        val appCtx = context.applicationContext
        val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(appCtx, UsageResetReceiver::class.java).apply {
            action = ACTION_DAILY_RESET
        }

        val pi = PendingIntent.getBroadcast(
            appCtx,
            REQ_CODE_RESET,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAt = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DATE, 1)
        }.timeInMillis

        // ✅ Inexact repeating is the most OEM-friendly + battery-policy-friendly.
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerAt,
            AlarmManager.INTERVAL_DAY,
            pi
        )
    }

    fun cancelDailyReset(context: Context) {
        val appCtx = context.applicationContext
        val alarmManager = appCtx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(appCtx, UsageResetReceiver::class.java).apply {
            action = ACTION_DAILY_RESET
        }

        val pi = PendingIntent.getBroadcast(
            appCtx,
            REQ_CODE_RESET,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pi)
    }
}