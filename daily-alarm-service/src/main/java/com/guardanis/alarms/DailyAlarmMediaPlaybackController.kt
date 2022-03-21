package com.guardanis.alarms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import java.util.concurrent.TimeUnit

/**
 * There can only be one.
 */
object DailyAlarmMediaPlaybackController {

    private val mediaPlayers = mutableMapOf<Int, MediaPlayer>()

    fun play(serviceJobId: Int, audioFile: String) {
        try {
            val mediaPlayer = MediaPlayer()
            mediaPlayer.setDataSource(audioFile)
            mediaPlayer.prepare()
            mediaPlayer.start()
            mediaPlayer.setOnCompletionListener({
                try {
                    mediaPlayer.release()
                }
                catch (e: Exception) {
                    e.printStackTrace()
                }

                mediaPlayers.remove(key = serviceJobId)
            })

            mediaPlayers[serviceJobId] = mediaPlayer
        }
        catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun scheduleExpiration(
        context: Context,
        serviceJobId: Int,
        currentEpochTime: Long,
        durationSeconds: Int) {

        val pendingIntentFlags: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_IMMUTABLE
        else
            0

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            expirationReceiverBuilder(context, serviceJobId),
            pendingIntentFlags
        )

        val playbackDurationMs = TimeUnit.SECONDS.toMillis(durationSeconds.toLong())

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        alarmManager.setCompatRtcWakeup(currentEpochTime + playbackDurationMs, pendingIntent)
    }

    fun killMediaPlayback(context: Context, serviceJobId: Int) {
        releaseMediaPlayer(serviceJobId)
        cancelPendingMediaExpiration(context, serviceJobId)
    }

    fun cancelPendingMediaExpiration(context: Context, serviceJobId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return

        alarmManager.cancelPendingBroadcastLaunch(context, expirationReceiverBuilder(context, serviceJobId))
    }

    private fun expirationReceiverBuilder(context: Context, serviceJobId: Int): Intent {
        val expirationIntent = Intent(context, DailyAlarmKillMediaPlaybackReceiver::class.java)
        expirationIntent.action = DailyAlarmKillMediaPlaybackReceiver.RECEIVER_KEY
        expirationIntent.putExtra(DailyAlarmKillMediaPlaybackReceiver.EXTRA_SERVICE_JOB_ID, serviceJobId)

        return expirationIntent
    }

    private fun releaseMediaPlayer(serviceJobId: Int) {
        try {
            mediaPlayers[serviceJobId]?.run({
                this.stop()
                this.release()
            })

            mediaPlayers.remove(key = serviceJobId)
        }
        catch (e: Exception) {
            e.printStackTrace()
        }
    }

}