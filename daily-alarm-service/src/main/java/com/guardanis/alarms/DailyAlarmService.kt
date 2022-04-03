package com.guardanis.alarms

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.JobIntentService
import java.util.concurrent.TimeUnit

abstract class DailyAlarmService: JobIntentService() {

    protected abstract val serviceName: String
    protected abstract val serviceJobId: Int

    protected abstract val alarms: List<DailyAlarmRequest>

    protected abstract val notificationClickedReceiverClass: Class<*>

    override fun onDestroy() {
        super.onDestroy()

        Log.d(tag, "$serviceName destroyed")
    }

    override fun onHandleWork(intent: Intent) {
        Log.d(tag, "$serviceName started")

        if (!isServiceEnabled()) {
            Log.d(tag, "$serviceName Inactive. Disabling.")

            return
        }

        val currentTimeOfDaySeconds = currentTimeOfDayInSeconds
        val currentEpochTime = System.currentTimeMillis()

        handleCurrentAlarmIfEligible(currentTimeOfDaySeconds, currentEpochTime)
        scheduleNextAlarmIfAvailable(currentTimeOfDaySeconds, currentEpochTime)
    }

    protected abstract fun isServiceEnabled(): Boolean

    private fun handleCurrentAlarmIfEligible(
        currentTimeOfDaySeconds: Int,
        currentEpochTime: Long) {

        // The "next" alarm will always be the current alarm, until it has been displayed
        val currentAlarm = nextEligibleAlarmOrNull(this, serviceJobId, alarms, currentTimeOfDaySeconds, currentEpochTime)

        if (currentAlarm == null) {
            Log.d(tag, "$serviceName No active alarm.")

            return
        }

        handleCurrentAlarm(currentAlarm, currentTimeOfDaySeconds, currentEpochTime)
    }

    protected open fun handleCurrentAlarm(
        alarm: DailyAlarmRequest,
        currentTimeOfDaySeconds: Int,
        currentEpochTime: Long) {

        Log.d(tag, "$serviceName Alarm eligible. Showing...")

        registerNotificationChannel()
        showNotification(alarm)
        playNotificationSound(alarm, currentEpochTime)
        vibrate(alarm)
        notifyAlarmTriggered(this, serviceJobId, alarm, currentEpochTime)
    }

    protected open fun showNotification(alarm: DailyAlarmRequest) {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            notificationManager.cancel(serviceJobId)

            val clickedIntentFlags: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else
                PendingIntent.FLAG_UPDATE_CURRENT

            val contentIntent = PendingIntent.getBroadcast(this, 0, buildNotificationClickedIntent(), clickedIntentFlags)

            val notification = buildNotification(contentIntent)
            notification.flags = notification.flags or (Notification.FLAG_ONLY_ALERT_ONCE or Notification.FLAG_AUTO_CANCEL)

            notificationManager.notify(serviceJobId, notification)
        }
        catch (e: Exception) {
            e.printStackTrace()
        }
    }

    protected open fun buildNotificationClickedIntent(): Intent {
        val clickedIntent = Intent(this, notificationClickedReceiverClass::class.java)
        clickedIntent.action = DailyAlarmNotificationClickedReceiver.NOTIFICATION_CLICKED_RECEIVER_KEY
        clickedIntent.putExtra(DailyAlarmNotificationClickedReceiver.EXTRA_SERVICE_JOB_ID, serviceJobId)

        return clickedIntent
    }

    protected abstract fun buildNotification(contentIntent: PendingIntent): Notification

    private fun registerNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val channel = createNotificationChannel()

        manager.createNotificationChannel(channel)
    }

    protected abstract fun createNotificationChannel(): NotificationChannel

    protected open fun vibrate(alarm: DailyAlarmRequest) {
        if (!alarm.vibrate)
            return

        Log.d(tag, "$serviceName Vibrating for Alarm ${alarm.id}")

        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            vibrator.vibrateCompat(vibratePattern(alarm))
        }
        catch (e: Exception) {
            e.printStackTrace()
        }
    }

    protected open fun vibratePattern(alarm: DailyAlarmRequest): LongArray {
        return alarm.vibratePattern
    }

    @SuppressLint("MissingPermission")
    protected fun Vibrator.vibrateCompat(pattern: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
        else {
            @Suppress("DEPRECATION")
            this.vibrate(pattern, -1)
        }
    }

    private fun playNotificationSound(alarm: DailyAlarmRequest, currentEpochTime: Long) {
        if (!alarm.audioPlaybackEnabled)
            return

        Log.d(tag, "$serviceName Playing audio for Alarm ${alarm.id}")

        DailyAlarmMediaPlaybackController.killMediaPlayback(this, serviceJobId)
        DailyAlarmMediaPlaybackController.play(serviceJobId, mediaPlaybackDataSource(alarm))
        DailyAlarmMediaPlaybackController.scheduleExpiration(
            this,
            serviceJobId = serviceJobId,
            currentEpochTime = currentEpochTime,
            durationSeconds = alarm.playbackDurationSeconds,
        )
    }

    protected open fun mediaPlaybackDataSource(alarm: DailyAlarmRequest): String {
        return alarm.audioFile
    }

    private fun scheduleNextAlarmIfAvailable(
        currentTimeOfDaySeconds: Int,
        currentEpochTime: Long) {

        scheduleNextAlarmIfAvailable(
            this,
            this::class.java,
            serviceJobId,
            alarms,
            currentTimeOfDaySeconds,
            currentEpochTime,
            buildWakeUpIntent()
        )
    }

    protected open fun buildWakeUpIntent(): Intent {
        return DailyAlarmWakeUpReceiver.buildWakeUpIntent(
            this,
            this::class.java,
            serviceJobId
        )
    }

    companion object {

        const val tag: String = "AbstractAlarmService"

        protected const val prefKeyLastNotify = "last_notify_%1\$s"

        fun <T: JobIntentService> restartOnApplicationCreate(
            context: Context,
            serviceClass: Class<T>,
            serviceJobId: Int,
            wakeupIntent: Intent) {

            val pendingIntentFlags: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            else
                PendingIntent.FLAG_NO_CREATE

            val pendingWakeUpBroadcast = PendingIntent.getBroadcast(context, 0, wakeupIntent, pendingIntentFlags)

            if (pendingWakeUpBroadcast != null) {
                Log.d(tag, "Wakeup for job $serviceJobId already scheduled. Aborting restart.")

                return
            }

            start(context, serviceClass, serviceJobId)
        }

        fun <T: JobIntentService> restart(
            context: Context,
            serviceClass: Class<T>,
            serviceJobId: Int,
            wakeupIntent: Intent) {

            stop(context, serviceJobId, wakeupIntent)
            start(context, serviceClass, serviceJobId)
        }

        fun <T: JobIntentService> start(context: Context, serviceClass: Class<T>, serviceJobId: Int) {
            enqueueWork(
                context,
                serviceClass,
                serviceJobId,
                Intent(context, serviceClass)
            )
        }

        fun <T: JobIntentService> scheduleNextAlarmIfAvailable(
            context: Context,
            serviceClass: Class<T>,
            serviceJobId: Int,
            alarms: List<DailyAlarmRequest>,
            currentTimeOfDaySeconds: Int,
            currentEpochTime: Long) {

            scheduleNextAlarmIfAvailable(
                context,
                serviceClass,
                serviceJobId,
                alarms,
                currentTimeOfDaySeconds,
                currentEpochTime,
                DailyAlarmWakeUpReceiver.buildWakeUpIntent(
                    context,
                    serviceClass,
                    serviceJobId
                )
            )
        }

        fun <T: JobIntentService> scheduleNextAlarmIfAvailable(
            context: Context,
            serviceClass: Class<T>,
            serviceJobId: Int,
            alarms: List<DailyAlarmRequest>,
            currentTimeOfDaySeconds: Int,
            currentEpochTime: Long,
            wakeupIntent: Intent) {

            val nextAlarmTimeFromNow = nextEligibleAlarmSecondsFromNowOrNull(
                context,
                serviceJobId,
                alarms,
                currentTimeOfDaySeconds,
                currentEpochTime
            )

            if (nextAlarmTimeFromNow == null) {
                Log.d(tag, "No alarm eligible for $serviceJobId. Aborting scheduling.")

                return
            }

            Log.d(tag, "Scheduling next alarm for $serviceJobId exactly $nextAlarmTimeFromNow seconds from now...")

            scheduleAlarmAfterSecondsFromNow(
                context,
                nextAlarmTimeFromNow,
                wakeupIntent
            )
        }
        
        fun scheduleAlarmAfterSecondsFromNow(context: Context, seconds: Long, wakeupIntent: Intent) {
            val pendingIntentFlags: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE
            else
                0

            cancelPendingAlarms(context, wakeupIntent)

            val pendingIntent = PendingIntent.getBroadcast(context, 0, wakeupIntent, pendingIntentFlags)

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

            alarmManager.setCompatRtcWakeup(
                System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(seconds),
                pendingIntent
            )
        }

        fun <T: JobIntentService> stop(
            context: Context,
            serviceClass: Class<T>,
            serviceJobId: Int) {

            stop(
                context,
                serviceJobId,
                DailyAlarmWakeUpReceiver.buildWakeUpIntent(context, serviceClass, serviceJobId)
            )
        }

        fun stop(
            context: Context,
            serviceJobId: Int,
            wakeupIntent: Intent) {

            cancelPendingAlarms(context, wakeupIntent)
            cancelNotification(context, serviceJobId)

            DailyAlarmMediaPlaybackController.cancelPendingMediaExpiration(context, serviceJobId)

            clearHistory(context, serviceJobId)
        }

        private fun cancelPendingAlarms(context: Context, wakeupIntent: Intent) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return

            alarmManager.cancelPendingBroadcastLaunch(context, wakeupIntent)
        }

        fun cancelNotification(context: Context, id: Int) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(id)
        }

        fun nextEligibleAlarmSecondsFromNowOrNull(
            context: Context,
            serviceJobId: Int,
            alarms: List<DailyAlarmRequest>,
            currentTimeOfDaySeconds: Int,
            currentEpochTime: Long): Long? {

            return nextEligibleAlarmOrNull(context, serviceJobId, alarms, currentTimeOfDaySeconds, currentEpochTime)
                ?.let({
                    it.nextEligibleRequestSecondsFromTimeOfDay(
                        currentTimeOfDaySeconds = currentTimeOfDaySeconds,
                        secondsSinceLastNotification = secondsSinceLastNotification(context, serviceJobId, it, currentEpochTime)
                    )
                })
        }

        fun nextEligibleAlarmOrNull(
            context: Context,
            serviceJobId: Int,
            alarms: List<DailyAlarmRequest>,
            currentTimeOfDaySeconds: Int,
            currentEpochTime: Long): DailyAlarmRequest? {

            return alarms
                .filter(DailyAlarmRequest::active)
                .minByOrNull({
                    it.nextEligibleRequestSecondsFromTimeOfDay(
                        currentTimeOfDaySeconds = currentTimeOfDaySeconds,
                        secondsSinceLastNotification = secondsSinceLastNotification(context, serviceJobId, it, currentEpochTime)
                    )
                })
        }

        internal open fun notifyAlarmTriggered(
            context: Context,
            serviceJobId: Int,
            alarm: DailyAlarmRequest,
            epochTimeMs: Long) {

            getSharedAlarmPreferences(context, serviceJobId)
                .edit()
                .putLong(
                    prefKeyLastNotify.format(alarm.id.toString()),
                    epochTimeMs
                )
                .apply()
        }

        fun secondsSinceLastNotification(
            context: Context,
            serviceJobId: Int,
            alarm: DailyAlarmRequest,
            currentEpochTime: Long): Long {

            return lastNotificationEpochTime(context, serviceJobId, alarm)
                .takeIf({ 0 < it })
                ?.let({ TimeUnit.MILLISECONDS.toSeconds(currentEpochTime - it) }) ?: 0
        }

        internal fun lastNotificationEpochTime(context: Context, serviceJobId: Int, alarm: DailyAlarmRequest): Long {
            return getSharedAlarmPreferences(context, serviceJobId)
                .getLong(prefKeyLastNotify.format(alarm.id.toString()), 0L)
        }

        fun clearHistory(context: Context, serviceJobId: Int) {
            getSharedAlarmPreferences(context, serviceJobId)
                .edit()
                .clear()
                .apply()
        }

        internal fun getSharedAlarmPreferences(context: Context, serviceJobId: Int): SharedPreferences {
            return context.getSharedPreferences("DailyAlarmService-$serviceJobId", Context.MODE_PRIVATE)
        }
    }
}