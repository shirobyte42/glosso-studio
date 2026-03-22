package me.shirobyte42.glosso.presentation.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.shirobyte42.glosso.domain.repository.GlossoRepository
import me.shirobyte42.glosso.domain.repository.PreferenceRepository
import me.shirobyte42.glosso.data.local.DatabaseDownloader
import me.shirobyte42.glosso.data.local.DownloadProgress

class HomeViewModel(
    private val repository: GlossoRepository,
    private val prefs: PreferenceRepository,
    private val downloader: DatabaseDownloader
) : ViewModel() {
    private val TAG = "HomeViewModel"

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        Log.d(TAG, "Initializing HomeViewModel")
        checkInitialSetup()
        viewModelScope.launch {
            prefs.getMasteryStreakFlow().collect { streak ->
                Log.d(TAG, "Streak updated from flow: $streak")
                _uiState.update { it.copy(streak = streak) }
            }
        }
    }

    private fun checkInitialSetup() {
        if (!downloader.isModelSetupComplete()) {
            _uiState.update { it.copy(isInitialSetupRequired = true) }
        } else {
            refreshStats()
        }
    }

    fun onLevelClick(levelIndex: Int, onNavigate: (Int) -> Unit) {
        if (!downloader.isModelSetupComplete()) {
            _uiState.update { it.copy(isInitialSetupRequired = true) }
            return
        }
        
        if (downloader.isLevelDownloaded(levelIndex)) {
            onNavigate(levelIndex)
        } else {
            _uiState.update { it.copy(pendingLevelIndex = levelIndex, isDownloadRequired = true) }
        }
    }

    fun startInitialSetup() {
        _uiState.update { it.copy(isDownloading = true, isInitialSetupRequired = false) }
        viewModelScope.launch {
            downloader.downloadRequiredAssets().collect { progress ->
                when (progress) {
                    is DownloadProgress.Progress -> {
                        _uiState.update { it.copy(downloadProgress = progress.percent) }
                    }
                    is DownloadProgress.Success -> {
                        _uiState.update { it.copy(isDownloading = false, downloadProgress = 1f) }
                        refreshStats()
                    }
                    is DownloadProgress.Error -> {
                        _uiState.update { it.copy(isDownloading = false, downloadError = progress.message) }
                    }
                }
            }
        }
    }

    fun startDownload() {
        val levelIndex = _uiState.value.pendingLevelIndex ?: return
        _uiState.update { it.copy(isDownloading = true, isDownloadRequired = false) }
        
        viewModelScope.launch {
            downloader.downloadLevel(levelIndex).collect { progress ->
                when (progress) {
                    is DownloadProgress.Progress -> {
                        _uiState.update { it.copy(downloadProgress = progress.percent) }
                    }
                    is DownloadProgress.Success -> {
                        _uiState.update { it.copy(isDownloading = false, downloadProgress = 1f) }
                        refreshStats()
                    }
                    is DownloadProgress.Error -> {
                        _uiState.update { it.copy(isDownloading = false, downloadError = progress.message) }
                    }
                }
            }
        }
    }

    fun refreshStats() {
        Log.d(TAG, "Refreshing dashboard stats")
        _uiState.update { it.copy(downloadError = null) }
        
        viewModelScope.launch {
            val totalMastery = prefs.getTotalMasteryCount()
            
            val levelStats = (0 until 6).map { levelIndex ->
                val mastered = prefs.getMasteryCountForCategory(levelIndex)
                
                // Only try to get total if downloaded, otherwise use a placeholder or 0
                val total = if (downloader.isLevelDownloaded(levelIndex)) {
                    try {
                        repository.getSentenceCount(levelIndex, "en")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to get count for level $levelIndex - DB might be corrupted. Deleting.", e)
                        downloader.deleteLevel(levelIndex)
                        0
                    }
                } else {
                    0
                }
                
                val safeTotal = if (total <= 0) 1 else total
                LevelStat(
                    mastered = mastered,
                    total = total,
                    progress = if (total > 0) (mastered.toFloat() / total.toFloat()).coerceAtMost(1.0f) else 0f,
                    isDownloaded = downloader.isLevelDownloaded(levelIndex)
                )
            }

            Log.d(TAG, "Stats: totalMastery=$totalMastery, levelStats=$levelStats")

            _uiState.update { it.copy(
                masteryScore = totalMastery,
                levelStats = levelStats,
                levelProgress = levelStats.map { it.progress }
            ) }
        }
    }

    fun resetProgress() {
        Log.w(TAG, "Resetting all user progress")
        prefs.resetProgress()
        refreshStats()
    }
}

data class LevelStat(
    val mastered: Int,
    val total: Int,
    val progress: Float,
    val isDownloaded: Boolean = false
)

data class HomeUiState(
    val masteryScore: Int = 0,
    val streak: Int = 0,
    val levelProgress: List<Float> = List(6) { 0f },
    val levelStats: List<LevelStat> = List(6) { LevelStat(0, 10, 0f) },
    val isLoading: Boolean = false,
    val isInitialSetupRequired: Boolean = false,
    val isDownloadRequired: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadError: String? = null,
    val pendingLevelIndex: Int? = null
)
