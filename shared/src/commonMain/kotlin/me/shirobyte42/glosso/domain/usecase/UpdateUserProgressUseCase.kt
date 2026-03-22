package me.shirobyte42.glosso.domain.usecase

import me.shirobyte42.glosso.domain.repository.PreferenceRepository

class UpdateMasteryUseCase(
    private val prefs: PreferenceRepository
) {
    operator fun invoke(score: Int, sentenceText: String, category: Int): MasteryResult {
        val wasAlreadyMastered = prefs.isSentenceMastered(sentenceText)
        val isScoreMastery = score >= 85
        
        if (isScoreMastery && !wasAlreadyMastered) {
            prefs.markSentenceAsMastered(sentenceText, category)
        }

        val currentStreak = prefs.getMasteryStreak()
        
        return MasteryResult(
            isNewMastery = isScoreMastery && !wasAlreadyMastered,
            currentStreak = currentStreak,
            score = score
        )
    }
}

data class MasteryResult(
    val isNewMastery: Boolean,
    val currentStreak: Int,
    val score: Int
)
