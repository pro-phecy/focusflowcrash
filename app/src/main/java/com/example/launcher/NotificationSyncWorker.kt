package com.example.launcher

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import com.example.data.AppDatabase
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class NotificationSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        try {
            val db = AppDatabase.getDatabase(applicationContext)
            val profile = db.userProfileDao().getProfile()
            
            // Check if notifications are enabled in profile settings
            val notificationsEnabled = profile?.notifications != false
            if (!notificationsEnabled) {
                return Result.success()
            }

            // 1. Fetch sessions completed today to calculate goal progress
            val sessions = db.focusSessionDao().getAllSessions()
            val calendar = Calendar.getInstance()
            val todayDayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
            val todayYear = calendar.get(Calendar.YEAR)

            val todayMinutes = sessions.filter {
                val sCal = Calendar.getInstance().apply {
                    val timeMillis = parseIso8601ToMillis(it.startedAt)
                    if (timeMillis != null) {
                        timeInMillis = timeMillis
                    }
                }
                sCal.get(Calendar.DAY_OF_YEAR) == todayDayOfYear && sCal.get(Calendar.YEAR) == todayYear
            }.sumOf { it.duration / 60 }

            val dailyGoal = profile?.dailyGoal ?: 45 // default goal

            // 2. Generate appropriate push notification based on progress and focus tips
            val (title, content) = if (todayMinutes < dailyGoal) {
                val deficit = dailyGoal - todayMinutes
                val motivationTips = listOf(
                    "Ready for some focus flow?" to "You are $deficit minutes away from your daily deep work goal. Let's start a session!",
                    "Your daily streak is waiting" to "Stay consistent! Put down the distractions and achieve your $dailyGoal-minute daily goal.",
                    "Time for deep focus" to "You've focused for $todayMinutes minutes today. A quick 15-minute session will boost your momentum!",
                    "Cosmic Focus Alert 🚀" to "A stellar focus session of 25 minutes will put you right on track for your daily target.",
                    "Optimize your cognitive space" to "Eliminate micro-distractions. You're just a few blocks away from your daily goal."
                )
                motivationTips[Random.nextInt(motivationTips.size)]
            } else {
                val rewardTips = listOf(
                    "Daily Goal Completed! 🎉" to "Outstanding job! You hit your focus target of $dailyGoal minutes. Keep this momentum!",
                    "Elite Focus Achievement 🌟" to "You have exceeded your daily goal with $todayMinutes minutes of deep work!",
                    "A productive day indeed" to "Your brain deserves a structured break. All focus blocks successfully completed today!"
                )
                rewardTips[Random.nextInt(rewardTips.size)]
            }

            // 3. Occasionally inject a high-quality productivity/deep-work concept notification
            val showConceptTip = Random.nextFloat() < 0.4f
            val finalNotification = if (showConceptTip) {
                val deepWorkTips = listOf(
                    "The 90-Minute Rule" to "Did you know? Humans naturally operate on ultradian rhythms. Aim for 90-minute focus blocks followed by a 15-minute rest.",
                    "Task Batching Tip" to "Batch similar small tasks together. This reduces switching costs and preserves executive attention.",
                    "Digital Minimalism" to "Keep your phone in another room during high-priority tasks. Out of sight is out of mind.",
                    "Context Switching cost" to "It takes an average of 23 minutes to refocus after a single distraction. Guard your attention carefully!"
                )
                deepWorkTips[Random.nextInt(deepWorkTips.size)]
            } else {
                title to content
            }

            // Send push notification using helper
            NotificationHelper.sendNotification(
                applicationContext,
                finalNotification.first,
                finalNotification.second
            )

            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry()
        }
    }

    private fun parseIso8601ToMillis(isoString: String): Long? {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            sdf.parse(isoString)?.time
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val WORK_NAME = "com.example.launcher.NOTIF_SYNC_WORK"

        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<NotificationSyncWorker>(
                4, TimeUnit.HOURS, // Run every 4 hours in the background
                15, TimeUnit.MINUTES // 15-minute flex interval
            )
                .setInitialDelay(2, TimeUnit.HOURS) // Delayed start so it doesn't immediately fire on startup
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP, // Keep existing schedule if it exists
                workRequest
            )
        }

        fun triggerImmediately(context: Context) {
            // One-off work request to trigger push notification immediately for test/preview
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<NotificationSyncWorker>().build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
