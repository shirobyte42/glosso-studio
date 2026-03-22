package me.shirobyte42.glosso.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores a record for each day the user has mastered at least one sentence.
 * Date format is "YYYY-MM-DD".
 */
@Entity(tableName = "activity_days")
data class ActivityDayEntity(
    @PrimaryKey val dateString: String,
    val timestamp: Long = System.currentTimeMillis()
)
