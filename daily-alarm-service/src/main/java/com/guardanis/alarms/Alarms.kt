package com.guardanis.alarms

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.concurrent.TimeUnit

fun AlarmManager.setCompatRtcWakeupSecondsFromNow(afterSeconds: Int, pendingIntent: PendingIntent) {
    return this.setCompatRtcWakeup(
            System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(afterSeconds.toLong()),
            pendingIntent
    )
}

@SuppressLint("NewApi")
fun AlarmManager.setCompatRtcWakeup(afterMs: Long, pendingIntent: PendingIntent) {
    when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT ->
            this.set(AlarmManager.RTC_WAKEUP, afterMs, pendingIntent)
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M ->
            this.setExact(AlarmManager.RTC_WAKEUP, afterMs, pendingIntent)
        else ->
            this.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, afterMs, pendingIntent)
    }
}

fun AlarmManager.cancelPendingBroadcastLaunch(context: Context, serviceIntent: Intent) {
    val pendingIntentFlags: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
    else
        PendingIntent.FLAG_NO_CREATE

    try {
        val displayIntent = PendingIntent.getBroadcast(context, 0, serviceIntent, pendingIntentFlags)
                ?: return

        this.cancel(displayIntent)

        displayIntent.cancel()
    }
    catch (e: Exception) {
        e.printStackTrace()
    }
}