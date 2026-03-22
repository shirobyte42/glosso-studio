package me.shirobyte42.glosso.domain.repository

import me.shirobyte42.glosso.domain.model.PronunciationFeedback
import me.shirobyte42.glosso.domain.model.Sentence

interface SpeechController {
    fun startRecording()
    fun stopRecording(): String? // Returns base64 string
    fun recognize(base64Wav: String): String? // Returns IPA string
    fun calculateScore(targetText: String, expectedIpa: String, actualIpa: String): PronunciationFeedback
    fun getNormalizedPhonemes(ipa: String): String // Returns simplified V, B, D etc.
    fun playAudio(base64: String, speed: Float = 1.0f)
    fun playUrl(url: String, speed: Float = 1.0f)
    fun stopPlayback()
}
