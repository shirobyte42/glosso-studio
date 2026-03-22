package tech.ignacio.glosso.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mastered_sentences")
data class MasteredSentenceEntity(
    @PrimaryKey val text: String,
    val levelIndex: Int,
    val masteredAt: Long = System.currentTimeMillis()
)
