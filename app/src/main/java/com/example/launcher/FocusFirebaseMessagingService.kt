package com.example.launcher

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.KeyValueSetting
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FocusFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Store the registration token locally so we can display it in the settings dashboard for testing
        saveTokenLocally(applicationContext, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Extract parameters from message payload
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "FocusFlow Bulletin"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: "Stay in your focus flow."

        // Check if notifications are enabled in database settings
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val profile = db.userProfileDao().getProfile()
                val notificationsEnabled = profile?.notifications != false

                if (notificationsEnabled) {
                    NotificationHelper.sendNotification(applicationContext, title, body)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to sending notification anyway in case db is locked
                NotificationHelper.sendNotification(applicationContext, title, body)
            }
        }
    }

    companion object {
        private const val FCM_TOKEN_KEY = "fcm_registration_token"

        fun saveTokenLocally(context: Context, token: String) {
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                try {
                    val db = AppDatabase.getDatabase(context.applicationContext)
                    db.keyValueDao().insertSetting(KeyValueSetting(FCM_TOKEN_KEY, token))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        fun getSavedToken(context: Context, callback: (String?) -> Unit) {
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                try {
                    val db = AppDatabase.getDatabase(context.applicationContext)
                    val setting = db.keyValueDao().getValue(FCM_TOKEN_KEY)
                    callback(setting?.value)
                } catch (e: Exception) {
                    e.printStackTrace()
                    callback(null)
                }
            }
        }
    }
}
