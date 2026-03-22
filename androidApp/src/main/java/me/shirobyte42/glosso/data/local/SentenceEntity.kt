package me.shirobyte42.glosso.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import me.shirobyte42.glosso.domain.model.Sentence

@Entity(
    tableName = "sentences",
    indices = [
        Index(value = ["id"], name = "ix_sentences_id"),
        Index(value = ["language"], name = "ix_sentences_language"),
        Index(value = ["level"], name = "ix_sentences_level"),
        Index(value = ["language", "level"], name = "ix_lang_level"),
        Index(value = ["topic"], name = "ix_sentences_topic"),
        Index(value = ["language", "level", "topic"], name = "ix_lang_level_topic")
    ]
)
data class SentenceEntity(
    @PrimaryKey val id: Int,
    val text: String,
    val ipa: String?,
    @ColumnInfo(name = "level") val level: String?,
    @ColumnInfo(name = "topic") val topic: String?,
    @ColumnInfo(name = "language") val language: String?,
    @ColumnInfo(name = "audio_b64") val audio1: String?,
    @ColumnInfo(name = "audio_b64_2") val audio2: String?
)

fun SentenceEntity.toDomain() = Sentence(
    id = id,
    text = text,
    ipa = ipa ?: "",
    level = level ?: "A1",
    topic = topic ?: "General",
    language = language ?: "en",
    audio1 = audio1,
    audio2 = audio2
)

fun Sentence.toEntity() = SentenceEntity(
    id = id ?: 0,
    text = text,
    ipa = ipa,
    level = level,
    topic = topic,
    language = language,
    audio1 = audio1,
    audio2 = audio2
)
