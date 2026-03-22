package me.shirobyte42.glosso.data.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

class WavRecorder(private val file: File) {
    // Record directly at 8000Hz to match Allosaurus eng2102 requirements
    private val sampleRate = 8000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null

    @SuppressLint("MissingPermission")
    fun start() {
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioRecord?.startRecording()
        isRecording = true

        recordingThread = Thread {
            writeAudioDataToFile()
        }
        recordingThread?.start()
    }

    fun stop() {
        isRecording = false
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            recordingThread?.join()
            recordingThread = null
            updateWavHeader()
        } catch (e: Exception) {
            Log.e("WavRecorder", "Error stopping recording", e)
        }
    }

    private fun writeAudioDataToFile() {
        val os = FileOutputStream(file)
        os.write(ByteArray(44)) // Header placeholder

        val data = ShortArray(bufferSize / 2)
        while (isRecording) {
            val read = audioRecord?.read(data, 0, data.size) ?: 0
            if (read > 0) {
                // Write as Little-Endian bytes
                val buffer = ByteArray(read * 2)
                for (i in 0 until read) {
                    buffer[i * 2] = (data[i].toInt() and 0xff).toByte()
                    buffer[i * 2 + 1] = (data[i].toInt() shr 8 and 0xff).toByte()
                }
                os.write(buffer)
            }
        }
        os.close()
    }

    private fun updateWavHeader() {
        val totalAudioLen = file.length() - 44
        val totalDataLen = totalAudioLen + 36
        val channels = 1
        val byteRate = (16 * sampleRate * channels / 8).toLong()

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0 // PCM
        header[22] = channels.toByte(); header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * 16 / 8).toByte(); header[33] = 0
        header[34] = 16; header[35] = 0 // Bits per sample
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()

        val raf = RandomAccessFile(file, "rw")
        raf.seek(0); raf.write(header); raf.close()
    }
}
