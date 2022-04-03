package com.guardanis.alarms.sample

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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
            if (!canScheduleExactAlarms()) {
                SampleDailyAlarmService.setEnabled(this, false)

                showCannotScheduleAlarmsError()

                return
            }

            DailyAlarmService.start(
                this,
                SampleDailyAlarmService::class.java,
                SampleDailyAlarmService.serviceJobId
            )

            // Or, to schedule the next eligible from now,
            // replace the [DailyAlarmService.start] with this:
//            DailyAlarmService.scheduleNextAlarmIfAvailable(
//                this,
//                SampleDailyAlarmService::class.java,
//                SampleDailyAlarmService.serviceJobId,
//                SampleDailyAlarmService.mockedAlarms,
//                currentTimeOfDayInSeconds,
//                System.currentTimeMillis()
//            )
        }
        else {
            DailyAlarmService.stop(
                this,
                SampleDailyAlarmService::class.java,
                SampleDailyAlarmService.serviceJobId
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

    private fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true
        }

        val alarmService = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false

        return alarmService.canScheduleExactAlarms()
    }

    private fun showCannotScheduleAlarmsError() {
        AlertDialog.Builder(this)
            .setMessage("You need SCHEDULE_EXACT_ALARM permissions to use the service")
            .setPositiveButton("Edit permissions", { _, _ ->
                val intent = Intent()
                intent.action = android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM

                startActivity(intent)
            })
            .setNegativeButton("Cancel", null)
            .show()
    }
}