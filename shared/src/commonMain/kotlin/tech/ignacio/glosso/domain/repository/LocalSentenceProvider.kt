package tech.ignacio.glosso.domain.repository

import tech.ignacio.glosso.domain.model.Sentence

interface LocalSentenceProvider {
    suspend fun getSample(category: Int, language: String, topics: List<String>? = null, exclude: List<String> = emptyList()): Sentence
    suspend fun getTopics(category: Int, language: String): List<String>
    suspend fun getSentenceCount(category: Int, language: String): Int
}
