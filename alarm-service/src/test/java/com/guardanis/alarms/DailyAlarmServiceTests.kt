package com.guardanis.alarms

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(sdk=[Build.VERSION_CODES.R])
class DailyAlarmServiceTests {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val threeActive = listOf(
        DailyAlarmRequest(
            id = 0,
            startSecondsInDay = TimeUnit.HOURS.toSeconds(2).toInt(),
            endSecondsInDay = TimeUnit.HOURS.toSeconds(4).toInt(),
            active = true,
            vibrate = false,
            audioFile = "",
            playbackDurationSeconds = 0,
            repeatFrequencySeconds = 1
        ),
        DailyAlarmRequest(
            id = 1,
            startSecondsInDay = TimeUnit.HOURS.toSeconds(8).toInt(),
            TimeUnit.HOURS.toSeconds(10).toInt(),
            active = true,
            vibrate = false,
            audioFile = "",
            playbackDurationSeconds = 0,
            repeatFrequencySeconds = 1
        ),
        DailyAlarmRequest(
            id = 2,
            startSecondsInDay = TimeUnit.HOURS.toSeconds(14).toInt(),
            endSecondsInDay = TimeUnit.HOURS.toSeconds(16).toInt(),
            active = true,
            vibrate = false,
            audioFile = "",
            playbackDurationSeconds = 0,
            repeatFrequencySeconds = 1
        )
    )

    @Before
    fun clearPreferences() {
        DailyAlarmService.getSharedAlarmPreferences(context, 0)
            .edit()
            .clear()
            .apply()
    }

    @Test
    fun testNextEligibleAlarmCanBeFound() {
        fun alarm(duration: Int): DailyAlarmRequest? {
            return DailyAlarmService.nextEligibleAlarmOrNull(
                context,
                0,
                threeActive,
                TimeUnit.HOURS.toSeconds(duration.toLong()).toInt(),
                TimeUnit.HOURS.toMillis(duration.toLong())
            )
        }

        assertEquals(
            TimeUnit.HOURS.toSeconds(2).toInt(),
            alarm(1)?.startSecondsInDay
        )

        assertEquals(
            TimeUnit.HOURS.toSeconds(8).toInt(),
            alarm(6)?.startSecondsInDay
        )

        assertEquals(
            TimeUnit.HOURS.toSeconds(14).toInt(),
            alarm(12)?.startSecondsInDay
        )

        assertEquals(
            TimeUnit.HOURS.toSeconds(2).toInt(),
            alarm(20)?.startSecondsInDay
        )
    }

    @Test
    fun testNextEligibleAlarmSecondsFromNowWithRollover() {
        fun secondsFromNow(duration: Int): Long? {
            return DailyAlarmService.nextEligibleAlarmSecondsFromNowOrNull(
                context,
                0,
                threeActive,
                TimeUnit.HOURS.toSeconds(duration.toLong()).toInt(),
                TimeUnit.HOURS.toMillis(duration.toLong())
            )
        }

        assertEquals(
            TimeUnit.HOURS.toSeconds(2),
            secondsFromNow(0)
        )

        assertEquals(
            1L,
            secondsFromNow(2)
        )

        assertEquals(
            1L,
            secondsFromNow(15)
        )

        assertEquals(
            TimeUnit.HOURS.toSeconds(6),
            secondsFromNow(20)
        )
    }

    @Test
    fun testNextEligibleAlarmNotIncludedAfterTriggered() {
        val adjustedAlarms = listOf(
            DailyAlarmRequest(
                id = 0,
                startSecondsInDay = TimeUnit.HOURS.toSeconds(2).toInt(),
                endSecondsInDay = TimeUnit.HOURS.toSeconds(4).toInt(),
                active = true,
                vibrate = false,
                audioFile = "",
                playbackDurationSeconds = 0,
                repeatFrequencySeconds = TimeUnit.HOURS.toSeconds(1).toInt()
            ),
            threeActive[1],
            threeActive[2]
        )

        fun secondsFromNow(duration: Int): Long? {
            return DailyAlarmService.nextEligibleAlarmSecondsFromNowOrNull(
                context,
                0,
                adjustedAlarms,
                TimeUnit.HOURS.toSeconds(duration.toLong()).toInt(),
                TimeUnit.HOURS.toMillis(duration.toLong())
            )
        }

        DailyAlarmService.notifyAlarmTriggered(
            context,
            0,
            adjustedAlarms.first(),
            TimeUnit.HOURS.toMillis(3)
        )

        assertEquals(
            TimeUnit.HOURS.toSeconds(1),
            secondsFromNow(3)
        )

        DailyAlarmService.notifyAlarmTriggered(
            context,
            0,
            adjustedAlarms.first(),
            TimeUnit.HOURS.toMillis(3).plus(1000)
        )

        assertEquals(
            TimeUnit.HOURS.toSeconds(5),
            secondsFromNow(3)
        )
    }
}