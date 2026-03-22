package tech.ignacio.glosso.di

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import tech.ignacio.glosso.data.remote.GlossoRemoteDataSource
import tech.ignacio.glosso.data.repository.GlossoRepositoryImpl
import tech.ignacio.glosso.domain.repository.GlossoRepository
import tech.ignacio.glosso.domain.usecase.UpdateMasteryUseCase

val commonModule = module {
    single {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                    isLenient = true
                })
            }
            install(Logging) {
                // Use a simple logger that prints to console (Logcat on Android)
                logger = object : Logger {
                    override fun log(message: String) {
                        println("HTTP Client: $message")
                    }
                }
                level = LogLevel.ALL
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60000 // 60s
                connectTimeoutMillis = 30000 // 30s
                socketTimeoutMillis = 60000  // 60s
            }
        }
    }
    
    single { GlossoRemoteDataSource(get()) }
    single<GlossoRepository> { GlossoRepositoryImpl(get()) }
    single { UpdateMasteryUseCase(get()) }
}
