package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile LIMIT 1")
    fun getProfileFlow(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile LIMIT 1")
    suspend fun getProfile(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfileEntity)

    @Query("DELETE FROM user_profile")
    suspend fun clearProfile()
}

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC")
    fun getAllSessionsFlow(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC")
    suspend fun getAllSessions(): List<FocusSessionEntity>

    @Query("SELECT * FROM focus_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): FocusSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: FocusSessionEntity)

    @Query("DELETE FROM focus_sessions")
    suspend fun clearSessions()
}

@Dao
interface KeyValueDao {
    @Query("SELECT * FROM key_value_settings WHERE `key` = :settingKey LIMIT 1")
    fun getValueFlow(settingKey: String): Flow<KeyValueSetting?>

    @Query("SELECT * FROM key_value_settings WHERE `key` = :settingKey LIMIT 1")
    suspend fun getValue(settingKey: String): KeyValueSetting?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: KeyValueSetting)

    @Query("DELETE FROM key_value_settings WHERE `key` = :settingKey")
    suspend fun deleteSetting(settingKey: String)

    @Query("DELETE FROM key_value_settings")
    suspend fun clear()
}
