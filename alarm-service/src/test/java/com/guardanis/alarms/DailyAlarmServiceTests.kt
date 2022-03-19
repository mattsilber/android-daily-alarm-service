package com.guardanis.alarms

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class DailyAlarmServiceTests {

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
                    id = 0,
                    startSecondsInDay = TimeUnit.HOURS.toSeconds(8).toInt(),
                    TimeUnit.HOURS.toSeconds(10).toInt(),
                    active = true,
                    vibrate = false,
                    audioFile = "",
                    playbackDurationSeconds = 0,
                    repeatFrequencySeconds = 1
            ),
            DailyAlarmRequest(
                    id = 0,
                    startSecondsInDay = TimeUnit.HOURS.toSeconds(14).toInt(),
                    endSecondsInDay = TimeUnit.HOURS.toSeconds(16).toInt(),
                    active = true,
                    vibrate = false,
                    audioFile = "",
                    playbackDurationSeconds = 0,
                    repeatFrequencySeconds = 1
            )
    )

    @Test
    fun testNextEligibleAlarmCanBeFound() {
        assertEquals(
                TimeUnit.HOURS.toSeconds(2).toInt(),
                DailyAlarmService.nextEligibleAlarmOrNull(threeActive, TimeUnit.HOURS.toSeconds(1).toInt())?.startSecondsInDay
        )

        assertEquals(
                TimeUnit.HOURS.toSeconds(8).toInt(),
                DailyAlarmService.nextEligibleAlarmOrNull(threeActive, TimeUnit.HOURS.toSeconds(6).toInt())?.startSecondsInDay
        )

        assertEquals(
                TimeUnit.HOURS.toSeconds(14).toInt(),
                DailyAlarmService.nextEligibleAlarmOrNull(threeActive, TimeUnit.HOURS.toSeconds(12).toInt())?.startSecondsInDay
        )

        assertEquals(
                TimeUnit.HOURS.toSeconds(2).toInt(),
                DailyAlarmService.nextEligibleAlarmOrNull(threeActive, TimeUnit.HOURS.toSeconds(20).toInt())?.startSecondsInDay
        )
    }

    @Test
    fun testNextEligibleAlarmSecondsFromNowWithRollover() {
        assertEquals(
                TimeUnit.HOURS.toSeconds(2),
                DailyAlarmService.nextEligibleAlarmSecondsFromNowOrNull(threeActive, TimeUnit.HOURS.toSeconds(0).toInt())
        )

        assertEquals(
                1L,
                DailyAlarmService.nextEligibleAlarmSecondsFromNowOrNull(threeActive, TimeUnit.HOURS.toSeconds(2).toInt())
        )

        assertEquals(
                1L,
                DailyAlarmService.nextEligibleAlarmSecondsFromNowOrNull(threeActive, TimeUnit.HOURS.toSeconds(15).toInt())
        )

        assertEquals(
                TimeUnit.HOURS.toSeconds(6),
                DailyAlarmService.nextEligibleAlarmSecondsFromNowOrNull(threeActive, TimeUnit.HOURS.toSeconds(20).toInt())
        )
    }
}