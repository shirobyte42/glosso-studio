package me.shirobyte42.glosso.data.local

import android.content.Context
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class DatabaseDownloader(
    private val context: Context,
    private val client: HttpClient
) {
    private val DB_NAME = "sentences.db"
    
    private val DOWNLOAD_URL: String by lazy {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName
        "https://github.com/shirobyte42/glosso-studio/releases/download/v$versionName/sentences.db"
    }

    fun getDatabaseFile(): File {
        return context.getDatabasePath(DB_NAME)
    }

    fun isDatabaseDownloaded(): Boolean {
        val file = getDatabaseFile()
        return file.exists() && file.length() > 0
    }

    fun downloadDatabase(): Flow<DownloadProgress> = callbackFlow {
        val file = getDatabaseFile()
        file.parentFile?.mkdirs()

        try {
            // Using prepareGet ensures we don't buffer the 500MB into memory before processing.
            // This is a streaming approach that prevents OOM (SIG 9) crashes.
            client.prepareGet(DOWNLOAD_URL) {
                onDownload { bytesSentTotal, contentLength ->
                    if (contentLength != null && contentLength > 0) {
                        trySend(DownloadProgress.Progress(bytesSentTotal.toFloat() / contentLength))
                    }
                }
            }.execute { response ->
                if (response.status.isSuccess()) {
                    val channel = response.bodyAsChannel()
                    
                    withContext(Dispatchers.IO) {
                        FileOutputStream(file).use { output ->
                            // Use Ktor's streaming channel to Java output stream
                            channel.toInputStream().copyTo(output)
                        }
                    }
                    trySend(DownloadProgress.Success)
                    close()
                } else {
                    trySend(DownloadProgress.Error("Failed to download: ${response.status}"))
                    close()
                }
            }
        } catch (e: Exception) {
            trySend(DownloadProgress.Error(e.message ?: "Unknown error"))
            close(e)
        }
        
        awaitClose { }
    }
}

sealed class DownloadProgress {
    data class Progress(val percent: Float) : DownloadProgress()
    object Success : DownloadProgress()
    data class Error(val message: String) : DownloadProgress()
}
