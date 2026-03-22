package tech.ignacio.glosso.data.local

import androidx.room.*

@Dao
interface ActivityDayDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDay(day: ActivityDayEntity)

    @Query("SELECT dateString FROM activity_days ORDER BY dateString DESC")
    suspend fun getAllActivityDates(): List<String>

    @Query("SELECT COUNT(*) FROM activity_days")
    suspend fun getTotalActivityDays(): Int

    @Query("DELETE FROM activity_days")
    suspend fun deleteAll()
}
