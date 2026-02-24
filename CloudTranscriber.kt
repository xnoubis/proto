package com.flowvoice.engine

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Cloud Transcriber - Whisper API fallback for enhanced accuracy
 * 
 * USE CASES:
 * 1. Offline transcription needs accuracy boost
 * 2. Complex audio (background noise, accents)
 * 3. User explicitly requests cloud processing
 * 
 * DESIGN NOTES:
 * - Async operation with retry mechanism
 * - Respects offline-first: only called when explicitly needed
 * - API key stored in encrypted preferences (not hardcoded)
 */
class CloudTranscriber(
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1/audio/transcriptions"
) {
    
    companion object {
        private const val TAG = "CloudTranscriber"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L
    }

    sealed class CloudResult {
        data class Success(val text: String, val language: String? = null) : CloudResult()
        data class Error(val message: String, val canRetry: Boolean = true) : CloudResult()
        object NetworkUnavailable : CloudResult()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Transcribe audio file using Whisper API
     * 
     * @param audioFile WAV or MP3 file to transcribe
     * @param language Optional language hint (e.g., "en")
     * @param retryCount Current retry attempt (internal)
     */
    suspend fun transcribe(
        audioFile: File,
        language: String? = "en",
        retryCount: Int = 0
    ): CloudResult = withContext(Dispatchers.IO) {
        
        if (apiKey.isBlank()) {
            return@withContext CloudResult.Error(
                "API key not configured. Set in Settings.",
                canRetry = false
            )
        }

        if (!audioFile.exists()) {
            return@withContext CloudResult.Error(
                "Audio file not found: ${audioFile.path}",
                canRetry = false
            )
        }

        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody("audio/wav".toMediaType())
                )
                .addFormDataPart("model", "whisper-1")
                .addFormDataPart("response_format", "verbose_json")
                .apply {
                    language?.let { addFormDataPart("language", it) }
                }
                .build()

            val request = Request.Builder()
                .url(baseUrl)
                .header("Authorization", "Bearer $apiKey")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()

            when {
                response.isSuccessful -> {
                    val json = response.body?.string() ?: ""
                    parseResponse(json)
                }
                
                response.code == 429 -> {
                    // Rate limited - retry with backoff
                    if (retryCount < MAX_RETRIES) {
                        Log.w(TAG, "Rate limited, retrying in ${RETRY_DELAY_MS}ms")
                        kotlinx.coroutines.delay(RETRY_DELAY_MS * (retryCount + 1))
                        transcribe(audioFile, language, retryCount + 1)
                    } else {
                        CloudResult.Error("Rate limited. Please try again later.", canRetry = true)
                    }
                }
                
                response.code in 500..599 -> {
                    // Server error - retry
                    if (retryCount < MAX_RETRIES) {
                        Log.w(TAG, "Server error ${response.code}, retrying...")
                        kotlinx.coroutines.delay(RETRY_DELAY_MS)
                        transcribe(audioFile, language, retryCount + 1)
                    } else {
                        CloudResult.Error("Server unavailable. Please try again.", canRetry = true)
                    }
                }
                
                else -> {
                    val errorBody = response.body?.string() ?: "Unknown error"
                    Log.e(TAG, "API error: ${response.code} - $errorBody")
                    CloudResult.Error("Transcription failed: ${response.message}", canRetry = true)
                }
            }

        } catch (e: IOException) {
            Log.e(TAG, "Network error", e)
            if (retryCount < MAX_RETRIES) {
                kotlinx.coroutines.delay(RETRY_DELAY_MS)
                transcribe(audioFile, language, retryCount + 1)
            } else {
                CloudResult.NetworkUnavailable
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error", e)
            CloudResult.Error("Unexpected error: ${e.message}", canRetry = true)
        }
    }

    private fun parseResponse(json: String): CloudResult {
        return try {
            val obj = JSONObject(json)
            val text = obj.getString("text")
            val language = obj.optString("language", null)
            CloudResult.Success(text.trim(), language)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse response: $json", e)
            CloudResult.Error("Failed to parse response", canRetry = false)
        }
    }

    /**
     * Check if cloud is available (quick connectivity test)
     */
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("https://api.openai.com/v1/models")
                .head()
                .build()
            client.newCall(request).execute().isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Hybrid Transcription Strategy
 * 
 * Coordinates between offline (Vosk) and cloud (Whisper) based on:
 * - Connectivity status
 * - User preference
 * - Transcription confidence (future: implement confidence scoring)
 */
data class TranscriptionConfig(
    val preferOffline: Boolean = true,
    val cloudFallbackOnError: Boolean = true,
    val cloudEnhancementEnabled: Boolean = false,  // Post-process with cloud
    val language: String = "en"
)

enum class TranscriptionMode {
    OFFLINE_ONLY,      // Never use cloud
    HYBRID_AUTO,       // Offline primary, cloud fallback on error
    HYBRID_ENHANCE,    // Offline real-time, cloud post-processing
    CLOUD_ONLY         // Always use cloud (requires connectivity)
}
