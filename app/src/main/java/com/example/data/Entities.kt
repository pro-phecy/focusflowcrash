package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AppDefinition(
    val id: String,
    val name: String,
    val icon: String,           // Material Icon name representation
    val androidPackage: String  // e.g. "com.whatsapp"
)

@JsonClass(generateAdapter = true)
data class ScheduleEntry(
    val day: String,            // "Monday", "Tuesday", etc.
    val startTime: String,      // "HH:MM"
    val endTime: String,         // "HH:MM"
    val task: String = "Deep Work" // e.g. "Coding / Dev", "Reading / Research", etc.
)

@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val userId: String,
    val displayName: String,
    val email: String,
    val dailyGoal: Int,          // in minutes
    val preferredAppsJson: String, // List<String> serialized to JSON
    val scheduleJson: String,      // List<ScheduleEntry> serialized to JSON
    val notifications: Boolean,
    val darkMode: Boolean,
    val privacyMode: Boolean,
    val photoUrl: String?
)

@Entity(tableName = "focus_sessions")
data class FocusSessionEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val startedAt: String,
    val duration: Int,           // total seconds
    val goal: String,
    val allowedAppsJson: String,  // List<String> serialized to JSON
    val completed: Boolean,
    val endedAt: String?
)

@Entity(tableName = "key_value_settings")
data class KeyValueSetting(
    @PrimaryKey val key: String,
    val value: String
)
