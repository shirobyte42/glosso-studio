package tech.ignacio.glosso.di

import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import tech.ignacio.glosso.data.audio.AllosaurusRecognizer
import tech.ignacio.glosso.data.audio.AndroidSpeechController
import tech.ignacio.glosso.data.prefs.AndroidPreferenceRepository
import tech.ignacio.glosso.domain.repository.PreferenceRepository
import tech.ignacio.glosso.domain.repository.SpeechController
import tech.ignacio.glosso.presentation.home.HomeViewModel
import tech.ignacio.glosso.presentation.studio.StudioViewModel

import tech.ignacio.glosso.data.local.LocalSentenceDataSource
import tech.ignacio.glosso.data.local.GlossoDatabase
import androidx.room.Room
import tech.ignacio.glosso.data.repository.GlossoRepositoryImpl
import tech.ignacio.glosso.domain.repository.GlossoRepository

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
