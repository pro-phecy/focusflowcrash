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
    private const val CHANNEL_ID = "focusflow_channel"
    private const val CHANNEL_NAME = "FocusFlow Notifications"
    private const val CHANNEL_DESC = "Notifications and status updates from FocusFlow"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendNotification(context: Context, title: String, content: String) {
        try {
            createNotificationChannel(context)
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
        }
        
        val requestCode = "${entry.day}-${entry.startTime}".hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = getNextTriggerTime(entry.day, entry.startTime) ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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

    private fun getNextTriggerTime(dayName: String, timeStr: String): Long? {
        try {
            val parts = timeStr.split(":")
            if (parts.size != 2) return null
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null

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
