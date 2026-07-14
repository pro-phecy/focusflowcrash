package com.example.launcher

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.ScheduleEntry
import java.util.Calendar
import java.util.Locale

object NotificationHelper {
    private const val CHANNEL_ID = "focusflow_push_channel"
    private const val CHANNEL_NAME = "FocusFlow Alerts"
    private const val CHANNEL_DESC = "Push alerts and focus reminders from FocusFlow"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableLights(true)
                lightColor = android.graphics.Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendNotification(context: Context, title: String, content: String) {
        try {
            val appContext = context.applicationContext
            createNotificationChannel(appContext)
            val intent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                appContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val soundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)

            val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(com.example.R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setSound(soundUri)
                .setVibrate(longArrayOf(0, 250, 250, 250))
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun scheduleNotification(context: Context, entry: ScheduleEntry) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = "com.example.FOCUS_ALARM"
            putExtra("day", entry.day)
            putExtra("startTime", entry.startTime)
            putExtra("endTime", entry.endTime)
            putExtra("task", entry.task)
        }
        
        val requestCode = "${entry.day}-${entry.startTime}".hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = getNextTriggerTime(entry.day, entry.startTime) ?: return

        setExactAlarmSafely(alarmManager, triggerTime, pendingIntent)
    }

    fun cancelScheduledNotification(context: Context, entry: ScheduleEntry) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = "com.example.FOCUS_ALARM"
        }
        val requestCode = "${entry.day}-${entry.startTime}".hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun scheduleSessionEndAlarm(context: Context, durationSeconds: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = "com.example.SESSION_TIMER_COMPLETE"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            9999, // Unique request code for active session alarm
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + (durationSeconds * 1000L)
        setExactAlarmSafely(alarmManager, triggerTime, pendingIntent)
    }

    private fun setExactAlarmSafely(alarmManager: AlarmManager, triggerTime: Long, pendingIntent: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)
                alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: Exception) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }

    fun cancelSessionEndAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = "com.example.SESSION_TIMER_COMPLETE"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            9999,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun getNextTriggerTime(dayName: String, timeStr: String): Long? {
        try {
            val cleanedTime = timeStr.trim()
            val isPm = cleanedTime.endsWith("PM", ignoreCase = true)
            val isAm = cleanedTime.endsWith("AM", ignoreCase = true)
            
            // Remove AM/PM suffix for splitting
            val timeWithoutAmPm = cleanedTime
                .replace("AM", "", ignoreCase = true)
                .replace("PM", "", ignoreCase = true)
                .trim()

            val parts = timeWithoutAmPm.split(":")
            if (parts.size != 2) return null
            var hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null

            if (isPm || isAm) {
                // 12-hour format conversion to 24-hour format
                if (isPm && hour < 12) {
                    hour += 12
                } else if (isAm && hour == 12) {
                    hour = 0
                }
            }

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val targetDayOfWeek = when (dayName.lowercase(Locale.US)) {
                "sunday" -> Calendar.SUNDAY
                "monday" -> Calendar.MONDAY
                "tuesday" -> Calendar.TUESDAY
                "wednesday" -> Calendar.WEDNESDAY
                "thursday" -> Calendar.THURSDAY
                "friday" -> Calendar.FRIDAY
                "saturday" -> Calendar.SATURDAY
                else -> return null
            }

            val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            var daysDiff = targetDayOfWeek - currentDayOfWeek
            if (daysDiff < 0) {
                daysDiff += 7
            } else if (daysDiff == 0) {
                if (calendar.timeInMillis <= System.currentTimeMillis()) {
                    daysDiff = 7
                }
            }
            
            if (daysDiff > 0) {
                calendar.add(Calendar.DAY_OF_YEAR, daysDiff)
            }

            return calendar.timeInMillis
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
