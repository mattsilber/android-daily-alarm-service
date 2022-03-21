package com.guardanis.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.JobIntentService

class DailyAlarmWakeUpReceiver: BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == RECEIVER_KEY) {
            val serviceJobId = intent.getIntExtra(EXTRA_SERVICE_JOB_ID, -1)
            val serviceClass = intent.getSerializableExtra(EXTRA_SERVICE_CLASS) as? Class<JobIntentService>

            if (serviceJobId == -1 || serviceClass == null) {
                return
            }

            DailyAlarmService.start(context, serviceClass, serviceJobId)
        }
    }

    companion object {

        const val RECEIVER_KEY = "alarm.service.WAKE_UP"

        const val EXTRA_SERVICE_CLASS = "alarm.service.KILL_MEDIA_SERVICE_CLASS"
        const val EXTRA_SERVICE_JOB_ID = "alarm.service.WAKE_UP_SERVICE_JOB_ID"

        fun <T: JobIntentService> buildWakeUpIntent(
            context: Context,
            serviceClass: Class<T>,
            serviceJobId: Int): Intent {

            val wakeUpIntent = Intent(context, DailyAlarmWakeUpReceiver::class.java)
            wakeUpIntent.action = RECEIVER_KEY
            wakeUpIntent.putExtra(EXTRA_SERVICE_CLASS, serviceClass)
            wakeUpIntent.putExtra(EXTRA_SERVICE_JOB_ID, serviceJobId)

            return wakeUpIntent
        }
    }
}