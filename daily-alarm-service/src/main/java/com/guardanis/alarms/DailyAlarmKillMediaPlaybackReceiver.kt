package com.guardanis.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DailyAlarmKillMediaPlaybackReceiver: BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == RECEIVER_KEY) {
            val serviceJobId = intent.getIntExtra(EXTRA_SERVICE_JOB_ID, -1)

            if (serviceJobId == -1) {
                return
            }

            DailyAlarmMediaPlaybackController.killMediaPlayback(context, serviceJobId)
        }
    }

    companion object {

        const val RECEIVER_KEY = "alarm.service.KILL_MEDIA_PLAYBACK"
        const val EXTRA_SERVICE_JOB_ID = "alarm.service.KILL_MEDIA_SERVICE_JOB_ID"
    }
}