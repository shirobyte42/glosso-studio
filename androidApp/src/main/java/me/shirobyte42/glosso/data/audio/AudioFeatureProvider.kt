package me.shirobyte42.glosso.data.audio

import java.util.Random
import kotlin.math.*

/**
 * Feature provider for Allosaurus eng2102. 
 * Replicates the official pm_config parameters.
 */
class AudioFeatureProvider(
    private val sampleRate: Int = 8000,
    private val numCep: Int = 40,
    private val nFilt: Int = 40,
    private val lowFreq: Double = 40.0,
    private val highFreq: Double = 3800.0,
    private val winLen: Double = 0.025,
    private val winStep: Double = 0.01,
    private val preemph: Double = 0.97,
    private val ceplifter: Int = 22
) {
    private val frameLen = (winLen * sampleRate).roundToInt()
    private val frameStep = (winStep * sampleRate).roundToInt()
    private val nfft = nextPowerOfTwo(frameLen)
    private val window = FloatArray(frameLen) { i ->
        (0.5 - 0.5 * cos(2.0 * PI * i / (frameLen - 1))).pow(0.85).toFloat()
    }
    private val melFilterBank = getFilterBanks(nFilt, nfft, sampleRate, lowFreq, highFreq)

    fun computeFeatures(signal: FloatArray): Array<FloatArray> {
        val slen = signal.size
        val numFrames = if (slen <= frameLen) 1 else 1 + (slen - frameLen) / frameStep
        val feat = Array(numFrames) { FloatArray(numCep) }

        for (i in 0 until numFrames) {
            val start = i * frameStep
            val frame = FloatArray(frameLen)
            
            var sum = 0f
            for (j in 0 until frameLen) {
                val s = if (start + j < slen) signal[start + j] else 0f
                frame[j] = s
                sum += s
            }
            val mean = sum / frameLen
            for (j in 0 until frameLen) frame[j] -= mean

            val emphasized = FloatArray(frameLen)
            emphasized[0] = (1.0 - preemph).toFloat() * frame[0]
            for (j in 1 until frameLen) {
                emphasized[j] = frame[j] - (preemph * frame[j - 1]).toFloat()
            }

            for (j in 0 until frameLen) emphasized[j] *= window[j]
            val pspec = getPowerSpectrum(emphasized, nfft)

            val fbEnergies = FloatArray(nFilt)
            for (m in 0 until nFilt) {
                var e = 0f
                for (k in 0 until (nfft / 2 + 1)) {
                    e += pspec[k] * melFilterBank[m][k]
                }
                fbEnergies[m] = max(e, 2.220446049250313e-16f)
            }

            val logEnergies = FloatArray(nFilt) { m -> ln(fbEnergies[m]) }
            val dctFeat = dctType2Ortho(logEnergies, numCep)
            
            // Note: Official config says use_energy: false, so we KEEP the DCT result at index 0
            feat[i] = applyLifter(dctFeat, ceplifter)
        }
        return feat
    }

    private fun nextPowerOfTwo(n: Int): Int {
        var p = 1
        while (p < n) p = p shl 1
        return p
    }

    private fun getPowerSpectrum(frame: FloatArray, nfft: Int): FloatArray {
        val complexFrame = FloatArray(nfft * 2)
        for (i in 0 until frame.size) complexFrame[i * 2] = frame[i]
        fft(complexFrame)
        val pspec = FloatArray(nfft / 2 + 1)
        for (i in 0..nfft / 2) {
            val real = complexFrame[i * 2]
            val imag = complexFrame[i * 2 + 1]
            pspec[i] = (real * real + imag * imag)
        }
        return pspec
    }

    private fun fft(data: FloatArray) {
        val n = data.size / 2
        var j = 0
        for (i in 0 until n) {
            if (i < j) {
                var temp = data[i * 2]; data[i * 2] = data[j * 2]; data[j * 2] = temp
                temp = data[i * 2 + 1]; data[i * 2 + 1] = data[j * 2 + 1]; data[j * 2 + 1] = temp
            }
            var m = n shr 1
            while (m >= 1 && j >= m) { j -= m; m = m shr 1 }
            j += m
        }
        var m = 1
        while (m < n) {
            val step = m shl 1
            val theta = -PI / m
            val wBaseReal = cos(theta).toFloat(); val wBaseImag = sin(theta).toFloat()
            var wReal = 1f; var wImag = 0f
            for (k in 0 until m) {
                for (i in k until n step step) {
                    val target = i + m
                    val tReal = wReal * data[target * 2] - wImag * data[target * 2 + 1]
                    val tImag = wReal * data[target * 2 + 1] + wImag * data[target * 2]
                    data[target * 2] = data[i * 2] - tReal
                    data[target * 2 + 1] = data[i * 2 + 1] - tImag
                    data[i * 2] += tReal; data[i * 2 + 1] += tImag
                }
                val nextWReal = wReal * wBaseReal - wImag * wBaseImag
                wImag = wReal * wBaseImag + wImag * wBaseReal
                wReal = nextWReal
            }
            m = step
        }
    }

    private fun hzToMel(hz: Double): Double = 1127.0 * ln(1.0 + hz / 700.0)

    private fun getFilterBanks(nfilt: Int, nfft: Int, samplerate: Int, lowfreq: Double, highfreq: Double): Array<FloatArray> {
        val lowMel = hzToMel(lowfreq)
        val highMel = hzToMel(highfreq)
        val melFreqDelta = (highMel - lowMel) / (nfilt + 1)
        val fbank = Array(nfilt) { FloatArray(nfft / 2 + 1) }
        for (j in 0 until nfilt) {
            val leftMel = lowMel + j * melFreqDelta
            val centerMel = lowMel + (j + 1) * melFreqDelta
            val rightMel = lowMel + (j + 2) * melFreqDelta
            for (i in 0 until (nfft / 2 + 1)) {
                val freqHz = i.toDouble() * samplerate / nfft
                val mel = hzToMel(freqHz)
                if (mel > leftMel && mel < rightMel) {
                    if (mel < centerMel) {
                        fbank[j][i] = ((mel - leftMel) / (centerMel - leftMel)).toFloat()
                    } else {
                        fbank[j][i] = ((rightMel - mel) / (rightMel - centerMel)).toFloat()
                    }
                }
            }
        }
        return fbank
    }

    private fun dctType2Ortho(data: FloatArray, n: Int): FloatArray {
        val result = FloatArray(n)
        val N = data.size
        val factor0 = sqrt(1.0 / N)
        val factorK = sqrt(2.0 / N)
        for (k in 0 until n) {
            var sum = 0.0
            for (i in 0 until N) {
                sum += data[i] * cos(PI * k * (i + 0.5) / N)
            }
            result[k] = (sum * (if (k == 0) factor0 else factorK)).toFloat()
        }
        return result
    }

    private fun applyLifter(cepstra: FloatArray, L: Int): FloatArray {
        if (L <= 0) return cepstra
        val n = cepstra.size
        val result = FloatArray(n)
        for (i in 0 until n) {
            val lift = 1.0 + (L / 2.0) * sin(PI * i / L)
            result[i] = (cepstra[i] * lift).toFloat()
        }
        return result
    }
}
