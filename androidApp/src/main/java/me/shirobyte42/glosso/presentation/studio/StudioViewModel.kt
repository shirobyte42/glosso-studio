package me.shirobyte42.glosso.presentation.studio

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.shirobyte42.glosso.domain.model.PronunciationFeedback
import me.shirobyte42.glosso.domain.model.Sentence
import me.shirobyte42.glosso.domain.repository.GlossoRepository
import me.shirobyte42.glosso.domain.repository.PreferenceRepository
import me.shirobyte42.glosso.domain.repository.SpeechController
import me.shirobyte42.glosso.domain.usecase.UpdateMasteryUseCase
import me.shirobyte42.glosso.data.audio.AndroidSpeechController
import me.shirobyte42.glosso.data.local.SentenceDao
import java.io.File

class StudioViewModel(
    private val repository: GlossoRepository,
    private val sentenceDao: SentenceDao,
    private val speechController: SpeechController,
    private val prefs: PreferenceRepository,
    private val updateMastery: UpdateMasteryUseCase
) : ViewModel() {
    private val TAG = "StudioViewModel"

    private val _uiState = MutableStateFlow(StudioUiState(
        selectedVoiceIndex = prefs.getSelectedVoice(),
        currentStreak = prefs.getMasteryStreak()
    ))
    val uiState: StateFlow<StudioUiState> = _uiState

    private var lastAudioBase64: String? = null
    
    fun loadTopics(levelIndex: Int) {
        viewModelScope.launch {
            try {
                val topics = repository.getTopics(levelIndex, "en")
                _uiState.update { it.copy(topics = topics) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load topics", e)
            }
        }
    }

    fun toggleTopic(topic: String) {
        _uiState.update { state ->
            val current = state.selectedTopics
            if (current.contains(topic)) {
                state.copy(selectedTopics = current - topic)
            } else {
                state.copy(selectedTopics = current + topic)
            }
        }
    }

    fun setTopics(levelIndex: Int, topics: List<String>) {
        _uiState.update { it.copy(selectedTopics = topics) }
        loadSample(levelIndex)
    }

    fun loadSample(levelIndex: Int) {
        Log.d(TAG, "Loading sample for level $levelIndex with topics ${_uiState.value.selectedTopics}")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, feedback = null, isMastered = false, hasRecordedVoice = false) }
            prefs.setLastLevel(levelIndex)
            lastAudioBase64 = null
            
            try {
                val currentTopics = _uiState.value.selectedTopics
                val masteredSentences = prefs.getMasteredSentences().toList()
                
                val sentence = repository.getSample(
                    category = levelIndex,
                    language = "en",
                    topics = if (currentTopics.isEmpty()) null else currentTopics,
                    exclude = masteredSentences
                )

                _uiState.update { it.copy(
                    isLoading = false,
                    currentSentence = sentence,
                    isMastered = prefs.isSentenceMastered(sentence.text)
                ) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load sample", e)
                _uiState.update { it.copy(isLoading = false, error = "Failed to load: ${e.message}") }
            }
        }
    }

    fun setVoiceIndex(index: Int, autoPlay: Boolean = true) {
        _uiState.update { it.copy(selectedVoiceIndex = index) }
        prefs.setSelectedVoice(index)
        if (autoPlay) playReference()
    }

    fun toggleRecording() {
        if (_uiState.value.isRecording) {
            try {
                val base64 = speechController.stopRecording()
                _uiState.update { it.copy(isRecording = false) }
                if (base64 != null) {
                    lastAudioBase64 = base64
                    _uiState.update { it.copy(hasRecordedVoice = true) }
                    analyzeSpeech(base64)
                } else {
                    _uiState.update { it.copy(error = "Recording failed: audio is empty.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRecording = false, error = "Stop failed: ${e.message}") }
            }
        } else {
            try {
                speechController.startRecording()
                _uiState.update { it.copy(isRecording = true, error = null, feedback = null, hasRecordedVoice = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRecording = false, error = "Start failed: ${e.message}") }
            }
        }
    }

    private fun analyzeSpeech(base64Audio: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true, error = null) }
            try {
                val sentence = _uiState.value.currentSentence ?: return@launch
                val startTime = System.currentTimeMillis()
                
                val feedback = withContext(Dispatchers.Default) {
                    Log.d(TAG, "Attempting on-device recognition...")
                    val recognizedIpa = speechController.recognize(base64Audio)
                    
                    if (recognizedIpa != null) {
                        Log.d(TAG, "On-device recognition success: $recognizedIpa")
                        val result = speechController.calculateScore(sentence.text, sentence.ipa, recognizedIpa)
                        Log.d(TAG, "On-device scoring result: ${result.score}")
                        result
                    } else {
                        Log.e(TAG, "On-device recognition failed.")
                        throw Exception("On-device recognition failed. Please ensure setup is complete.")
                    }
                }
                
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed < 300) delay(300 - elapsed)
                
                val levelIndex = prefs.getLastLevel()
                val result = updateMastery(feedback.score, sentence.text, levelIndex)
                
                _uiState.update { it.copy(
                    isAnalyzing = false, 
                    feedback = feedback,
                    isMastered = result.isNewMastery || _uiState.value.isMastered,
                    currentStreak = result.currentStreak
                ) }
            } catch (e: Exception) {
                Log.e(TAG, "Analysis failed", e)
                _uiState.update { it.copy(isAnalyzing = false, error = "Analysis failed.") }
            }
        }
    }

    fun playReference() {
        val sentence = _uiState.value.currentSentence ?: return
        val voiceIndex = _uiState.value.selectedVoiceIndex
        val audio = if (voiceIndex == 0) sentence.audio1 else sentence.audio2 ?: sentence.audio1
        audio?.let {
            speechController.playAudio(it, 1.0f)
        }
    }

    fun playRecordedVoice() {
        lastAudioBase64?.let {
            speechController.playAudio(it, 1.0f)
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechController.stopPlayback()
    }
}

data class StudioUiState(
    val currentSentence: Sentence? = null,
    val isLoading: Boolean = false,
    val isRecording: Boolean = false,
    val isAnalyzing: Boolean = false,
    val feedback: PronunciationFeedback? = null,
    val selectedVoiceIndex: Int = 0,
    val isMastered: Boolean = false,
    val currentStreak: Int = 0,
    val topics: List<String> = emptyList(),
    val selectedTopics: List<String> = emptyList(),
    val hasRecordedVoice: Boolean = false,
    val error: String? = null
)
