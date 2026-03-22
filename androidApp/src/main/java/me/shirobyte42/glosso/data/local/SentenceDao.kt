package me.shirobyte42.glosso.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SentenceDao {
    @Query("SELECT * FROM sentences")
    fun getAllSentences(): Flow<List<SentenceEntity>>

    @Query("SELECT * FROM sentences WHERE level = :level AND language = :language")
    suspend fun getSentencesByLevel(level: String, language: String): List<SentenceEntity>

    @Query("SELECT * FROM sentences WHERE level = :level AND language = :language AND text NOT IN (:excludeTexts) ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomSentenceByLevel(level: String, language: String, excludeTexts: List<String>): SentenceEntity?

    @Query("SELECT * FROM sentences WHERE level = :level AND topic = :topic AND language = :language AND text NOT IN (:excludeTexts) ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomSentenceByLevelAndTopic(level: String, topic: String, language: String, excludeTexts: List<String>): SentenceEntity?

    @Query("SELECT * FROM sentences WHERE level = :level AND topic IN (:topics) AND language = :language AND text NOT IN (:excludeTexts) ORDER BY RANDOM() LIMIT 1")
    suspend fun getRandomSentenceByLevelAndTopics(level: String, topics: List<String>, language: String, excludeTexts: List<String>): SentenceEntity?

    @Query("SELECT DISTINCT topic FROM sentences WHERE level = :level AND language = :language ORDER BY topic")
    suspend fun getTopicsByLevel(level: String, language: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSentences(sentences: List<SentenceEntity>)

    @Query("DELETE FROM sentences")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM sentences")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM sentences WHERE level = :level AND language = :language")
    suspend fun getSentenceCountByLevel(level: String, language: String): Int
}
