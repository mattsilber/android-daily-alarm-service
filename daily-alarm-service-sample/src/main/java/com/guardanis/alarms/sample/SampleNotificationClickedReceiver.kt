package com.guardanis.alarms.sample

import android.content.Context
import android.util.Log
import com.guardanis.alarms.DailyAlarmNotificationClickedReceiver

class SampleNotificationClickedReceiver: DailyAlarmNotificationClickedReceiver() {

    override val serviceJobId: Int = SampleDailyAlarmService.serviceJobId

    override fun handleAlarmNotificationClicked(context: Context) {
        Log.d("DAS_Sample", "Alarm notification was clicked for job $serviceJobId")

        super.handleAlarmNotificationClicked(context)
    }
}