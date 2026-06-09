package com.example.data

import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class Converters {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
    private val stringListAdapter = moshi.adapter<List<String>>(stringListType)

    private val scheduleListType = Types.newParameterizedType(List::class.java, ScheduleEntry::class.java)
    private val scheduleListAdapter = moshi.adapter<List<ScheduleEntry>>(scheduleListType)

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return stringListAdapter.toJson(value ?: emptyList())
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        return try {
            stringListAdapter.fromJson(value) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromScheduleList(value: List<ScheduleEntry>?): String {
        return scheduleListAdapter.toJson(value ?: emptyList())
    }

    @TypeConverter
    fun toScheduleList(value: String?): List<ScheduleEntry> {
        if (value.isNullOrEmpty()) return emptyList()
        return try {
            scheduleListAdapter.fromJson(value) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
