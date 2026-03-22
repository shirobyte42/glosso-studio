package me.shirobyte42.glosso.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SentenceEntity::class, MasteredSentenceEntity::class, ActivityDayEntity::class], 
    version = 6, 
    exportSchema = false
)
abstract class GlossoDatabase : RoomDatabase() {
    abstract val sentenceDao: SentenceDao
    abstract val masteredSentenceDao: MasteredSentenceDao
    abstract val activityDayDao: ActivityDayDao

    companion object {
        fun getDatabaseName(levelIndex: Int) = "sentences_$levelIndex.db"
        const val PROGRESS_DATABASE_NAME = "glosso_progress_db"
    }
}
