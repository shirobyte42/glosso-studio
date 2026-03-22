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
import java.security.MessageDigest

class DatabaseDownloader(
    private val context: Context,
    private val client: HttpClient
) {
    private val TAG = "DatabaseDownloader"
    private val ONNX_MODEL_NAME = "allosaurus_eng2102.onnx"
    private val PHONE_MAP_NAME = "phone_eng.txt"
    
    // Hardcoded SHA-256 hashes for verification
    private val ONNX_HASH = "a792590100c9ca5ea4e8f04a71de884f9fe9fb9cdaa17a528acb369f56028a07"
    private val PHONE_HASH = "883efb706711ccbf72df2522c765f3012f170cd787bca519a1b4f68bf9aa0f47"

    private fun getDbName(levelIndex: Int) = "sentences_$levelIndex.db"
    
    private fun getBaseUrl(): String {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
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
        
        // Use SHA-256 verification if files exist
        if (!onnxFile.exists() || !phoneFile.exists()) return false
        
        return verifyHash(onnxFile, ONNX_HASH) && verifyHash(phoneFile, PHONE_HASH)
    }

    private fun verifyHash(file: File, expectedHash: String): Boolean {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = file.readBytes()
            val hash = digest.digest(bytes).joinToString("") { "%02x".format(it) }
            val match = hash == expectedHash
            if (!match) Log.e(TAG, "Hash mismatch for ${file.name}! Expected $expectedHash, got $hash")
            return match
        } catch (e: Exception) {
            return false
        }
    }

    fun deleteLevel(levelIndex: Int) {
        val file = getDatabaseFile(levelIndex)
        if (file.exists()) file.delete()
    }

    fun downloadRequiredAssets(): Flow<DownloadProgress> = callbackFlow {
        try {
            // 1. Download Phone Map
            val phoneFile = getPhoneMapFile()
            if (!phoneFile.exists() || !verifyHash(phoneFile, PHONE_HASH)) {
                Log.d(TAG, "Downloading/Repairing phone map...")
                try {
                    val response = client.get(getDownloadUrl(PHONE_MAP_NAME))
                    val bytes = response.body<ByteArray>()
                    withContext(Dispatchers.IO) { phoneFile.writeBytes(bytes) }
                    
                    if (!verifyHash(phoneFile, PHONE_HASH)) {
                        phoneFile.delete()
                        throw Exception("Phone map verification failed!")
                    }
                } catch (e: Exception) {
                    if (phoneFile.exists()) phoneFile.delete()
                    throw e
                }
            }

            // 2. Download ONNX Model
            val onnxFile = getOnnxFile()
            if (!onnxFile.exists() || !verifyHash(onnxFile, ONNX_HASH)) {
                Log.d(TAG, "Downloading/Repairing ONNX model...")
                downloadStreamingInternal(getDownloadUrl(ONNX_MODEL_NAME), onnxFile) { progress ->
                    trySend(DownloadProgress.Progress(progress))
                }
                
                if (!verifyHash(onnxFile, ONNX_HASH)) {
                    onnxFile.delete()
                    throw Exception("ONNX model verification failed!")
                }
            }

            if (isModelSetupComplete()) {
                trySend(DownloadProgress.Success)
            } else {
                trySend(DownloadProgress.Error("Verification failed"))
            }
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
            try {
                client.prepareGet(url) {
                    onDownload { bytesSentTotal, contentLength ->
                        if (contentLength > 0) {
                            onProgress(bytesSentTotal.toFloat() / contentLength)
                        }
                    }
                }.execute { response ->
                    if (response.status.isSuccess()) {
                        val channel = response.bodyAsChannel()
                        val expectedSize = response.contentLength() ?: -1L
                        var totalBytesRead = 0L
                        
                        FileOutputStream(destination).use { fos ->
                            val bufferedOutputStream = fos.buffered()
                            val inputStream = channel.toInputStream()
                            val buffer = ByteArray(64 * 1024)
                            var bytes: Int
                            
                            while (inputStream.read(buffer).also { bytes = it } != -1) {
                                bufferedOutputStream.write(buffer, 0, bytes)
                                totalBytesRead += bytes
                            }
                            
                            bufferedOutputStream.flush()
                            fos.flush()
                            fos.getFD().sync()
                        }
                        
                        if (expectedSize != -1L && totalBytesRead != expectedSize) {
                            destination.delete()
                            throw Exception("File size mismatch after download!")
                        }
                    } else {
                        throw Exception("HTTP ${response.status.value}")
                    }
                }
            } catch (e: Exception) {
                if (destination.exists()) {
                    destination.delete()
                }
                throw e
            }
        }
    }

    fun downloadLevel(levelIndex: Int): Flow<DownloadProgress> = callbackFlow {
        val file = getDatabaseFile(levelIndex)
        file.parentFile?.mkdirs()
        val url = getDownloadUrl(getDbName(levelIndex))

        try {
            downloadStreamingInternal(url, file) { progress ->
                trySend(DownloadProgress.Progress(progress))
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
