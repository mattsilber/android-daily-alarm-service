package com.guardanis.alarms.sample

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.guardanis.alarms.DailyAlarmService
import com.guardanis.alarms.currentTimeOfDayInSeconds

class MainActivity: AppCompatActivity(), View.OnClickListener {

    override fun onCreate(savedInstance: Bundle?) {
        super.onCreate(savedInstance)

        setContentView(R.layout.activity_main)

        findViewById<View>(R.id.sample_action_toggle_enabled)
            .setOnClickListener(this)

        updateNextAlarmInfo()
        updateActionText()
    }

    override fun onClick(v: View?) {
        when (v?.id ?: 0) {
            R.id.sample_action_toggle_enabled -> toggleEnabledClicked(v)
            else -> {
                Log.d("DAS-Sample", "What?... ${v?.id}")
            }
        }
    }

    private fun toggleEnabledClicked(view: View?) {
        SampleDailyAlarmService.setEnabled(this, !SampleDailyAlarmService.isEnabled(this))

        if (SampleDailyAlarmService.isEnabled(this)) {
            DailyAlarmService.start(
                this,
                SampleDailyAlarmService::class.java,
                SampleDailyAlarmService.serviceJobId
            )
        }
        else {
            DailyAlarmService.stop(
                this,
                SampleDailyAlarmService.serviceJobId,
                SampleDailyAlarmService.buildWakeUpIntent(this)
            )
        }

        updateNextAlarmInfo()
        updateActionText()
    }

    private fun updateNextAlarmInfo() {
        val nextAlarm = DailyAlarmService.nextEligibleAlarmOrNull(
            this,
            SampleDailyAlarmService.serviceJobId,
            SampleDailyAlarmService.mockedAlarms,
            currentTimeOfDayInSeconds,
            System.currentTimeMillis()
        ) ?: throw RuntimeException("There should always be a sample alarm here...")

        findViewById<TextView>(R.id.sample_next_alarm_info).text = getString(
            R.string.das_info_next_alarm,
            nextAlarm.startTime,
            nextAlarm.endTime,
            nextAlarm.nextEligibleRequestSecondsFromTimeOfDay(
                currentTimeOfDaySeconds = currentTimeOfDayInSeconds,
                secondsSinceLastNotification = DailyAlarmService.secondsSinceLastNotification(
                    this,
                    SampleDailyAlarmService.serviceJobId,
                    nextAlarm,
                    System.currentTimeMillis()
                )
            ).toString()
        )
    }

    private fun updateActionText() {
        findViewById<TextView>(R.id.sample_action_toggle_enabled).text = getString(
            when {
                SampleDailyAlarmService.isEnabled(this) -> R.string.das_action_disable
                else -> R.string.das_action_start
            }
        )
    }
}