package me.shirobyte42.glosso.di

import me.shirobyte42.glosso.data.repository.GlossoRepositoryImpl
import me.shirobyte42.glosso.domain.repository.GlossoRepository
import me.shirobyte42.glosso.domain.usecase.UpdateMasteryUseCase
import org.koin.dsl.module

val commonModule = module {
    single<GlossoRepository> { GlossoRepositoryImpl(get()) }
    single { UpdateMasteryUseCase(get()) }
}
