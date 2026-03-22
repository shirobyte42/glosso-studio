package me.shirobyte42.glosso.data.local

import android.content.Context
import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
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
    private val TAG = "DatabaseDownloader"
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
        // Heuristic: most level DBs are > 40MB
        return file.exists() && file.length() > 5 * 1024 * 1024
    }

    fun isModelSetupComplete(): Boolean {
        val onnxFile = getOnnxFile()
        val phoneFile = getPhoneMapFile()
        // The model is ~43MB. If it's smaller than 10MB, it's definitely corrupted/pointer.
        return onnxFile.exists() && onnxFile.length() > 10 * 1024 * 1024 &&
               phoneFile.exists() && phoneFile.length() > 100
    }

    fun deleteLevel(levelIndex: Int) {
        val file = getDatabaseFile(levelIndex)
        if (file.exists()) file.delete()
    }

    fun downloadRequiredAssets(): Flow<DownloadProgress> = callbackFlow {
        try {
            // 1. Download Phone Map (tiny)
            val phoneUrl = getDownloadUrl(PHONE_MAP_NAME)
            val phoneFile = getPhoneMapFile()
            
            if (!phoneFile.exists() || phoneFile.length() < 10) {
                Log.d(TAG, "Downloading phone map...")
                val response = client.get(phoneUrl)
                if (response.status.isSuccess()) {
                    val bytes = response.body<ByteArray>()
                    withContext(Dispatchers.IO) {
                        phoneFile.writeBytes(bytes)
                    }
                }
            }

            // 2. Download ONNX Model
            val onnxUrl = getDownloadUrl(ONNX_MODEL_NAME)
            val onnxFile = getOnnxFile()
            
            Log.d(TAG, "Verifying ONNX model at $onnxUrl")
            val headResponse = client.head(onnxUrl)
            val expectedSize = headResponse.contentLength() ?: -1L
            Log.d(TAG, "Server reports size: $expectedSize, local size: ${onnxFile.length()}")
            
            // If local file exists but size doesn't match server, OR it's suspiciously small (< 10MB)
            val isCorrupted = onnxFile.exists() && (
                (expectedSize != -1L && onnxFile.length() != expectedSize) || 
                (onnxFile.length() < 10 * 1024 * 1024)
            )

            if (isCorrupted) {
                Log.w(TAG, "Local model file is corrupted or incomplete. Deleting and restarting.")
                onnxFile.delete()
            }

            if (!onnxFile.exists() || onnxFile.length() == 0L) {
                Log.d(TAG, "Starting streaming download for ONNX model...")
                downloadStreamingInternal(onnxUrl, onnxFile) { progress ->
                    trySend(DownloadProgress.Progress(progress))
                }
            }

            trySend(DownloadProgress.Success)
            close()
        } catch (e: Exception) {
            Log.e(TAG, "Asset download failed", e)
            trySend(DownloadProgress.Error(e.message ?: "Asset download failed"))
            close(e)
        }
        awaitClose { }
    }

    private suspend fun downloadStreamingInternal(url: String, destination: File, onProgress: (Float) -> Unit) {
        client.prepareGet(url) {
            onDownload { bytesSentTotal, contentLength ->
                if (contentLength > 0) {
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
                throw Exception("HTTP ${response.status.value}: Failed to download $url")
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

            val isCorrupted = file.exists() && (
                (expectedSize != -1L && file.length() != expectedSize) || 
                (file.length() < 1024 * 1024)
            )

            if (isCorrupted) {
                file.delete()
            }

            if (!file.exists() || file.length() == 0L) {
                downloadStreamingInternal(url, file) { progress ->
                    trySend(DownloadProgress.Progress(progress))
                }
            }
            
            trySend(DownloadProgress.Success)
            close()
        } catch (e: Exception) {
            Log.e(TAG, "Level download failed", e)
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
