package com.flowvoice.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.SpeechStreamService
import org.vosk.android.StorageService
import java.io.IOException

/**
 * FlowVoice Engine - Offline-first voice recognition with hybrid cloud fallback
 * 
 * DESIGN PRINCIPLES (Accessibility-first):
 * - Real-time display: text appears as spoken
 * - Offline primary: no cloud dependency for core function
 * - Retry mechanism: graceful recovery from failures
 * - Minimal interaction: one-tap activation
 */
class VoiceEngine(private val context: Context) {

    companion object {
        private const val TAG = "FlowVoiceEngine"
        private const val SAMPLE_RATE = 16000f
        
        // Model assets - downloaded on first launch
        private const val MODEL_PATH = "model-en-us"
    }

    // State management
    sealed class VoiceState {
        object Idle : VoiceState()
        object Loading : VoiceState()
        object Listening : VoiceState()
        object Processing : VoiceState()
        data class Error(val message: String, val canRetry: Boolean = true) : VoiceState()
    }
    
    sealed class TranscriptionEvent {
        data class Partial(val text: String) : TranscriptionEvent()  // Real-time as you speak
        data class Final(val text: String) : TranscriptionEvent()    // Confirmed transcription
        data class Silence(val duration: Long) : TranscriptionEvent()
    }

    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()
    
    private val _transcription = MutableSharedFlow<TranscriptionEvent>(replay = 0, extraBufferCapacity = 64)
    val transcription: SharedFlow<TranscriptionEvent> = _transcription.asSharedFlow()
    
    private val _fullText = MutableStateFlow("")
    val fullText: StateFlow<String> = _fullText.asStateFlow()
    
    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Initialize the Vosk model - call once on app start
     * Downloads model if not present (~50MB for lightweight English)
     */
    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        _state.value = VoiceState.Loading
        
        try {
            // Check if model exists in assets/storage
            StorageService.unpack(context, MODEL_PATH, "model",
                { model ->
                    this@VoiceEngine.model = model
                    Log.i(TAG, "Model loaded successfully")
                    _state.value = VoiceState.Idle
                },
                { exception ->
                    Log.e(TAG, "Failed to load model", exception)
                    _state.value = VoiceState.Error(
                        "Model initialization failed: ${exception.message}",
                        canRetry = true
                    )
                }
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Model initialization error", e)
            _state.value = VoiceState.Error("Failed to initialize: ${e.message}", canRetry = true)
            Result.failure(e)
        }
    }

    /**
     * Start listening - main entry point for voice capture
     */
    fun startListening() {
        val currentModel = model
        if (currentModel == null) {
            _state.value = VoiceState.Error("Model not loaded", canRetry = true)
            return
        }
        
        if (_state.value == VoiceState.Listening) {
            Log.w(TAG, "Already listening")
            return
        }
        
        try {
            // Create recognizer with model
            recognizer = Recognizer(currentModel, SAMPLE_RATE).apply {
                setWords(true)        // Include word-level timestamps
                setPartialWords(true) // Enable partial results (real-time display)
            }
            
            // Create speech service
            speechService = SpeechService(recognizer, SAMPLE_RATE).apply {
                startListening(createRecognitionListener())
            }
            
            _state.value = VoiceState.Listening
            Log.i(TAG, "Started listening")
            
        } catch (e: IOException) {
            Log.e(TAG, "Failed to start listening", e)
            _state.value = VoiceState.Error("Microphone access failed: ${e.message}", canRetry = true)
        }
    }

    /**
     * Stop listening and finalize transcription
     */
    fun stopListening() {
        speechService?.stop()
        speechService = null
        
        recognizer?.close()
        recognizer = null
        
        _state.value = VoiceState.Idle
        Log.i(TAG, "Stopped listening")
    }

    /**
     * Toggle listening state - convenience for single-tap UI
     */
    fun toggleListening() {
        when (_state.value) {
            VoiceState.Listening -> stopListening()
            VoiceState.Idle -> startListening()
            is VoiceState.Error -> {
                // Retry on tap
                scope.launch { 
                    initialize()
                    if (_state.value == VoiceState.Idle) {
                        startListening()
                    }
                }
            }
            else -> { /* Loading/Processing - ignore */ }
        }
    }

    /**
     * Clear accumulated text
     */
    fun clearText() {
        _fullText.value = ""
    }

    /**
     * Get current text for clipboard/sharing
     */
    fun getText(): String = _fullText.value

    private fun createRecognitionListener() = object : RecognitionListener {
        
        override fun onPartialResult(hypothesis: String?) {
            // CRITICAL: This is the real-time "see as you speak" feature
            hypothesis?.let { json ->
                try {
                    val result = JSONObject(json)
                    val partial = result.optString("partial", "")
                    if (partial.isNotBlank()) {
                        scope.launch {
                            _transcription.emit(TranscriptionEvent.Partial(partial))
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse partial result", e)
                }
            }
        }

        override fun onResult(hypothesis: String?) {
            // Final confirmed transcription for this utterance
            hypothesis?.let { json ->
                try {
                    val result = JSONObject(json)
                    val text = result.optString("text", "")
                    if (text.isNotBlank()) {
                        scope.launch {
                            _transcription.emit(TranscriptionEvent.Final(text))
                            // Append to full text with space
                            val current = _fullText.value
                            _fullText.value = if (current.isEmpty()) text else "$current $text"
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse result", e)
                }
            }
        }

        override fun onFinalResult(hypothesis: String?) {
            // Called when recognition is stopped
            onResult(hypothesis)
            _state.value = VoiceState.Idle
        }

        override fun onError(exception: Exception?) {
            Log.e(TAG, "Recognition error", exception)
            scope.launch {
                _state.value = VoiceState.Error(
                    "Recognition failed: ${exception?.message ?: "Unknown error"}",
                    canRetry = true
                )
            }
        }

        override fun onTimeout() {
            Log.i(TAG, "Recognition timeout (silence detected)")
            // Don't stop - just note the silence
            scope.launch {
                _transcription.emit(TranscriptionEvent.Silence(System.currentTimeMillis()))
            }
        }
    }

    /**
     * Release all resources
     */
    fun release() {
        stopListening()
        model?.close()
        model = null
        scope.cancel()
    }
}

/**
 * Extension: Punctuation enhancement (can be called on final text)
 * 
 * Vosk doesn't include punctuation by default.
 * This adds basic punctuation based on pauses and patterns.
 */
fun String.addBasicPunctuation(): String {
    // Simple heuristic punctuation
    return this
        .replace(Regex("\\s+"), " ")  // Normalize spaces
        .trim()
        .capitalizeFirst()
        .let { if (!it.endsWith(".") && !it.endsWith("?") && !it.endsWith("!")) "$it." else it }
}

private fun String.capitalizeFirst(): String {
    return if (isNotEmpty()) {
        this[0].uppercaseChar() + substring(1)
    } else this
}
