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
        // Ensure we handle version names that might already start with 'v' or not
        val rawVersion = packageInfo.versionName
        val version = if (rawVersion.startsWith("v")) rawVersion else "v$rawVersion"
        return "https://gitlab.com/shirobyte421/glosso-studio/-/raw/$version/data"
    }

    private fun getDownloadUrl(fileName: String): String = "${getBaseUrl()}/$fileName"

    fun getDatabaseFile(levelIndex: Int): File = context.getDatabasePath(getDbName(levelIndex))
    
    fun getOnnxFile(): File = File(context.filesDir, ONNX_MODEL_NAME)
    
    fun getPhoneMapFile(): File = File(context.filesDir, PHONE_MAP_NAME)

    fun isLevelDownloaded(levelIndex: Int): Boolean {
        val file = getDatabaseFile(levelIndex)
        return file.exists() && file.length() > 1024 * 1024
    }

    fun isModelSetupComplete(): Boolean {
        val onnxFile = getOnnxFile()
        val phoneFile = getPhoneMapFile()
        // If file is very small, it's likely an LFS pointer, not the model.
        return onnxFile.exists() && onnxFile.length() > 5 * 1024 * 1024 &&
               phoneFile.exists() && phoneFile.length() > 100
    }

    fun deleteLevel(levelIndex: Int) {
        val file = getDatabaseFile(levelIndex)
        if (file.exists()) file.delete()
    }

    fun downloadRequiredAssets(): Flow<DownloadProgress> = callbackFlow {
        try {
            val phoneUrl = getDownloadUrl(PHONE_MAP_NAME)
            val phoneFile = getPhoneMapFile()
            
            if (!phoneFile.exists() || phoneFile.length() < 10) {
                Log.d(TAG, "Downloading phone map from $phoneUrl")
                val response = client.get(phoneUrl)
                if (response.status.isSuccess()) {
                    val bytes = response.body<ByteArray>()
                    withContext(Dispatchers.IO) {
                        phoneFile.writeBytes(bytes)
                    }
                } else {
                    throw Exception("Phone map 404")
                }
            }

            val onnxUrl = getDownloadUrl(ONNX_MODEL_NAME)
            val onnxFile = getOnnxFile()
            
            Log.d(TAG, "Verifying ONNX model at $onnxUrl")
            val headResponse = client.head(onnxUrl)
            val expectedSize = headResponse.contentLength() ?: -1L
            
            if (onnxFile.exists() && expectedSize != -1L && onnxFile.length() != expectedSize) {
                Log.w(TAG, "Size mismatch. Expected $expectedSize, got ${onnxFile.length()}")
                onnxFile.delete()
            }

            if (!onnxFile.exists() || onnxFile.length() < 1024 * 1024) {
                Log.d(TAG, "Starting streaming download for ONNX model...")
                // Use a non-callback approach inside the callbackFlow to ensure sequential execution
                downloadStreamingInternal(onnxUrl, onnxFile) { progress ->
                    trySend(DownloadProgress.Progress(progress))
                }
            }

            Log.d(TAG, "Required assets download complete.")
            trySend(DownloadProgress.Success)
            close()
        } catch (e: Exception) {
            Log.e(TAG, "Asset download failed", e)
            trySend(DownloadProgress.Error(e.message ?: "Setup failed"))
            close(e)
        }
        awaitClose { }
    }

    private suspend fun downloadStreamingInternal(url: String, destination: File, onProgress: (Float) -> Unit) {
        withContext(Dispatchers.IO) {
            client.prepareGet(url) {
                onDownload { bytesSentTotal, contentLength ->
                    if (contentLength > 0) {
                        onProgress(bytesSentTotal.toFloat() / contentLength)
                    }
                }
            }.execute { response ->
                if (response.status.isSuccess()) {
                    val channel = response.bodyAsChannel()
                    FileOutputStream(destination).use { output ->
                        channel.toInputStream().copyTo(output)
                        output.flush()
                        output.getFD().sync()
                    }
                } else {
                    throw Exception("HTTP ${response.status.value}")
                }
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

            if (file.exists() && expectedSize != -1L && file.length() != expectedSize) {
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
            trySend(DownloadProgress.Error(e.message ?: "Download failed"))
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
