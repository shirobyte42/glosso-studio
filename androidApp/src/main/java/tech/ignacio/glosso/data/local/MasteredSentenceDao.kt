package tech.ignacio.glosso.data.local

import androidx.room.*

@Dao
interface MasteredSentenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMasteredSentence(sentence: MasteredSentenceEntity)

    @Query("SELECT text FROM mastered_sentences")
    suspend fun getAllMasteredTexts(): List<String>

    @Query("SELECT COUNT(*) FROM mastered_sentences WHERE levelIndex = :levelIndex")
    suspend fun getCountByLevel(levelIndex: Int): Int

    @Query("SELECT COUNT(*) FROM mastered_sentences")
    suspend fun getTotalCount(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM mastered_sentences WHERE text = :text)")
    suspend fun isMastered(text: String): Boolean

    @Query("DELETE FROM mastered_sentences")
    suspend fun deleteAll()
}
