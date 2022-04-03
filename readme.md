## Daily Alarm Service
[![Tests](https://github.com/mattsilber/android-daily-alarm-service/actions/workflows/test_and_deploy.yml/badge.svg?branch=master)](https://github.com/mattsilber/android-daily-alarm-service/actions/workflows/test_and_deploy.yml)

Allow a user to schedule one to many alarms that should run at a desired frequency between a specified time period every single day. Each alarm may also have it's own audio playback, vibration, and notification display options.

### Import the Library

```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation "com.guardanis:daily-alarm-service:1.0.0"
}
```

### Create the Service

```kotlin
class SampleDailyAlarmService: DailyAlarmService() {

    override val serviceName: String = "Sample-Daily-Alarm-Service"
    override val serviceJobId: Int = 1234

    // Load your alarms from wherever you want
    override val alarms: List<DailyAlarmRequest> = listOf(
        DailyAlarmRequest(
            id = 0,
            startSecondsInDay = TimeUnit.HOURS.toSeconds(10).toInt(),
            endSecondsInDay = TimeUnit.HOURS.toSeconds(18).toInt(),
            active = true,
            vibratePattern = longArrayOf(0, 200, 200, 450, 0),
            repeatFrequencySeconds = TimeUnit.MINUTES.toSeconds(25).toInt()
        )
    )

    // Define a DailyAlarmNotificationClickedReceiver because it's abstract (it should be open...sorry)
    override val notificationClickedReceiverClass: Class<*> = SampleNotificationClickedReceiver::class.java

    private val notificationChannelName: String = "sample_notifications"

    override fun isServiceEnabled(): Boolean {
        // Return true if your service is currently allowed to run
        return true 
    }

    override fun buildNotification(contentIntent: PendingIntent): Notification {
        return NotificationCompat.Builder(this, notificationChannelName)
            .build()
    }

    override fun createNotificationChannel(): NotificationChannel {
        return NotificationChannel(
            notificationChannelName,
            "Sample Channel",
            NotificationManager.IMPORTANCE_HIGH
        )
    }
}
```

### Declare the Service

```xml
<service 
    android:name=".SampleDailyAlarmService"
    android:permission="android.permission.BIND_JOB_SERVICE"
    android:enabled="true" />

```

### Start the Service

Schedule the next alarm to run after the eligible alarm's frequency:

```kotlin
DailyAlarmService.scheduleNextAlarmIfAvailable(
    context,
    SampleDailyAlarmService::class.java,
    1234,
    yourListOfAlarms,
    currentTimeOfDayInSeconds,
    System.currentTimeMillis()
)
```

Or start the service directly, which will immediately display the notification and schedule itself for later:

```kotlin
DailyAlarmService.start(
    context,
    SampleDailyAlarmService::class.java,
    1234
)
```

### Stop the Service

```kotlin
DailyAlarmService.stop(     
    context,
    SampleDailyAlarmService::class.java,
    1234
)
```