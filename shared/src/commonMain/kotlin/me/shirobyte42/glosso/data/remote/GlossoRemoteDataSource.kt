package me.shirobyte42.glosso.data.remote

import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.shirobyte42.glosso.domain.model.PronunciationFeedback
import me.shirobyte42.glosso.domain.model.Sentence

@Serializable
internal data class SampleRequest(
    val category: Int, 
    val language: String,
    val topics: List<String>? = null,
    val exclude: List<String>? = null
)

@Serializable
internal data class SampleResponse(val real_transcript: List<String>, val ipa_transcript: String, val native_audios: List<String?>)

@Serializable
internal data class AnalyzeRequest(
    val file: String, 
    val target_text: String, 
    val target_ipa: String,
    val format: String, 
    val mode: String,
    val language: String
)

@Serializable
internal data class AnalyzeResponse(val choices: List<Choice>)

@Serializable
internal data class Choice(val message: Message)

@Serializable
internal data class Message(val content: String)

class GlossoRemoteDataSource(private val client: HttpClient) {
    private val baseUrl = "https://articulate.pages.dev/"
    private val TAG = "RemoteDataSource"

    suspend fun fetchSample(category: Int, language: String, topics: List<String>? = null, exclude: List<String>? = null): Sentence {
        Log.d(TAG, "Fetching sample: category=$category, language=$language, topics=$topics, excludeCount=${exclude?.size ?: 0}")
        val response = client.post("${baseUrl}api/getSample") {
            contentType(ContentType.Application.Json)
            setBody(SampleRequest(category, language, topics, exclude))
        }
        
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            Log.e(TAG, "Failed to fetch sample: ${response.status} - $errorBody")
            throw Exception("Server error: ${response.status}")
        }

        val data: SampleResponse = response.body()
        Log.d(TAG, "Received sample: ${data.real_transcript.firstOrNull()}")

        val level = when(category) {
            0 -> "A1"
            1 -> "A2"
            2 -> "B1"
            3 -> "B2"
            4 -> "C1"
            5 -> "C2"
            else -> "A1"
        }

        return Sentence(
            text = data.real_transcript.firstOrNull() ?: "",
            ipa = data.ipa_transcript,
            level = level,
            topic = "General", // Remote API doesn't currently provide topics
            language = language,
            audio1 = data.native_audios.getOrNull(0),
            audio2 = data.native_audios.getOrNull(1)
        )
    }

    suspend fun analyze(base64Audio: String, targetText: String, targetIpa: String, mode: String, language: String = "en"): PronunciationFeedback {
        Log.d(TAG, "Analyzing speech: mode=$mode, language=$language, target='$targetText'")
        
        try {
            val response = client.post("${baseUrl}api/analyze") {
                contentType(ContentType.Application.Json)
                setBody(AnalyzeRequest(base64Audio, targetText, targetIpa, "wav", mode, language))
            }

            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                Log.e(TAG, "Analysis request failed: ${response.status} - $errorBody")
                throw Exception("Analysis failed: ${response.status}")
            }

            val data: AnalyzeResponse = response.body()
            val content = data.choices.firstOrNull()?.message?.content ?: ""
            Log.d(TAG, "AI Content received (raw): $content")
            
            val feedback = Json { ignoreUnknownKeys = true }.decodeFromString<PronunciationFeedback>(content)
            Log.d(TAG, "Analysis result: score=${feedback.score}, transcription='${feedback.transcription}'")
            
            return feedback
        } catch (e: Exception) {
            Log.e(TAG, "Error during analysis", e)
            throw e
        }
    }
}
