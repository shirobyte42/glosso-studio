package me.shirobyte42.glosso.di

import org.koin.androidx.viewmodel.dsl.viewModel
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
import androidx.room.Room
import me.shirobyte42.glosso.data.repository.GlossoRepositoryImpl
import me.shirobyte42.glosso.domain.repository.GlossoRepository

val appModule = module {
    single { 
        Room.databaseBuilder(
            get(),
            GlossoDatabase::class.java,
            GlossoDatabase.DATABASE_NAME
        )
        .createFromAsset("sentences.db")
        .fallbackToDestructiveMigration()
        .build()
    }
    
    single { get<GlossoDatabase>().sentenceDao }
    single { get<GlossoDatabase>().masteredSentenceDao }
    single { get<GlossoDatabase>().activityDayDao }
    
    single { AllosaurusRecognizer(get()) }
    single<PreferenceRepository> { AndroidPreferenceRepository(get(), get(), get()) }
    single<SpeechController> { AndroidSpeechController(get(), get()) }
    
    single { LocalSentenceDataSource(get(), get()) }
    // Override the common repository with one that has the local data source
    single<GlossoRepository> { GlossoRepositoryImpl(get(), get<LocalSentenceDataSource>()) }
    
    viewModel { HomeViewModel(get(), get()) }
    viewModel { 

        StudioViewModel(
            repository = get(),
            speechController = get(),
            prefs = get(),
            updateMastery = get()
        ) 
    }
}
