package com.guardanis.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.JobIntentService

abstract class WakeUpReceiver<T: JobIntentService>: BroadcastReceiver() {

    abstract val serviceClass: Class<T>
    abstract val serviceJobId: Int

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == RECEIVER_KEY) {
            DailyAlarmService.start(context, serviceClass, serviceJobId)
        }
    }

    companion object {

        const val RECEIVER_KEY = "alarm.service.WAKE_UP"
    }
}