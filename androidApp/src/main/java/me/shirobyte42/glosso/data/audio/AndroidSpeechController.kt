package me.shirobyte42.glosso.data.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.os.Build
import android.util.Base64
import android.util.Log
import me.shirobyte42.glosso.domain.model.MatchStatusModel
import me.shirobyte42.glosso.domain.model.PhonemeMatchModel
import me.shirobyte42.glosso.domain.model.PronunciationFeedback
import me.shirobyte42.glosso.domain.repository.SpeechController
import java.io.File
import java.io.FileOutputStream

class AndroidSpeechController(
    private val context: Context,
    private val recognizer: AllosaurusRecognizer
) : SpeechController {

    private var wavRecorder: WavRecorder? = null
    private var player: MediaPlayer? = null
    private val audioFile = File(context.cacheDir, "speech_temp.wav")

    fun getContext(): Context = context

    override fun startRecording() {
        Log.d("SpeechController", "Starting WAV recording to ${audioFile.absolutePath}")
        try {
            stopPlayback()
            if (audioFile.exists()) {
                audioFile.delete()
            }
            
            wavRecorder = WavRecorder(audioFile)
            wavRecorder?.start()
            Log.d("SpeechController", "WAV Recording started successfully")
        } catch (e: Exception) {
            Log.e("SpeechController", "Failed to start WAV recording", e)
            throw e
        }
    }

    override fun stopRecording(): String? {
        Log.d("SpeechController", "Stopping WAV recording")
        return try {
            wavRecorder?.stop()
            wavRecorder = null
            
            if (audioFile.exists()) {
                val size = audioFile.length()
                Log.d("SpeechController", "WAV file size: $size bytes")
                if (size > 44) {
                    val bytes = audioFile.readBytes()
                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                } else {
                    Log.e("SpeechController", "WAV file is empty or only has header")
                    null
                }
            } else {
                Log.e("SpeechController", "WAV file does not exist after stop")
                null
            }
        } catch (e: Exception) {
            Log.e("SpeechController", "Failed to stop WAV recording", e)
            null
        }
    }

    override fun recognize(base64Wav: String): String? {
        return recognizer.recognize(base64Wav)
    }

    override fun calculateScore(targetText: String, expectedIpa: String, actualIpa: String): PronunciationFeedback {
        val result = PhoneticComparator.calculateScoringResult(targetText, expectedIpa, actualIpa)
        val letterFeedback = PhoneticComparator.generateLetterFeedback(targetText, expectedIpa, result.alignment)
        
        return PronunciationFeedback(
            score = result.score,
            transcription = actualIpa,
            normalizedExpected = result.normalizedExpected,
            normalizedActual = result.normalizedActual,
            alignment = result.alignment.map { match ->
                PhonemeMatchModel(
                    expected = match.expected,
                    actual = match.actual,
                    status = when(match.status) {
                        MatchStatus.PERFECT -> MatchStatusModel.PERFECT
                        MatchStatus.CLOSE -> MatchStatusModel.CLOSE
                        MatchStatus.MISSED -> MatchStatusModel.MISSED
                    }
                )
            },
            letterFeedback = letterFeedback.map { info ->
                me.shirobyte42.glosso.domain.model.LetterFeedbackModel(
                    char = info.char,
                    status = when(info.status) {
                        MatchStatus.PERFECT -> MatchStatusModel.PERFECT
                        MatchStatus.CLOSE -> MatchStatusModel.CLOSE
                        MatchStatus.MISSED -> MatchStatusModel.MISSED
                    }
                )
            }
        )
    }

    override fun getNormalizedPhonemes(ipa: String): String {
        return PhoneticComparator.normalize(ipa)
    }

    override fun playAudio(base64: String, speed: Float) {
        try {
            stopPlayback()
            val pureBase64 = if (base64.contains(",")) base64.substringAfter(",") else base64
            val audioData = Base64.decode(pureBase64, Base64.DEFAULT)
            
            val tempFile = File.createTempFile("native_audio", ".mp3", context.cacheDir)
            FileOutputStream(tempFile).use { it.write(audioData) }

            player = MediaPlayer().apply {
                setDataSource(tempFile.absolutePath)
                prepare()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    playbackParams = PlaybackParams().setSpeed(speed)
                }
                start()
                setOnCompletionListener {
                    tempFile.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("SpeechController", "Failed to play audio", e)
        }
    }

    override fun playUrl(url: String, speed: Float) {
        try {
            stopPlayback()
            player = MediaPlayer().apply {
                setDataSource(url)
                prepare()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    playbackParams = PlaybackParams().setSpeed(speed)
                }
                start()
            }
        } catch (e: Exception) {
            Log.e("SpeechController", "Failed to play URL", e)
        }
    }

    override fun stopPlayback() {
        try {
            player?.stop()
            player?.release()
            player = null
        } catch (e: Exception) {
            // ignore
        }
    }
}
