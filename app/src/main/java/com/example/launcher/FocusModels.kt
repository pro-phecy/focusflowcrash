package com.example.launcher

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

data class AppDef(
    val id: String,
    val name: String,
    val iconName: String, // mapped to an entry in our icons map
    val androidPackage: String
)

object AppLibrary {
    val ALL_APPS = listOf(
        // Communication
        AppDef("1", "Messages", "chat", "com.google.android.apps.messaging"),
        AppDef("2", "Phone", "phone", "com.android.dialer"),
        AppDef("3", "Mail", "mail", "com.google.android.gm"),
        AppDef("4", "WhatsApp", "whatsapp", "com.whatsapp"),
        AppDef("5", "Telegram", "telegram", "org.telegram.messenger"),
        AppDef("6", "Slack", "slack", "com.slack"),
        AppDef("7", "Discord", "discord", "com.discord"),

        // Social
        AppDef("8", "Instagram", "instagram", "com.instagram.android"),
        AppDef("9", "Twitter", "twitter", "com.twitter.android"),
        AppDef("10", "Facebook", "facebook", "com.facebook.katana"),
        AppDef("11", "LinkedIn", "linkedin", "com.linkedin.android"),
        AppDef("12", "TikTok", "tiktok", "com.zhiliaoapp.musically"),

        // Productivity
        AppDef("13", "Calendar", "calendar", "com.google.android.calendar"),
        AppDef("14", "Notes", "notes", "com.google.android.keep"),
        AppDef("15", "Tasks", "tasks", "com.google.android.apps.tasks"),
        AppDef("16", "Calculator", "calculator", "com.android.calculator2"),
        AppDef("17", "Notion", "notion", "notion.id"),
        AppDef("18", "Drive", "drive", "com.google.android.apps.docs"),

        // Entertainment
        AppDef("19", "YouTube", "youtube", "com.google.android.youtube"),
        AppDef("20", "Netflix", "netflix", "com.netflix.mediaclient"),
        AppDef("21", "Spotify", "spotify", "com.spotify.music"),
        AppDef("22", "Music", "music", "com.google.android.apps.youtube.music"),

        // Utilities
        AppDef("23", "Camera", "camera", "com.android.camera"),
        AppDef("24", "Maps", "maps", "com.google.android.apps.maps"),
        AppDef("25", "Browser", "browser", "com.android.chrome"),
        AppDef("26", "Settings", "settings", "com.android.settings"),
        AppDef("27", "Wallet", "wallet", "com.google.android.apps.walletnfcrel"),
        AppDef("28", "Health", "health", "com.google.android.apps.fitness")
    )

    fun getIcon(name: String): ImageVector {
        return when (name.lowercase()) {
            "chat", "whatsapp", "telegram", "slack", "discord" -> Icons.Default.ChatBubble
            "phone" -> Icons.Default.Phone
            "mail" -> Icons.Default.Email
            "instagram", "camera" -> Icons.Default.PhotoCamera
            "twitter", "facebook" -> Icons.Default.Public
            "linkedin" -> Icons.Default.Work
            "tiktok", "music", "spotify" -> Icons.Default.MusicNote
            "calendar" -> Icons.Default.DateRange
            "notes" -> Icons.Default.Edit
            "tasks" -> Icons.Default.CheckCircle
            "calculator" -> Icons.Default.Calculate
            "notion" -> Icons.Default.Book
            "drive" -> Icons.Default.Cloud
            "youtube", "netflix" -> Icons.Default.PlayCircle
            "maps" -> Icons.Default.Map
            "browser" -> Icons.Default.Language
            "settings" -> Icons.Default.Settings
            "wallet" -> Icons.Default.Wallet
            "health" -> Icons.Default.Favorite
            else -> Icons.Default.Apps
        }
    }
    
    fun getCategory(app: AppDef): String {
        return when (app.id.toIntOrNull() ?: 0) {
            in 1..7 -> "Communication"
            in 8..12 -> "Social"
            in 13..18 -> "Productivity"
            in 19..22 -> "Entertainment"
            else -> "Utilities"
        }
    }
}
