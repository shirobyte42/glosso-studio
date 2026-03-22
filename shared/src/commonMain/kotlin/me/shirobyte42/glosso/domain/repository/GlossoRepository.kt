package me.shirobyte42.glosso.domain.repository

import me.shirobyte42.glosso.domain.model.PronunciationFeedback
import me.shirobyte42.glosso.domain.model.Sentence

interface GlossoRepository {
    suspend fun getSample(category: Int, language: String, topics: List<String>? = null, exclude: List<String> = emptyList()): Sentence
    suspend fun getTopics(category: Int, language: String): List<String>
    suspend fun getSentenceCount(category: Int, language: String): Int
    suspend fun analyzeSpeech(base64Audio: String, targetText: String, targetIpa: String, mode: String = "quick", language: String = "en"): PronunciationFeedback
}
