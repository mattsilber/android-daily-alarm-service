package com.guardanis.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

abstract class DailyAlarmNotificationClickedReceiver: BroadcastReceiver() {

    abstract val serviceJobId: Int

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == NOTIFICATION_CLICKED_RECEIVER_KEY) {
            handleAlarmNotificationClicked(context)
        }
    }

    open fun handleAlarmNotificationClicked(context: Context) {
        DailyAlarmService.killMediaPlayback(context)
        DailyAlarmService.cancelNotification(context, serviceJobId)
    }

    companion object {

        const val NOTIFICATION_CLICKED_RECEIVER_KEY = "alarm.service.NOTIFICATION_CLICKED"
    }
}