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

    /**
     * Checks if the database is already fully downloaded.
     * Note: This is synchronous and only checks local state. 
     * Use [verifyAndDownload] for a more robust check.
     */
    fun isDatabaseDownloaded(): Boolean {
        val file = getDatabaseFile()
        // Simple check: exists and is large enough to be the real DB (not an error page)
        // The sentences.db v0.1.0 is ~542MB.
        return file.exists() && file.length() > 500 * 1024 * 1024
    }

    fun downloadDatabase(): Flow<DownloadProgress> = callbackFlow {
        val file = getDatabaseFile()
        file.parentFile?.mkdirs()

        try {
            // 1. Check file size on server to verify completeness
            val headResponse = client.head(DOWNLOAD_URL)
            val expectedSize = headResponse.contentLength() ?: -1L

            if (file.exists() && expectedSize != -1L && file.length() == expectedSize) {
                trySend(DownloadProgress.Success)
                close()
                return@callbackFlow
            }

            // 2. Start/Restart download if size mismatch or file missing
            // If it exists but is smaller, we'll overwrite it for now to ensure integrity.
            // Future improvement: implement Range-based resume.
            if (file.exists() && file.length() != expectedSize) {
                file.delete()
            }

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
