package com.flowvoice

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.flowvoice.engine.TranscriptionMode
import com.flowvoice.engine.VoiceEngine
import com.flowvoice.engine.VoiceEngine.VoiceState
import com.flowvoice.engine.VoiceEngine.TranscriptionEvent
import com.flowvoice.engine.addBasicPunctuation
import com.flowvoice.ui.theme.FlowVoiceTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * FlowVoice - Accessible Voice Dictation
 * 
 * ACCESSIBILITY DESIGN:
 * - One giant tap target for mic (no precise clicking)
 * - Real-time text display (see words as spoken)
 * - Minimal interaction required
 * - Copy to clipboard with single tap on text
 * - Voice activation support (future)
 */
class MainActivity : ComponentActivity() {
    
    private lateinit var voiceEngine: VoiceEngine
    
    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            initializeEngine()
        } else {
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        voiceEngine = VoiceEngine(applicationContext)
        
        // Request mic permission
        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        
        setContent {
            FlowVoiceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FlowVoiceScreen(voiceEngine)
                }
            }
        }
    }

    private fun initializeEngine() {
        lifecycleScope.launch {
            voiceEngine.initialize()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceEngine.release()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowVoiceScreen(voiceEngine: VoiceEngine) {
    val context = LocalContext.current
    val state by voiceEngine.state.collectAsState()
    val fullText by voiceEngine.fullText.collectAsState()
    
    // Track partial (real-time) text separately for display
    var partialText by remember { mutableStateOf("") }
    
    // Collect transcription events
    LaunchedEffect(Unit) {
        voiceEngine.transcription.collectLatest { event ->
            when (event) {
                is TranscriptionEvent.Partial -> {
                    partialText = event.text
                }
                is TranscriptionEvent.Final -> {
                    partialText = "" // Clear partial on final
                }
                is TranscriptionEvent.Silence -> {
                    // Could show visual indicator
                }
            }
        }
    }
    
    // Settings state
    var showSettings by remember { mutableStateOf(false) }
    var transcriptionMode by remember { mutableStateOf(TranscriptionMode.OFFLINE_ONLY) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "FlowVoice",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            
            IconButton(onClick = { showSettings = true }) {
                Text("⚙️", fontSize = 24.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Mode indicator
        ModeIndicator(mode = transcriptionMode, state = state)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // MAIN TEXT DISPLAY - Tappable to copy
        TextDisplay(
            finalText = fullText,
            partialText = partialText,
            onCopy = { text ->
                copyToClipboard(context, text)
                Toast.makeText(context, "Copied!", Toast.LENGTH_SHORT).show()
            },
            onClear = {
                voiceEngine.clearText()
            },
            modifier = Modifier.weight(1f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // BIG MIC BUTTON - Main interaction
        MicButton(
            state = state,
            onClick = { voiceEngine.toggleListening() },
            modifier = Modifier.size(120.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Status text
        StatusText(state = state)
        
        Spacer(modifier = Modifier.height(24.dp))
    }
    
    // Settings bottom sheet
    if (showSettings) {
        SettingsSheet(
            mode = transcriptionMode,
            onModeChange = { transcriptionMode = it },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
fun ModeIndicator(mode: TranscriptionMode, state: VoiceState) {
    val modeText = when (mode) {
        TranscriptionMode.OFFLINE_ONLY -> "🔒 Offline"
        TranscriptionMode.HYBRID_AUTO -> "⚡ Hybrid"
        TranscriptionMode.HYBRID_ENHANCE -> "✨ Enhanced"
        TranscriptionMode.CLOUD_ONLY -> "☁️ Cloud"
    }
    
    val stateColor = when (state) {
        VoiceState.Idle -> Color.Gray
        VoiceState.Loading -> Color.Yellow
        VoiceState.Listening -> Color.Green
        VoiceState.Processing -> Color.Blue
        is VoiceState.Error -> Color.Red
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(stateColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = modeText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun TextDisplay(
    finalText: String,
    partialText: String,
    onCopy: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val displayText = if (partialText.isNotBlank()) {
        if (finalText.isNotBlank()) "$finalText $partialText" else partialText
    } else {
        finalText
    }
    
    // Auto-scroll to bottom when text changes
    LaunchedEffect(displayText) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { 
                if (finalText.isNotBlank()) {
                    onCopy(finalText.addBasicPunctuation())
                }
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(scrollState)
            ) {
                if (displayText.isBlank()) {
                    Text(
                        text = "Tap the microphone to start speaking.\n\nYour words will appear here in real-time.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Show final text in normal weight
                    if (finalText.isNotBlank()) {
                        Text(
                            text = finalText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    // Show partial (real-time) text in different style
                    if (partialText.isNotBlank()) {
                        Text(
                            text = if (finalText.isNotBlank()) " $partialText" else partialText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            // Clear button (only if text exists)
            if (finalText.isNotBlank()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Text("✕", fontSize = 20.sp)
                }
            }
            
            // Copy hint
            if (finalText.isNotBlank()) {
                Text(
                    text = "Tap to copy",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun MicButton(
    state: VoiceState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isListening = state == VoiceState.Listening
    
    // Pulsing animation when listening
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.1f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val backgroundColor = when (state) {
        VoiceState.Listening -> MaterialTheme.colorScheme.error
        VoiceState.Loading -> MaterialTheme.colorScheme.surfaceVariant
        is VoiceState.Error -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.primary
    }
    
    val iconEmoji = when (state) {
        VoiceState.Listening -> "⏹️"  // Stop
        VoiceState.Loading -> "⏳"
        is VoiceState.Error -> "🔄"    // Retry
        else -> "🎤"
    }
    
    Box(
        modifier = modifier
            .scale(if (isListening) scale else 1f)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(enabled = state != VoiceState.Loading) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = iconEmoji,
            fontSize = 48.sp
        )
    }
}

@Composable
fun StatusText(state: VoiceState) {
    val text = when (state) {
        VoiceState.Idle -> "Tap to speak"
        VoiceState.Loading -> "Loading model..."
        VoiceState.Listening -> "Listening... Tap to stop"
        VoiceState.Processing -> "Processing..."
        is VoiceState.Error -> "Error: ${state.message}\nTap to retry"
    }
    
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (state is VoiceState.Error) 
            MaterialTheme.colorScheme.error 
        else 
            MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    mode: TranscriptionMode,
    onModeChange: (TranscriptionMode) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Transcription Mode",
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            TranscriptionMode.entries.forEach { modeOption ->
                val (title, description) = when (modeOption) {
                    TranscriptionMode.OFFLINE_ONLY -> 
                        "Offline Only" to "🔒 Private. Works without internet. Good accuracy."
                    TranscriptionMode.HYBRID_AUTO -> 
                        "Hybrid Auto" to "⚡ Offline primary, cloud backup on errors."
                    TranscriptionMode.HYBRID_ENHANCE -> 
                        "Hybrid Enhanced" to "✨ Real-time offline, cloud post-processing for accuracy."
                    TranscriptionMode.CLOUD_ONLY -> 
                        "Cloud Only" to "☁️ Best accuracy. Requires internet."
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onModeChange(modeOption) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = mode == modeOption,
                        onClick = { onModeChange(modeOption) }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = title, fontWeight = FontWeight.Medium)
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("FlowVoice", text)
    clipboard.setPrimaryClip(clip)
}
