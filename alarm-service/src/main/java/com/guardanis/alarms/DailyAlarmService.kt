package com.guardanis.alarms

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.JobIntentService
import java.util.*
import java.util.concurrent.TimeUnit

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

        val currentTimeOfDaySeconds = this.currentTimeOfDayInSeconds
        val currentEpochTime = System.currentTimeMillis()

        handleCurrentAlarmIfEligible(currentTimeOfDaySeconds, currentEpochTime)
        scheduleNextAlarmIfAvailable(currentTimeOfDaySeconds, currentEpochTime)
    }

    protected abstract fun isServiceEnabled(): Boolean

    private fun handleCurrentAlarmIfEligible(
        currentTimeOfDaySeconds: Int,
        currentEpochTime: Long) {

        val currentAlarm = currentAlarmOrNull(this, serviceJobId, alarms, currentTimeOfDaySeconds, currentEpochTime)

        if (currentAlarm == null) {
            Log.d(tag, "$serviceName No active alarm.")

            return
        }

        Log.d(tag, "$serviceName Alarm eligible. Showing...")

        registerNotificationChannel()
        showNotification()
        playNotificationSound(currentAlarm)
        doVibrate(currentAlarm)
        notifyAlarmTriggered(this, serviceJobId, currentAlarm, currentEpochTime)
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

    private fun scheduleNextAlarmIfAvailable(
        currentTimeOfDaySeconds: Int,
        currentEpochTime: Long) {

        val nextAlarmTimeFromNow = nextEligibleAlarmSecondsFromNowOrNull(this, serviceJobId, alarms, currentTimeOfDaySeconds, currentEpochTime) ?: return

        scheduleAlarmAfterSecondsFromNow(this, nextAlarmTimeFromNow, buildWakeUpIntent())

        Log.d(tag, "$serviceName scheduling next alarm for $nextAlarmTimeFromNow seconds from now...")
    }

    protected abstract fun buildWakeUpIntent(): Intent

    companion object {

        const val tag: String = "AbstractAlarmService"

        private var mediaPlayer: MediaPlayer? = null

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

        fun currentAlarmOrNull(
            context: Context,
            serviceJobId: Int,
            alarms: List<DailyAlarmRequest>,
            currentTimeOfDaySeconds: Int,
            epochTimeMs: Long): DailyAlarmRequest? {

            return alarms
                .filter(DailyAlarmRequest::active)
                .sortedBy(DailyAlarmRequest::startSecondsInDay)
                .firstOrNull({
                    val secondsSinceNotification = secondsSinceLastNotification(context, serviceJobId, it, epochTimeMs)

                    currentTimeOfDaySeconds >= it.startSecondsInDay
                        && currentTimeOfDaySeconds <= it.endSecondsInDay
                        && currentTimeOfDaySeconds - secondsSinceNotification <= it.repeatFrequencySeconds
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


        internal fun secondsSinceLastNotification(
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

        internal fun getSharedAlarmPreferences(context: Context, serviceJobId: Int): SharedPreferences {
            return context.getSharedPreferences("DailyAlarmService-$serviceJobId", Context.MODE_PRIVATE)
        }
    }
}