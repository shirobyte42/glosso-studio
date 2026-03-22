package me.shirobyte42.glosso.domain.repository

import kotlinx.coroutines.flow.Flow

interface PreferenceRepository {
    fun getSelectedVoice(): Int
    fun setSelectedVoice(index: Int)
    
    fun getLastLevel(): Int
    fun setLastLevel(level: Int)
    
    // Professional Progress
    fun getMasteryStreakFlow(): Flow<Int>
    fun getMasteryStreak(): Int
    fun setMasteryStreak(streak: Int)
    
    fun getBestMasteryStreak(): Int
    fun setBestMasteryStreak(streak: Int)

    // Mastered Sentences Tracking
    fun isSentenceMastered(text: String): Boolean
    fun markSentenceAsMastered(text: String, category: Int)
    fun getMasteredSentences(): Set<String>
    fun getMasteryCountForCategory(category: Int): Int
    fun getTotalMasteryCount(): Int
    fun resetProgress()
}
