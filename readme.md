# Daily Alarm Service


### Declare Permissions

```xml
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

### Declare Services

```xml
<service 
    android:name=".SampleDailyAlarmService"
    android:permission="android.permission.BIND_JOB_SERVICE"
    android:enabled="true" />

<receiver
    android:name=".SampleWakeUpReceiver"
    android:enabled="true"
    android:exported="true">
    <intent-filter>
        <action android:name="alarm.service.WAKE_UP" />
    </intent-filter>
</receiver>
```

Replace names with your own implementations. See those samples for details.