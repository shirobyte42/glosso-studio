package me.shirobyte42.glosso.domain.usecase

import me.shirobyte42.glosso.domain.repository.PreferenceRepository

class UpdateMasteryUseCase(
    private val prefs: PreferenceRepository
) {
    operator fun invoke(score: Int, sentenceText: String, category: Int): MasteryResult {
        val wasAlreadyMastered = prefs.isSentenceMastered(sentenceText)
        val isScoreMastery = score >= 85
        
        if (isScoreMastery) {
            if (!wasAlreadyMastered) {
                prefs.markSentenceAsMastered(sentenceText, category)
            }
            // Increment combo
            val newCombo = prefs.getMasteryCombo() + 1
            prefs.setMasteryCombo(newCombo)
        } else {
            // Reset combo on failure
            prefs.setMasteryCombo(0)
        }

        val currentCombo = prefs.getMasteryCombo()
        
        return MasteryResult(
            isNewMastery = isScoreMastery && !wasAlreadyMastered,
            currentStreak = currentCombo,
            score = score
        )
    }
}

data class MasteryResult(
    val isNewMastery: Boolean,
    val currentStreak: Int,
    val score: Int
)
