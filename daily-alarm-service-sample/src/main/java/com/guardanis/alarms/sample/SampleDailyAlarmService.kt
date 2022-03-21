package com.guardanis.alarms.sample

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import com.guardanis.alarms.DailyAlarmRequest
import com.guardanis.alarms.DailyAlarmService
import com.guardanis.alarms.WakeUpReceiver
import java.util.concurrent.TimeUnit

class SampleDailyAlarmService: DailyAlarmService() {

    override val serviceName: String = "Sample-Daily-Alarm-Service"
    override val serviceJobId: Int = SampleDailyAlarmService.serviceJobId

    override val alarms: List<DailyAlarmRequest> = mockedAlarms

    override val notificationClickedReceiverClass: Class<*> = SampleNotificationClickedReceiver::class.java

    private val notificationChannelName: String = "sample_notifications"

    override fun isServiceEnabled(): Boolean {
        return isEnabled(this)
    }

    override fun buildNotification(contentIntent: PendingIntent): Notification {
        return NotificationCompat.Builder(this, notificationChannelName)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.das_notification_title))
            .setContentText(getString(R.string.das_notification_message))
            .setContentIntent(contentIntent)
            .build()
    }

    override fun createNotificationChannel(): NotificationChannel {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            throw RuntimeException("Service shouldn't have called this!!!!")

        val channel = NotificationChannel(
            notificationChannelName,
            getString(R.string.das_notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        )

        channel.description = getString(R.string.das_notification_channel_description)

        return channel
    }

    override fun buildWakeUpIntent(): Intent {
        return buildWakeUpIntent(this)
    }

    companion object {

        const val serviceJobId: Int = 1234

        private const val prefEnabledKey = "alarm_service_enabled"

        val mockedAlarms = listOf(
            DailyAlarmRequest(
                id = 0,
                startSecondsInDay = TimeUnit.HOURS.toSeconds(10).toInt(),
                endSecondsInDay = TimeUnit.HOURS.toSeconds(23).toInt(),
                active = true,
                vibratePattern = longArrayOf(0, 200, 200, 450, 0),
                repeatFrequencySeconds = TimeUnit.MINUTES.toSeconds(25).toInt()
            )
        )

        private fun getSharedPreferences(context: Context): SharedPreferences {
            return context.getSharedPreferences("sample-daily-alarm-service", Context.MODE_PRIVATE)
        }

        fun setEnabled(context: Context, enabled: Boolean) {
            getSharedPreferences(context)
                .edit()
                .putBoolean(prefEnabledKey, enabled)
                .apply()
        }

        fun isEnabled(context: Context): Boolean {
            return getSharedPreferences(context)
                .getBoolean(prefEnabledKey, false)
        }

        fun buildWakeUpIntent(context: Context): Intent {
            val wakeUpIntent = Intent(context, SampleWakeUpReceiver::class.java)
            wakeUpIntent.action = WakeUpReceiver.RECEIVER_KEY

            return wakeUpIntent
        }
    }
}