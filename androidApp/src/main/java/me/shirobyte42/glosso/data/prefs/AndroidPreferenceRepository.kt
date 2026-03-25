package me.shirobyte42.glosso.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.shirobyte42.glosso.domain.repository.PreferenceRepository

import me.shirobyte42.glosso.data.local.MasteredSentenceDao
import me.shirobyte42.glosso.data.local.MasteredSentenceEntity
import me.shirobyte42.glosso.data.local.ActivityDayDao
import me.shirobyte42.glosso.data.local.ActivityDayEntity
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.*

class AndroidPreferenceRepository(
    context: Context,
    private val masteredSentenceDao: MasteredSentenceDao,
    private val activityDayDao: ActivityDayDao
) : PreferenceRepository {
    private val prefs = context.getSharedPreferences("glosso_prefs", Context.MODE_PRIVATE)
    private val _masteryStreakFlow = MutableStateFlow(0)

    init {
        // Initialize flow with current streak from DB
        _masteryStreakFlow.value = getMasteryStreak()
    }

    override fun getSelectedVoice(): Int = prefs.getInt("selected_voice", 0)
    override fun setSelectedVoice(index: Int) {
        prefs.edit().putInt("selected_voice", index).apply()
    }

    override fun getLastLevel(): Int = prefs.getInt("last_level", -1)
    override fun setLastLevel(level: Int) {
        prefs.edit().putInt("last_level", level).apply()
    }

    override fun getMasteryStreakFlow(): Flow<Int> = _masteryStreakFlow.asStateFlow()
    
    override fun getMasteryStreak(): Int = runBlocking {
        calculateCurrentStreak()
    }

    override fun setMasteryStreak(streak: Int) {
        // No-op for DB-based streak, but we can update flow
        _masteryStreakFlow.value = streak
        
        // Update best streak in prefs for efficiency
        if (streak > getBestMasteryStreak()) {
            setBestMasteryStreak(streak)
        }
    }

    override fun getMasteryCombo(): Int = prefs.getInt("mastery_combo", 0)
    override fun setMasteryCombo(combo: Int) {
        prefs.edit().putInt("mastery_combo", combo).apply()
    }

    override fun getBestMasteryStreak(): Int = prefs.getInt("best_mastery_streak", 0)
    override fun setBestMasteryStreak(streak: Int) {
        prefs.edit().putInt("best_mastery_streak", streak).apply()
    }

    override fun isSentenceMastered(text: String): Boolean = runBlocking {
        masteredSentenceDao.isMastered(text)
    }

    override fun markSentenceAsMastered(text: String, category: Int) = runBlocking {
        // Only insert if not already mastered to avoid redundant activity logs
        if (!masteredSentenceDao.isMastered(text)) {
            masteredSentenceDao.insertMasteredSentence(MasteredSentenceEntity(text, category))
            
            // Mark today as active
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val todayStr = sdf.format(Date())
            activityDayDao.insertDay(ActivityDayEntity(todayStr))
            
            // Update flow
            _masteryStreakFlow.value = calculateCurrentStreak()
        }
    }

    override fun getMasteredSentences(): Set<String> = runBlocking {
        masteredSentenceDao.getAllMasteredTexts().toSet()
    }

    override fun getMasteryCountForCategory(category: Int): Int = runBlocking {
        masteredSentenceDao.getCountByLevel(category)
    }

    override fun getTotalMasteryCount(): Int = runBlocking {
        masteredSentenceDao.getTotalCount()
    }

    override fun resetProgress() = runBlocking {
        val editor = prefs.edit()
        editor.clear()
        editor.apply()
        _masteryStreakFlow.value = 0
        masteredSentenceDao.deleteAll()
        activityDayDao.deleteAll()
    }

    override fun isTutorialShown(): Boolean = prefs.getBoolean("tutorial_shown", false)
    override fun setTutorialShown(shown: Boolean) {
        prefs.edit().putBoolean("tutorial_shown", shown).apply()
    }

    private suspend fun calculateCurrentStreak(): Int {
        val dates = activityDayDao.getAllActivityDates()
        if (dates.isEmpty()) return 0

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calendar = Calendar.getInstance()
        
        val todayStr = sdf.format(calendar.time)
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = sdf.format(calendar.time)

        // If today is not in the list AND yesterday is not in the list, streak is 0
        if (!dates.contains(todayStr) && !dates.contains(yesterdayStr)) {
            return 0
        }

        var streak = 0
        val checkCalendar = Calendar.getInstance()
        
        // Start checking from the most recent active day
        if (!dates.contains(todayStr)) {
            checkCalendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        while (true) {
            val checkStr = sdf.format(checkCalendar.time)
            if (dates.contains(checkStr)) {
                streak++
                checkCalendar.add(Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }

        return streak
    }
}
