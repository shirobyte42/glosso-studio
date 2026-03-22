package me.shirobyte42.glosso.data.audio

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Base64
import android.util.Log
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

class AllosaurusRecognizer(private val context: Context) {

    private val TAG = "AllosaurusRecognizer"
    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var phoneMap: Map<Int, String> = emptyMap()
    private val mfcc = Mfcc() // 8kHz, 40 cepstral coefficients

    init {
        try {
            Log.d(TAG, "Initializing OrtEnvironment...")
            ortEnv = OrtEnvironment.getEnvironment()
            
            Log.d(TAG, "Checking assets for model...")
            val assetList = context.assets.list("") ?: emptyArray()
            Log.d(TAG, "Assets available: ${assetList.joinToString(", ")}")

            if ("allosaurus_eng2102.onnx" !in assetList) {
                Log.e(TAG, "CRITICAL: allosaurus_eng2102.onnx NOT FOUND in assets!")
            }

            Log.d(TAG, "Loading model allosaurus_eng2102.onnx from assets...")
            val modelBytes = try {
                context.assets.open("allosaurus_eng2102.onnx").use { it.readBytes() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read model bytes from assets", e)
                throw e
            }
            Log.d(TAG, "Model size read: ${modelBytes.size} bytes")
            
            Log.d(TAG, "Creating OrtSession...")
            ortSession = ortEnv?.createSession(modelBytes)
            Log.d(TAG, "OrtSession created successfully")
            
            Log.d(TAG, "Loading English phone map...")
            phoneMap = loadPhoneMap()
            
            Log.d(TAG, "Initialization complete. Map size: ${phoneMap.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AllosaurusRecognizer", e)
        }
    }

    private fun loadPhoneMap(): Map<Int, String> {
        val map = mutableMapOf<Int, String>()
        try {
            context.assets.open("phone_eng.txt").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size >= 2) {
                        val phone = parts[0]
                        val id = parts[1].toInt()
                        map[id] = phone
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load phone map", e)
        }
        return map
    }

    fun recognize(base64Wav: String): String? {
        val session = ortSession
        val env = ortEnv
        
        if (session == null || env == null) {
            Log.e(TAG, "ONNX Session or Environment is null! Session: $session, Env: $env")
            return null
        }
        
        try {
            val wavData = try {
                Base64.decode(base64Wav, Base64.DEFAULT)
            } catch (e: Exception) {
                Log.e(TAG, "Base64 decode failed", e)
                return null
            }

            if (wavData.size <= 44) {
                Log.e(TAG, "WAV data too small: ${wavData.size} bytes")
                return null
            }
            
            val pcmData = ShortArray((wavData.size - 44) / 2)
            for (i in pcmData.indices) {
                val low = wavData[44 + i * 2].toInt() and 0xff
                val high = wavData[44 + i * 2 + 1].toInt()
                pcmData[i] = ((high shl 8) or low).toShort()
            }

            // 1. Pre-process: No trimming to avoid eating letters
            val trimmedPcm = pcmData 
            Log.d(TAG, "PCM size: ${pcmData.size}")

            if (trimmedPcm.size < 400) {
                Log.w(TAG, "Trimmed audio too short for recognition")
                return "" 
            }

            Log.d(TAG, "Extracted and trimmed ${trimmedPcm.size} PCM samples at 8kHz")

            // 2. Convert to FloatArray for MFCC
            val signal = FloatArray(trimmedPcm.size) { i -> trimmedPcm[i].toFloat() }

            // 3. Compute MFCC
            var feat = mfcc.compute(signal)
            Log.d(TAG, "MFCC computed: ${feat.size} frames")
            
            // 4. Apply speaker CMVN
            feat = applyCmvn(feat)

            // 5. eng2102 uses feature_window: 1 (no subsampling, just the 40 MFCCs)
            if (feat.isEmpty()) {
                Log.w(TAG, "Feature matrix is empty")
                return null
            }

            // 6. ONNX Inference
            val batchSize = 1L
            val seqLen = feat.size.toLong()
            val featDim = 40L 
            
            val flatFeat = FloatArray((batchSize * seqLen * featDim).toInt())
            for (t in 0 until feat.size) {
                val frame = feat[t]
                for (d in 0 until 40) {
                    flatFeat[t * 40 + d] = frame[d]
                }
            }

            val inputBuffer = FloatBuffer.wrap(flatFeat)
            val inputTensor = OnnxTensor.createTensor(env, inputBuffer, longArrayOf(batchSize, seqLen, featDim))
            
            val lengthsBuffer = LongBuffer.wrap(longArrayOf(seqLen))
            val lengthsTensor = OnnxTensor.createTensor(env, lengthsBuffer, longArrayOf(1))
            
            val inputs = mapOf(
                "input_tensor" to inputTensor,
                "input_lengths" to lengthsTensor
            )
            
            Log.d(TAG, "Running ONNX inference with seqLen $seqLen...")
            val results = session.run(inputs)
            val outputTensor = results[0] as OnnxTensor
            val outputFloatArray = outputTensor.floatBuffer.array()
            val numPhones = 39 // eng2102 has 39 phones
            
            Log.d(TAG, "ONNX inference successful. Output array size: ${outputFloatArray.size}")

            val decodedPhones = mutableListOf<String>()
            var lastPhoneId = -1
            
            val threshold = -3.0f 

            for (t in 0 until seqLen.toInt()) {
                var maxVal = -Float.MAX_VALUE
                var maxId = -1
                
                for (p in 0 until numPhones) {
                    val v = outputFloatArray[t * numPhones + p]
                    if (v > maxVal) {
                        maxVal = v
                        maxId = p
                    }
                }
                
                if (maxId != 0 && maxId != lastPhoneId && maxVal > threshold) {
                    phoneMap[maxId]?.let { decodedPhones.add(it) }
                }
                lastPhoneId = maxId
            }

            val result = decodedPhones.joinToString(" ")
            Log.d(TAG, "Recognition result: $result")
            return result

        } catch (e: Exception) {
            Log.e(TAG, "Recognition failed with exception", e)
            return null
        }
    }

    private fun applyCmvn(feat: Array<FloatArray>): Array<FloatArray> {
        if (feat.isEmpty()) return feat
        val numFrames = feat.size
        val dim = feat[0].size
        val mean = FloatArray(dim)
        val std = FloatArray(dim)

        for (t in 0 until numFrames) {
            val frame = feat[t]
            for (d in 0 until dim) mean[d] += frame[d]
        }
        val framesCount = numFrames.toFloat()
        for (d in 0 until dim) mean[d] /= framesCount

        for (t in 0 until numFrames) {
            val frame = feat[t]
            for (d in 0 until dim) {
                val diff = frame[d] - mean[d]
                std[d] += diff * diff
            }
        }
        for (d in 0 until dim) {
            val s = sqrt(((std[d] / framesCount) + 1e-10f).toDouble()).toFloat()
            std[d] = if (s < 1e-10f) 1.0f else s
        }

        return Array(numFrames) { t ->
            val frame = feat[t]
            FloatArray(dim) { d -> (frame[d] - mean[d]) / std[d] }
        }
    }
    
    fun close() {
        ortSession?.close()
        ortEnv?.close()
    }
}
