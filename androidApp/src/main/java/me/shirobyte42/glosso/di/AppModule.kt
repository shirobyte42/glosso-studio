package me.shirobyte42.glosso.di

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import me.shirobyte42.glosso.data.audio.AllosaurusRecognizer
import me.shirobyte42.glosso.data.audio.AndroidSpeechController
import me.shirobyte42.glosso.data.prefs.AndroidPreferenceRepository
import me.shirobyte42.glosso.domain.repository.PreferenceRepository
import me.shirobyte42.glosso.domain.repository.SpeechController
import me.shirobyte42.glosso.presentation.home.HomeViewModel
import me.shirobyte42.glosso.presentation.studio.StudioViewModel

import me.shirobyte42.glosso.data.local.LocalSentenceDataSource
import me.shirobyte42.glosso.data.local.GlossoDatabase
import me.shirobyte42.glosso.data.local.DatabaseDownloader
import me.shirobyte42.glosso.data.local.SentenceDao
import androidx.room.Room
import me.shirobyte42.glosso.data.repository.GlossoRepositoryImpl
import me.shirobyte42.glosso.domain.repository.GlossoRepository

val appModule = module {
    single { DatabaseDownloader(get(), get()) }

    // Persistent database for user progress (streaks, activity, mastered IDs)
    single(qualifier = org.koin.core.qualifier.named("progress_db")) {
        Room.databaseBuilder(
            get(),
            GlossoDatabase::class.java,
            GlossoDatabase.PROGRESS_DATABASE_NAME
        )
        .fallbackToDestructiveMigration()
        .build()
    }
    
    // Factory for dynamic sentence databases
    factory(qualifier = org.koin.core.qualifier.named("level_db")) { (levelIndex: Int) ->
        Room.databaseBuilder(
            get(),
            GlossoDatabase::class.java,
            GlossoDatabase.getDatabaseName(levelIndex)
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    single { get<GlossoDatabase>(qualifier = org.koin.core.qualifier.named("progress_db")).masteredSentenceDao }
    single { get<GlossoDatabase>(qualifier = org.koin.core.qualifier.named("progress_db")).activityDayDao }
    
    single { AllosaurusRecognizer(get()) }
    single<PreferenceRepository> { AndroidPreferenceRepository(get(), get(), get()) }
    single<SpeechController> { AndroidSpeechController(get(), get()) }

    single { 
        LocalSentenceDataSource(get()) { levelIndex ->
            val db: GlossoDatabase = get(qualifier = org.koin.core.qualifier.named("level_db")) { parametersOf(levelIndex) }
            db.sentenceDao
        }
    }
    // Override the common repository with one that has the local data source
    single<GlossoRepository> { GlossoRepositoryImpl(get(), get<LocalSentenceDataSource>()) }

    
    viewModel { HomeViewModel(get(), get(), get()) }
    viewModel { (levelIndex: Int) ->
        // Use the factory to get the database for the specific level
        val levelDb: GlossoDatabase = get(qualifier = org.koin.core.qualifier.named("level_db")) { parametersOf(levelIndex) }
        
        StudioViewModel(
            repository = get(),
            sentenceDao = levelDb.sentenceDao,
            speechController = get(),
            prefs = get(),
            updateMastery = get()
        ) 
    }
}
