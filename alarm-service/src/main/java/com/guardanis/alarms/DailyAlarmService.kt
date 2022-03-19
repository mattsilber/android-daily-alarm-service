package com.guardanis.alarms

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.JobIntentService
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.max

abstract class DailyAlarmService: JobIntentService() {

    protected abstract val serviceName: String
    protected abstract val serviceJobId: Int

    protected abstract val alarms: List<DailyAlarmRequest>

    protected val currentTimeOfDayInSeconds: Int
        get() {
            val cal = Calendar.getInstance()

            return (TimeUnit.HOURS.toSeconds(cal[Calendar.HOUR_OF_DAY].toLong())
                    .plus(TimeUnit.MINUTES.toSeconds(cal[Calendar.MINUTE].toLong()))
                    .plus(cal[Calendar.SECOND]).toInt())
        }

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

        handleCurrentAlarmIfEligible()
        scheduleNextAlarmIfAvailable()
    }

    protected abstract fun isServiceEnabled(): Boolean

    private fun handleCurrentAlarmIfEligible() {
        val currentAlarm = currentAlarmOrNull(alarms, currentTimeOfDayInSeconds)
                ?: run {
                    Log.d(tag, "$serviceName No active alarm.")

                    null
                }
                ?: return

        Log.d(tag, "$serviceName Alarm eligible. Showing...")

        registerNotificationChannel()
        showNotification()
        playNotificationSound(currentAlarm)
        doVibrate(currentAlarm)
    }

    private fun showNotification() {
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

    private fun doVibrate(alarm: DailyAlarmRequest) {
        if (!alarm.vibrate)
            return

        Log.d(tag, "$serviceName Vibrating for Alarm ${alarm.id}")

        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            vibrator.vibrateCompat(longArrayOf(0, 200, 200, 450, 0))
        }
        catch (e: Exception) {
            e.printStackTrace()
        }
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

    private fun playNotificationSound(alarm: DailyAlarmRequest) {
        releaseMediaPlayer()

        if (!alarm.audioPlaybackEnabled)
            return

        Log.d(tag, "$serviceName Playing audio for Alarm ${alarm.id}")

        try {
            mediaPlayer = MediaPlayer()
            mediaPlayer?.setDataSource(alarm.audioFile)
            mediaPlayer?.prepare()
            mediaPlayer?.start()
            mediaPlayer?.setOnCompletionListener({
                try {
                    mediaPlayer?.release()
                }
                catch (e: Exception) {
                    e.printStackTrace()
                }
            })
        }
        catch (e: Exception) {
            e.printStackTrace()
        }

        cancelPendingMediaExpiration(this)
        scheduleMediaExpiration(alarm)
    }

    private fun scheduleMediaExpiration(alarm: DailyAlarmRequest) {
        val pendingIntentFlags: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_IMMUTABLE
        else
            0

        val pendingIntent = PendingIntent.getBroadcast(this, 0, mediaExpirationReceiverBuilder(this), pendingIntentFlags)

        val playbackDurationMs = TimeUnit.SECONDS.toMillis(alarm.playbackDurationSeconds.toLong())

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.setCompatRtcWakeup(System.currentTimeMillis() + playbackDurationMs, pendingIntent)
    }

    private fun scheduleNextAlarmIfAvailable() {
        val nextAlarmTimeFromNow = nextEligibleAlarmSecondsFromNowOrNull(alarms, currentTimeOfDayInSeconds) ?: return

        scheduleAlarmAfterSecondsFromNow(this, nextAlarmTimeFromNow, buildWakeUpIntent())

        Log.d(tag, "$serviceName scheduling next alarm for $nextAlarmTimeFromNow seconds from now...")
    }

    protected abstract fun buildWakeUpIntent(): Intent

    companion object {

        const val tag: String = "AbstractAlarmService"

        private var mediaPlayer: MediaPlayer? = null

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
                    serviceClass::class.java,
                    serviceJobId,
                    Intent(context, serviceClass::class.java)
            )
        }

        private fun scheduleAlarmAfterSecondsFromNow(context: Context, seconds: Long, wakeupIntent: Intent) {
            val pendingIntentFlags: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_IMMUTABLE
            else
                0

            cancelPendingAlarms(context, wakeupIntent)

            val pendingIntent = PendingIntent.getBroadcast(context, 0, wakeupIntent, pendingIntentFlags)

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                    ?: return

            alarmManager.setCompatRtcWakeup(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(seconds), pendingIntent)
        }

        fun stop(
                context: Context,
                serviceJobId: Int,
                wakeupIntent: Intent) {

            cancelPendingAlarms(context, wakeupIntent)
            cancelNotification(context, serviceJobId)
            releaseMediaPlayer()
            cancelPendingMediaExpiration(context)
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

        fun killMediaPlayback(context: Context) {
            releaseMediaPlayer()
            cancelPendingMediaExpiration(context)
        }

        private fun releaseMediaPlayer() {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            }
            catch (e: Exception) {
                e.printStackTrace()
            }
        }

        private fun mediaExpirationReceiverBuilder(context: Context): Intent {
            val expirationIntent = Intent(context, KillMediaPlaybackReceiver::class.java)
            expirationIntent.action = KillMediaPlaybackReceiver.RECEIVER_KEY

            return expirationIntent
        }

        private fun cancelPendingMediaExpiration(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                    ?: return

            alarmManager.cancelPendingBroadcastLaunch(context, mediaExpirationReceiverBuilder(context))
        }

        fun nextEligibleAlarmSecondsFromNowOrNull(
                alarms: List<DailyAlarmRequest>,
                currentTimeSeconds: Int): Long? {

            val nextAlarm = nextEligibleAlarmOrNull(alarms, currentTimeSeconds)
                    ?: return null

            val currentTimeWithFrequency = currentTimeSeconds + nextAlarm.repeatFrequencySeconds

            return when {
                currentTimeWithFrequency in nextAlarm.startSecondsInDay..nextAlarm.endSecondsInDay ->
                    nextAlarm.repeatFrequencySeconds.toLong()
                nextAlarm.endSecondsInDay < currentTimeWithFrequency ->
                    TimeUnit.HOURS.toSeconds(24) - currentTimeSeconds + nextAlarm.startSecondsInDay
                else ->
                    (max(currentTimeWithFrequency, nextAlarm.startSecondsInDay) - currentTimeSeconds).toLong()
            }
        }

        fun nextEligibleAlarmOrNull(
                alarms: List<DailyAlarmRequest>,
                currentTimeSeconds: Int): DailyAlarmRequest? {

            val alarms = alarms
                    .takeIf(List<*>::isNotEmpty)
                    ?.filter(DailyAlarmRequest::active)
                    ?.sortedBy(DailyAlarmRequest::startSecondsInDay) ?: return null

            return alarms.firstOrNull({ currentTimeSeconds + it.repeatFrequencySeconds <= it.endSecondsInDay })
                    ?: alarms.firstOrNull({ currentTimeSeconds + it.repeatFrequencySeconds >= it.startSecondsInDay })
        }

        fun currentAlarmOrNull(
                alarms: List<DailyAlarmRequest>,
                currentTimeSeconds: Int): DailyAlarmRequest? {

            val alarms = alarms
                    .takeIf(List<*>::isNotEmpty)
                    ?.filter(DailyAlarmRequest::active)
                    ?.sortedBy(DailyAlarmRequest::startSecondsInDay) ?: return null

            return alarms.firstOrNull({
                it.active && currentTimeSeconds >= it.startSecondsInDay && currentTimeSeconds <= it.endSecondsInDay
            })
        }
    }
}