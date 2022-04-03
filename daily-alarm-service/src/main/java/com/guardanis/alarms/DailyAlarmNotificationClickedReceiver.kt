package com.guardanis.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

abstract class DailyAlarmNotificationClickedReceiver: BroadcastReceiver() {

    abstract val serviceJobId: Int

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == NOTIFICATION_CLICKED_RECEIVER_KEY) {
            val serviceJobId = intent.getIntExtra(EXTRA_SERVICE_JOB_ID, -1)

            if (serviceJobId != this.serviceJobId) {
                return
            }

            handleAlarmNotificationClicked(context)
        }
    }

    open fun handleAlarmNotificationClicked(context: Context) {
        DailyAlarmMediaPlaybackController.killMediaPlayback(context, serviceJobId)

        DailyAlarmService.cancelNotification(context, serviceJobId)
    }

    companion object {

        const val NOTIFICATION_CLICKED_RECEIVER_KEY = "alarm.service.NOTIFICATION_CLICKED"

        const val EXTRA_SERVICE_JOB_ID = "alarm.service.NOTIFICATION_CLICKED_SERVICE_JOB_ID"
    }
}