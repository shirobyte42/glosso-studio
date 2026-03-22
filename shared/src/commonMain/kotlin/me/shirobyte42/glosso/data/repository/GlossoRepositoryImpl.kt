package me.shirobyte42.glosso.data.repository

import me.shirobyte42.glosso.data.remote.GlossoRemoteDataSource
import me.shirobyte42.glosso.domain.model.PronunciationFeedback
import me.shirobyte42.glosso.domain.model.Sentence
import me.shirobyte42.glosso.domain.repository.GlossoRepository
import me.shirobyte42.glosso.domain.repository.LocalSentenceProvider

class GlossoRepositoryImpl(
    private val remoteDataSource: GlossoRemoteDataSource,
    private val localDataSource: LocalSentenceProvider? = null
) : GlossoRepository {
    override suspend fun getSample(category: Int, language: String, topics: List<String>?, exclude: List<String>): Sentence {
        if (localDataSource != null) {
            try {
                return localDataSource.getSample(category, language, topics, exclude)
            } catch (e: Exception) {
                // Fallback to remote if local fails or is empty
            }
        }
        return remoteDataSource.fetchSample(category, language, topics, exclude)
    }

    override suspend fun getTopics(category: Int, language: String): List<String> {
        return localDataSource?.getTopics(category, language) ?: emptyList()
    }

    override suspend fun getSentenceCount(category: Int, language: String): Int {
        return localDataSource?.getSentenceCount(category, language) ?: 0
    }

    override suspend fun analyzeSpeech(base64Audio: String, targetText: String, targetIpa: String, mode: String, language: String): PronunciationFeedback {
        return remoteDataSource.analyze(base64Audio, targetText, targetIpa, mode, language)
    }
}
