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
    private val ONNX_MODEL_NAME = "allosaurus_eng2102.onnx"
    private val PHONE_MAP_NAME = "phone_eng.txt"

    private fun getDbName(levelIndex: Int) = "sentences_$levelIndex.db"
    
    private fun getBaseUrl(): String {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName
        return "https://gitlab.com/shirobyte421/glosso-studio/-/raw/v$versionName/data"
    }

    private fun getDownloadUrl(fileName: String): String = "${getBaseUrl()}/$fileName"

    fun getDatabaseFile(levelIndex: Int): File = context.getDatabasePath(getDbName(levelIndex))
    
    fun getOnnxFile(): File = File(context.filesDir, ONNX_MODEL_NAME)
    
    fun getPhoneMapFile(): File = File(context.filesDir, PHONE_MAP_NAME)

    fun isLevelDownloaded(levelIndex: Int): Boolean {
        val file = getDatabaseFile(levelIndex)
        return file.exists() && file.length() > 40 * 1024 * 1024
    }

    fun isModelSetupComplete(): Boolean {
        return getOnnxFile().exists() && getOnnxFile().length() > 40 * 1024 * 1024 &&
               getPhoneMapFile().exists() && getPhoneMapFile().length() > 100
    }

    fun deleteLevel(levelIndex: Int) {
        val file = getDatabaseFile(levelIndex)
        if (file.exists()) file.delete()
    }

    fun downloadRequiredAssets(): Flow<DownloadProgress> = callbackFlow {
        try {
            // 1. Download Phone Map (tiny)
            if (!getPhoneMapFile().exists()) {
                downloadFile(getDownloadUrl(PHONE_MAP_NAME), getPhoneMapFile())
            }

            // 2. Download ONNX Model (approx 43MB)
            if (!getOnnxFile().exists() || getOnnxFile().length() < 40 * 1024 * 1024) {
                downloadStreaming(getDownloadUrl(ONNX_MODEL_NAME), getOnnxFile()) { progress ->
                    trySend(DownloadProgress.Progress(progress))
                }
            }

            trySend(DownloadProgress.Success)
            close()
        } catch (e: Exception) {
            trySend(DownloadProgress.Error(e.message ?: "Asset download failed"))
            close(e)
        }
        awaitClose { }
    }

    private suspend fun downloadFile(url: String, destination: File) {
        val bytes = client.get(url).bodyAsBytes()
        withContext(Dispatchers.IO) {
            destination.writeBytes(bytes)
        }
    }

    private suspend fun downloadStreaming(url: String, destination: File, onProgress: (Float) -> Unit) {
        client.prepareGet(url) {
            onDownload { bytesSentTotal, contentLength ->
                if (contentLength != null && contentLength > 0) {
                    onProgress(bytesSentTotal.toFloat() / contentLength)
                }
            }
        }.execute { response ->
            if (response.status.isSuccess()) {
                val channel = response.bodyAsChannel()
                withContext(Dispatchers.IO) {
                    FileOutputStream(destination).use { output ->
                        channel.toInputStream().copyTo(output)
                        output.flush()
                        output.getFD().sync()
                    }
                }
            } else {
                throw Exception("Failed to download: ${response.status}")
            }
        }
    }

    fun downloadLevel(levelIndex: Int): Flow<DownloadProgress> = callbackFlow {
        val file = getDatabaseFile(levelIndex)
        file.parentFile?.mkdirs()
        val url = getDownloadUrl(getDbName(levelIndex))

        try {
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

            downloadStreaming(url, file) { progress ->
                trySend(DownloadProgress.Progress(progress))
            }
            trySend(DownloadProgress.Success)
            close()
        } catch (e: Exception) {
            trySend(DownloadProgress.Error(e.message ?: "Level download failed"))
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
