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
    private fun getDbName(levelIndex: Int) = "sentences_$levelIndex.db"
    
    private fun getDownloadUrl(levelIndex: Int): String {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName
        return "https://github.com/shirobyte42/glosso-studio/releases/download/v$versionName/${getDbName(levelIndex)}"
    }

    fun getDatabaseFile(levelIndex: Int): File {
        return context.getDatabasePath(getDbName(levelIndex))
    }

    fun isLevelDownloaded(levelIndex: Int): Boolean {
        val file = getDatabaseFile(levelIndex)
        // Corrected heuristic: sentences_0.db is ~48MB. 
        // 1MB was too low and might have allowed corrupted files to pass.
        return file.exists() && file.length() > 40 * 1024 * 1024
    }

    fun deleteLevel(levelIndex: Int) {
        val file = getDatabaseFile(levelIndex)
        if (file.exists()) {
            file.delete()
        }
    }

    fun downloadLevel(levelIndex: Int): Flow<DownloadProgress> = callbackFlow {
        val file = getDatabaseFile(levelIndex)
        file.parentFile?.mkdirs()
        val url = getDownloadUrl(levelIndex)

        try {
            // 1. Verify size
            val headResponse = client.head(url)
            val expectedSize = headResponse.contentLength() ?: -1L

            if (file.exists() && expectedSize != -1L && file.length() == expectedSize) {
                trySend(DownloadProgress.Success)
                close()
                return@callbackFlow
            }

            if (file.exists() && expectedSize != -1L && file.length() != expectedSize) {
                file.delete()
            }

            // 2. Download
            client.prepareGet(url) {
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
                            output.flush()
                            output.getFD().sync() // Force write to physical storage
                        }
                    }
                    trySend(DownloadProgress.Success)
                    close()
                } else {
                    trySend(DownloadProgress.Error("Failed to download level $levelIndex: ${response.status}"))
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
