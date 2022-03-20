package com.guardanis.alarms.sample

import com.guardanis.alarms.WakeUpReceiver

class SampleWakeUpReceiver: WakeUpReceiver<SampleDailyAlarmService>() {

    override val serviceClass: Class<SampleDailyAlarmService> = SampleDailyAlarmService::class.java
    override val serviceJobId: Int = SampleDailyAlarmService.serviceJobId
}