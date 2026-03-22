package me.shirobyte42.glosso.data.repository

import me.shirobyte42.glosso.domain.model.PronunciationFeedback
import me.shirobyte42.glosso.domain.model.Sentence
import me.shirobyte42.glosso.domain.repository.GlossoRepository
import me.shirobyte42.glosso.domain.repository.LocalSentenceProvider

class GlossoRepositoryImpl(
    private val localDataSource: LocalSentenceProvider
) : GlossoRepository {
    override suspend fun getSample(category: Int, language: String, topics: List<String>?, exclude: List<String>): Sentence {
        return localDataSource.getSample(category, language, topics, exclude)
    }

    override suspend fun getTopics(category: Int, language: String): List<String> {
        return localDataSource.getTopics(category, language)
    }

    override suspend fun getSentenceCount(category: Int, language: String): Int {
        return localDataSource.getSentenceCount(category, language)
    }

    override suspend fun analyzeSpeech(base64Audio: String, targetText: String, targetIpa: String, mode: String, language: String): PronunciationFeedback {
        throw UnsupportedOperationException("Cloud analysis is disabled. Use on-device recognition.")
    }
}
