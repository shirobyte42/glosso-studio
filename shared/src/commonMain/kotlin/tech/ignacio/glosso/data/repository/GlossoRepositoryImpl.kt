package tech.ignacio.glosso.data.repository

import tech.ignacio.glosso.data.remote.GlossoRemoteDataSource
import tech.ignacio.glosso.domain.model.PronunciationFeedback
import tech.ignacio.glosso.domain.model.Sentence
import tech.ignacio.glosso.domain.repository.GlossoRepository
import tech.ignacio.glosso.domain.repository.LocalSentenceProvider

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
