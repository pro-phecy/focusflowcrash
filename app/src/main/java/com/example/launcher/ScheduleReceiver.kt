package com.example.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AppDatabase
import com.example.data.Converters
import com.example.data.ScheduleEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == "android.intent.action.QUICKBOOT_POWERON" || action == "com.htc.intent.action.QUICKBOOT_POWERON") {
            // Re-schedule all stored focus blocks after boot
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                try {
                    val db = AppDatabase.getDatabase(context.applicationContext)
                    val profile = db.userProfileDao().getProfile()
                    if (profile != null) {
                        val scheduleJson = profile.scheduleJson
                        val scheduleList = Converters().toScheduleList(scheduleJson)
                        for (entry in scheduleList) {
                            NotificationHelper.scheduleNotification(context.applicationContext, entry)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } else {
            // Triggered Alarm
            val day = intent.getStringExtra("day") ?: return
            val startTime = intent.getStringExtra("startTime") ?: return
            val endTime = intent.getStringExtra("endTime") ?: return
            
            // Send system notification
            NotificationHelper.sendNotification(
                context,
                "Scheduled Focus Session Starting!",
                "It is time for your scheduled Focus session from $startTime to $endTime ($day)."
            )
            
            // Schedule the next weekly occurrence for this specific block
            val entry = ScheduleEntry(day, startTime, endTime)
            NotificationHelper.scheduleNotification(context.applicationContext, entry)
        }
    }
}
