package tech.ignacio.glosso.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Sentence(
    val id: Int? = null,
    val text: String,
    val ipa: String,
    val level: String,
    val topic: String,
    val language: String,
    val audio1: String? = null, // Vivian
    val audio2: String? = null  // Aiden
)

@Serializable
data class PronunciationFeedback(
    val score: Int = 0,
    val transcription: String = "",
    val feedback: String? = null,
    val normalizedActual: String? = null,
    val normalizedExpected: String? = null,
    val alignment: List<PhonemeMatchModel> = emptyList(),
    val letterFeedback: List<LetterFeedbackModel> = emptyList()
)

@Serializable
data class PhonemeMatchModel(
    val expected: String,
    val actual: String,
    val status: MatchStatusModel
)

@Serializable
data class LetterFeedbackModel(
    val char: String,
    val status: MatchStatusModel
)

@Serializable
enum class MatchStatusModel { PERFECT, CLOSE, MISSED }
