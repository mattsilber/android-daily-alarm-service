package com.guardanis.alarms

import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class DailyAlarmRequest() {

    var id: Int = 0

    var active: Boolean = false
    var vibrate: Boolean = false

    var playbackDurationSeconds: Int = 0

    var audioFile: String = ""

    val audioPlaybackEnabled: Boolean
        get() = audioFile.isNotEmpty()

    var startSecondsInDay: Int = 60 * 60 * 12

    val startsAtMillisecondsIntoDay: Long
        get() = startSecondsInDay.toLong()

    val startHoursInDay: Int
        get() = (startSecondsInDay / (60 * 60))

    val startMinutesInDay: Int
        get() = (startSecondsInDay / 60) % 60

    val startTime: String
        get() = secondsToTime(startSecondsInDay.toLong())

    var endSecondsInDay: Int = 60 * 60 * 12 + 60

    val endHoursInDay: Int
        get() = (endSecondsInDay / (60 * 60))

    val endMinutesInDay: Int
        get() = (endSecondsInDay / 60) % 60

    val endTime: String
        get() = secondsToTime(endSecondsInDay.toLong())

    var repeatFrequencySeconds: Int = 60 * 60 * 23

    constructor(
            id: Int,
            startSecondsInDay: Int,
            endSecondsInDay: Int,
            active: Boolean,
            vibrate: Boolean,
            audioFile: String,
            playbackDurationSeconds: Int,
            repeatFrequencySeconds: Int): this() {

        this.id = id
        this.startSecondsInDay = startSecondsInDay
        this.endSecondsInDay = endSecondsInDay
        this.active = active
        this.vibrate = vibrate
        this.audioFile = audioFile
        this.playbackDurationSeconds = playbackDurationSeconds
        this.repeatFrequencySeconds = repeatFrequencySeconds
    }

    override fun equals(obj: Any?): Boolean {
        return (obj is DailyAlarmRequest && this.id == obj.id)
    }

    fun overlaps(another: DailyAlarmRequest): Boolean {
        val thisRange = this.startSecondsInDay..this.endSecondsInDay
        val anotherRange = another.startSecondsInDay..another.endSecondsInDay

        return min(thisRange.last, anotherRange.last)
                .minus(max(thisRange.first, anotherRange.first))
                .let({ 0 <= it })
    }

    private fun secondsToTime(seconds: Long): String {
        val hours = TimeUnit.SECONDS.toHours(seconds).toInt()
        val minutes = TimeUnit.SECONDS.toMinutes(seconds).toInt() % 60

        return (if (hours % 12 == 0) 12 else hours % 12)
                .toString()
                .plus(":")
                .plus(if (minutes > 9) minutes else "0$minutes")
                .plus(" ")
                .plus(if (hours >= 12) "PM" else "AM")
    }
}