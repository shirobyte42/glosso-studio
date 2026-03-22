package me.shirobyte42.glosso.data.local

import android.content.Context
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

class DatabaseDownloader(
    private val context: Context,
    private val client: HttpClient
) {
    private val DB_NAME = "sentences.db"
    
    // Dynamically build the URL based on the version name. 
    // This allows the app to always download the database associated with its release.
    private val DOWNLOAD_URL: String by lazy {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName
        "https://github.com/shirobyte42/glosso-studio/releases/download/v$versionName/sentences.db"
    }

    fun getDatabaseFile(): File {
        return context.getDatabasePath(DB_NAME)
    }

    fun isDatabaseDownloaded(): Boolean {
        return getDatabaseFile().exists() && getDatabaseFile().length() > 0
    }

    fun downloadDatabase(): Flow<DownloadProgress> = flow {
        val file = getDatabaseFile()
        file.parentFile?.mkdirs()

        try {
            val response = client.get(DOWNLOAD_URL) {
                onDownload { bytesSentTotal, contentLength ->
                    if (contentLength > 0) {
                        emit(DownloadProgress.Progress(bytesSentTotal.toFloat() / contentLength))
                    }
                }
            }

            if (response.status.isSuccess()) {
                val channel = response.bodyAsChannel()
                file.writeChannel().use { it.writeFrom(channel) }
                emit(DownloadProgress.Success)
            } else {
                emit(DownloadProgress.Error("Failed to download: ${response.status}"))
            }
        } catch (e: Exception) {
            emit(DownloadProgress.Error(e.message ?: "Unknown error"))
        }
    }
}

sealed class DownloadProgress {
    data class Progress(val percent: Float) : DownloadProgress()
    object Success : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}
