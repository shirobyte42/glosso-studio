package me.shirobyte42.glosso.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.shirobyte42.glosso.domain.model.Sentence
import me.shirobyte42.glosso.domain.repository.LocalSentenceProvider

class LocalSentenceDataSource(
    private val context: Context,
    private val sentenceDaoProvider: (Int) -> SentenceDao
) : LocalSentenceProvider {

    override suspend fun getTopics(category: Int, language: String): List<String> = withContext(Dispatchers.IO) {
        val levelKey = getLevelKey(category)
        sentenceDaoProvider(category).getTopicsByLevel(levelKey, language)
    }

    override suspend fun getSentenceCount(category: Int, language: String): Int = withContext(Dispatchers.IO) {
        val levelKey = getLevelKey(category)
        sentenceDaoProvider(category).getSentenceCountByLevel(levelKey, language)
    }

    override suspend fun getSample(category: Int, language: String, topics: List<String>?, exclude: List<String>): Sentence = withContext(Dispatchers.IO) {
        val levelKey = getLevelKey(category)
        val dao = sentenceDaoProvider(category)
        
        // Ensure exclude list is not empty for IN clause by adding an impossible value if needed
        val excludeList = if (exclude.isEmpty()) listOf("---") else exclude

        // Try to get from DB
        val entity = when {
            topics != null && topics.isNotEmpty() -> {
                dao.getRandomSentenceByLevelAndTopics(levelKey, topics, language, excludeList)
            }
            else -> {
                dao.getRandomSentenceByLevel(levelKey, language, excludeList)
            }
        }
        
        if (entity != null) {
            return@withContext entity.toDomain()
        }

        throw Exception("No sentences found for level $levelKey ${if (topics != null) "topics $topics" else ""} in $language")
    }

    private fun getLevelKey(category: Int): String {
        return when(category) {
            0 -> "A1"
            1 -> "A2"
            2 -> "B1"
            3 -> "B2"
            4 -> "C1"
            5 -> "C2"
            else -> "A1"
        }
    }
}
