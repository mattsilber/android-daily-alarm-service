package com.guardanis.alarms

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class DailyAlarmRequestTests {

    @Test
    fun testOverlapsReturnsFalseWhenRangesDoNotOverlap() {
        assertFalse(
            request(0, 1)
                .overlaps(request(2, 3))
        )

        assertFalse(
            request(2, 3)
                .overlaps(request(0, 1))
        )
    }

    @Test
    fun testOverlapsReturnsTrueWhenRangesOverlap() {
        assertTrue(
            request(0, 1)
                .overlaps(request(1, 3))
        )

        assertTrue(
            request(0, 3)
                .overlaps(request(1, 2))
        )

        assertTrue(
            request(0, 3)
                .overlaps(request(2, 4))
        )

        assertTrue(
            request(1, 3)
                .overlaps(request(0, 1))
        )
    }

    @Test
    fun testBasicNextEligibleRequestSecondsFromTimeOfDayCases() {
        assertEquals(
            1,
            request(1, 3)
                .nextEligibleRequestSecondsFromTimeOfDay(
                    currentTimeOfDaySeconds = 1,
                    secondsSinceLastNotification = 0
                )
        )

        assertEquals(
            1,
            request(1, 3)
                .nextEligibleRequestSecondsFromTimeOfDay(
                    currentTimeOfDaySeconds = 0,
                    secondsSinceLastNotification = 0
                )
        )

        assertEquals(
            0,
            request(1, 3)
                .nextEligibleRequestSecondsFromTimeOfDay(
                    currentTimeOfDaySeconds = 1,
                    secondsSinceLastNotification = 1
                )
        )

        assertEquals(
            TimeUnit.DAYS.toSeconds(1).minus(2),
            request(1, 3)
                .nextEligibleRequestSecondsFromTimeOfDay(
                    currentTimeOfDaySeconds = 3,
                    secondsSinceLastNotification = 0
                )
        )

        assertEquals(
            TimeUnit.HOURS.toSeconds(8),
            request(TimeUnit.HOURS.toSeconds(10).toInt(), TimeUnit.HOURS.toSeconds(11).toInt())
                .nextEligibleRequestSecondsFromTimeOfDay(
                    currentTimeOfDaySeconds = TimeUnit.HOURS.toSeconds(2).toInt(),
                    secondsSinceLastNotification = 0
                )
        )

        assertEquals(
            TimeUnit.HOURS.toSeconds(8),
            request(TimeUnit.HOURS.toSeconds(10).toInt(), TimeUnit.HOURS.toSeconds(11).toInt())
                .nextEligibleRequestSecondsFromTimeOfDay(
                    currentTimeOfDaySeconds = TimeUnit.HOURS.toSeconds(2).toInt(),
                    secondsSinceLastNotification = 1
                )
        )

        assertEquals(
            TimeUnit.HOURS.toSeconds(22),
            request(TimeUnit.HOURS.toSeconds(10).toInt(), TimeUnit.HOURS.toSeconds(11).toInt())
                .nextEligibleRequestSecondsFromTimeOfDay(
                    currentTimeOfDaySeconds = TimeUnit.HOURS.toSeconds(12).toInt(),
                    secondsSinceLastNotification = 1
                )
        )
    }

    private fun request(startSecondsInDay: Int, endSecondsInDay: Int): DailyAlarmRequest {
        return DailyAlarmRequest(
            id = 0,
            startSecondsInDay = startSecondsInDay,
            endSecondsInDay = endSecondsInDay,
            repeatFrequencySeconds = 1
        )
    }
}