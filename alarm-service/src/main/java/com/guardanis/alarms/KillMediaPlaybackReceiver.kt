package com.guardanis.alarms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class KillMediaPlaybackReceiver: BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == RECEIVER_KEY) {
            DailyAlarmService.killMediaPlayback(context)
        }
    }

    companion object {
        const val RECEIVER_KEY = "alarm.service.KILL_MEDIA_PLAYBACK"
    }
}