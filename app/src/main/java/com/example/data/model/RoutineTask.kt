package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Locale

@Entity(tableName = "routine_tasks")
data class RoutineTask(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val icon: String = "⏱️",
    val colorHex: String = "#F472B6", // Default soft pink
    val startMinute: Int = 720,       // Minutes from midnight (720 = 12:00 PM)
    val durationMinutes: Int = 60,   // Default 1 hour
    val isEnabled: Boolean = true,
    val notes: String = ""
) {
    val endMinute: Int
        get() = (startMinute + durationMinutes) % 1440

    fun isCurrent(currentMinuteOfDay: Int): Boolean {
        if (!isEnabled) return false
        val end = startMinute + durationMinutes
        return if (end <= 1440) {
            currentMinuteOfDay in startMinute until end
        } else {
            // Task wraps around midnight
            currentMinuteOfDay >= startMinute || currentMinuteOfDay < (end % 1440)
        }
    }

    fun formatStartTime(is24Hour: Boolean = true): String {
        val hour = (startMinute / 60) % 24
        val minute = startMinute % 60
        return if (is24Hour) {
            String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        } else {
            val displayHour = if (hour % 12 == 0) 12 else hour % 12
            val amPm = if (hour < 12) "AM" else "PM"
            String.format(Locale.getDefault(), "%02d:%02d %s", displayHour, minute, amPm)
        }
    }

    fun formatEndTime(is24Hour: Boolean = true): String {
        val end = (startMinute + durationMinutes) % 1440
        val hour = end / 60
        val minute = end % 60
        return if (is24Hour) {
            String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        } else {
            val displayHour = if (hour % 12 == 0) 12 else hour % 12
            val amPm = if (hour < 12) "AM" else "PM"
            String.format(Locale.getDefault(), "%02d:%02d %s", displayHour, minute, amPm)
        }
    }

    fun formatTimeRange(is24Hour: Boolean = true): String {
        return "${formatStartTime(is24Hour)} - ${formatEndTime(is24Hour)}"
    }
}
