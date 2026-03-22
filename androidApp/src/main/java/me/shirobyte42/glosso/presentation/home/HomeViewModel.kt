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
        checkDatabaseAndRefresh()
        viewModelScope.launch {
            prefs.getMasteryStreakFlow().collect { streak ->
                Log.d(TAG, "Streak updated from flow: $streak")
                _uiState.update { it.copy(streak = streak) }
            }
        }
    }

    private fun checkDatabaseAndRefresh() {
        if (downloader.isDatabaseDownloaded()) {
            refreshStats()
        } else {
            _uiState.update { it.copy(isDownloading = true) }
            startDownload()
        }
    }

    private fun startDownload() {
        viewModelScope.launch {
            downloader.downloadDatabase().collect { progress ->
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
        
        if (!downloader.isDatabaseDownloaded()) {
            checkDatabaseAndRefresh()
            return
        }

        viewModelScope.launch {
            val totalMastery = prefs.getTotalMasteryCount()
            
            val levelStats = (0 until 6).map { levelIndex ->
                val mastered = prefs.getMasteryCountForCategory(levelIndex)
                val total = try {
                    repository.getSentenceCount(levelIndex, "en")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get count for level $levelIndex", e)
                    0
                }
                val safeTotal = if (total <= 0) 1 else total
                LevelStat(
                    mastered = mastered,
                    total = total,
                    progress = (mastered.toFloat() / safeTotal.toFloat()).coerceAtMost(1.0f)
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
    val progress: Float
)

data class HomeUiState(
    val masteryScore: Int = 0,
    val streak: Int = 0,
    val levelProgress: List<Float> = List(6) { 0f },
    val levelStats: List<LevelStat> = List(6) { LevelStat(0, 10, 0f) },
    val isLoading: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadError: String? = null
)
