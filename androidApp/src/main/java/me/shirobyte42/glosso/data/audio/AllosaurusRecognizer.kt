package me.shirobyte42.glosso.data.audio

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.util.Base64
import android.util.Log
import java.io.File
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
    private val featureProvider = AudioFeatureProvider(sampleRate = 8000)

    init {
        initialize()
    }

    fun initialize() {
        if (ortSession != null) return
        
        try {
            Log.d(TAG, "Initializing OrtEnvironment...")
            if (ortEnv == null) {
                ortEnv = OrtEnvironment.getEnvironment()
            }
            
            val modelFile = File(context.filesDir, "allosaurus_eng2102.onnx")
            if (!modelFile.exists()) {
                Log.e(TAG, "CRITICAL: model NOT FOUND in files!")
                return
            }

            val modelBytes = modelFile.readBytes()
            Log.d(TAG, "Creating OrtSession...")
            ortSession = ortEnv?.createSession(modelBytes)
            
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
            val phoneMapFile = File(context.filesDir, "phone_eng.txt")
            if (phoneMapFile.exists()) {
                phoneMapFile.bufferedReader().use { reader ->
                    reader.forEachLine { line ->
                        val parts = line.trim().split(Regex("\\s+"))
                        if (parts.size >= 2) {
                            val phone = parts[0]
                            val id = parts[1].toInt()
                            map[id] = phone
                        }
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
        
        if (session == null || env == null) return null
        
        try {
            val wavData = try {
                Base64.decode(base64Wav, Base64.DEFAULT)
            } catch (e: Exception) {
                return null
            }

            if (wavData.size <= 44) return null
            
            val pcmData = ShortArray((wavData.size - 44) / 2)
            for (i in pcmData.indices) {
                val b1 = wavData[44 + i * 2].toInt() and 0xff
                val b2 = wavData[44 + i * 2 + 1].toInt() and 0xff
                pcmData[i] = (b1 or (b2 shl 8)).toShort()
            }
            
            val floatPcm = FloatArray(pcmData.size) { pcmData[it].toFloat() / 32768.0f }
            
            // 1. Compute MFCCs (with Log Energy replacement)
            val baseFeat = featureProvider.computeFeatures(floatPcm)
            if (baseFeat.isEmpty()) return null
            
            // 2. CMVN (Speaker Normalization)
            val feat = normalize(baseFeat)
            
            // 3. No stacking - model expects dim 40 directly
            val numFrames = feat.size
            val featDim = feat[0].size // 40
            val batchSize = 1L
            val seqLen = numFrames.toLong()
            
            val inputBuffer = FloatBuffer.allocate(numFrames * featDim)
            for (frame in feat) {
                inputBuffer.put(frame)
            }
            inputBuffer.rewind()
            
            val inputTensor = OnnxTensor.createTensor(env, inputBuffer, longArrayOf(batchSize, seqLen, featDim.toLong()))
            val lengthsBuffer = LongBuffer.wrap(longArrayOf(seqLen))
            val lengthsTensor = OnnxTensor.createTensor(env, lengthsBuffer, longArrayOf(1))
            
            Log.d(TAG, "Running ONNX inference with $numFrames frames (dim: $featDim)...")
            val results = session.run(mapOf("input_tensor" to inputTensor, "input_lengths" to lengthsTensor))
            val outputTensor = results[0] as OnnxTensor
            val outputFloatArray = outputTensor.floatBuffer.array()
            val outputShape = outputTensor.info.shape
            
            val classesCount = outputShape[2].toInt()
            val tokens = mutableListOf<String>()
            
            for (t in 0 until numFrames) {
                var maxIdx = -1
                var maxVal = Float.NEGATIVE_INFINITY
                for (p in 0 until classesCount) {
                    val score = outputFloatArray[t * classesCount + p]
                    if (score > maxVal) {
                        maxVal = score
                        maxIdx = p
                    }
                }
                if (maxIdx != -1) {
                    val phone = phoneMap[maxIdx]
                    if (phone != null && phone != "<blk>" && phone != "<sil>") {
                        if (tokens.isEmpty() || tokens.last() != phone) {
                            tokens.add(phone)
                        }
                    }
                }
            }
            
            Log.d(TAG, "Decoded tokens: ${tokens.joinToString(" ")}")
            return tokens.joinToString(" ")
        } catch (e: Exception) {
            Log.e(TAG, "Recognition failed", e)
            return null
        }
    }

    private fun normalize(feat: Array<FloatArray>): Array<FloatArray> {
        if (feat.isEmpty()) return feat
        val numFrames = feat.size
        val dim = feat[0].size
        val mean = FloatArray(dim)
        val std = FloatArray(dim)
        
        for (d in 0 until dim) {
            var sum = 0.0
            for (t in 0 until numFrames) sum += feat[t][d]
            mean[d] = (sum / numFrames).toFloat()
            
            var sumSq = 0.0
            for (t in 0 until numFrames) sumSq += (feat[t][d] - mean[d]) * (feat[t][d] - mean[d])
            std[d] = sqrt(sumSq / numFrames).toFloat() + 1e-9f
        }
        
        return Array(numFrames) { t ->
            val frame = feat[t]
            FloatArray(dim) { d -> (frame[d] - mean[d]) / std[d] }
        }
    }
}
