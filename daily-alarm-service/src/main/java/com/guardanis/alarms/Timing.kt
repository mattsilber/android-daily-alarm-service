package com.guardanis.alarms

import java.util.*
import java.util.concurrent.TimeUnit

val currentTimeOfDayInSeconds: Int
    get() {
        val cal = Calendar.getInstance()

        return (TimeUnit.HOURS.toSeconds(cal[Calendar.HOUR_OF_DAY].toLong())
            .plus(TimeUnit.MINUTES.toSeconds(cal[Calendar.MINUTE].toLong()))
            .plus(cal[Calendar.SECOND]).toInt())
    }