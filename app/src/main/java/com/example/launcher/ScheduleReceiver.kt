package com.example.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AppDatabase
import com.example.data.Converters
import com.example.data.ScheduleEntry
import com.example.data.KeyValueSetting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                if (action == "com.example.SESSION_TIMER_COMPLETE") {
                    val db = AppDatabase.getDatabase(context.applicationContext)
                    val profile = db.userProfileDao().getProfile()
                    val notificationsEnabled = profile?.notifications != false

                    // Find and mark active session as completed
                    val sessions = db.focusSessionDao().getAllSessions()
                    val activeSession = sessions.firstOrNull { !it.completed }
                    if (activeSession != null) {
                        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                        sdf.timeZone = TimeZone.getTimeZone("UTC")
                        val endTimeStr = sdf.format(Date())
                        val updated = activeSession.copy(completed = true, endedAt = endTimeStr)
                        db.focusSessionDao().insertSession(updated)

                        if (notificationsEnabled) {
                            NotificationHelper.sendNotification(
                                context,
                                "Focus Session Completed!",
                                "Fantastic job! You stayed focused for the full duration of: ${activeSession.goal}."
                            )
                        }
                    } else {
                        if (notificationsEnabled) {
                            NotificationHelper.sendNotification(
                                context,
                                "Focus Session Completed!",
                                "Fantastic job! Your focus session timer has reached zero."
                            )
                        }
                    }

                    // Clear active session key-value settings
                    db.keyValueDao().insertSetting(KeyValueSetting("active_session_id", ""))
                    db.keyValueDao().insertSetting(KeyValueSetting("timer_running", "false"))
                    db.keyValueDao().insertSetting(KeyValueSetting("timer_time_left", "0"))
                } else if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON" || action == "com.htc.intent.action.QUICKBOOT_POWERON") {
                    // Re-schedule all stored focus blocks after boot
                    val db = AppDatabase.getDatabase(context.applicationContext)
                    val profile = db.userProfileDao().getProfile()
                    if (profile != null) {
                        val scheduleJson = profile.scheduleJson
                        val scheduleList = Converters().toScheduleList(scheduleJson)
                        for (entry in scheduleList) {
                            NotificationHelper.scheduleNotification(context.applicationContext, entry)
                        }
                    }
                } else if (action == "com.example.FOCUS_ALARM") {
                    // Triggered Alarm
                    val day = intent.getStringExtra("day")
                    val startTime = intent.getStringExtra("startTime")
                    val endTime = intent.getStringExtra("endTime")
                    val task = intent.getStringExtra("task") ?: "Deep Work"
                    
                    val db = AppDatabase.getDatabase(context.applicationContext)
                    val profile = db.userProfileDao().getProfile()
                    val notificationsEnabled = profile?.notifications != false

                    if (notificationsEnabled) {
                        val displayDay = day ?: "Today"
                        val displayStart = startTime ?: "now"
                        
                        // Send system notification
                        NotificationHelper.sendNotification(
                            context,
                            "Focus Reminder: $task",
                            "It's time for your planned '$task' focus session ($displayDay at $displayStart)!"
                        )
                    }
                    
                    if (day != null && startTime != null && endTime != null) {
                        // Schedule the next weekly occurrence for this specific block
                        val entry = ScheduleEntry(day, startTime, endTime, task)
                        NotificationHelper.scheduleNotification(context.applicationContext, entry)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
