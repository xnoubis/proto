# FlowVoice

**Accessible Voice Dictation for Android**

Voice-to-text that respects your constraints: offline-first, real-time display, minimal interaction.

## Design Principles

1. **See Words As Spoken** - Real-time streaming transcription, not batch
2. **Offline Primary** - Works without internet; cloud is optional enhancement
3. **One-Tap Interaction** - Giant mic button, no precise clicking required
4. **Accessibility Spec** - If it hurts to use, it doesn't exist

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                      FlowVoice                          │
├─────────────────────────────────────────────────────────┤
│  UI Layer (Compose)                                     │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │
│  │ Text Display│  │  Mic Button │  │  Settings   │     │
│  │ (real-time) │  │ (120dp tap) │  │  (modes)    │     │
│  └─────────────┘  └─────────────┘  └─────────────┘     │
├─────────────────────────────────────────────────────────┤
│  Engine Layer                                           │
│  ┌────────────────────┐  ┌────────────────────┐        │
│  │    VoiceEngine     │  │  CloudTranscriber  │        │
│  │  (Vosk Offline)    │  │  (Whisper API)     │        │
│  │                    │  │                    │        │
│  │  • Streaming API   │  │  • Retry logic     │        │
│  │  • 50MB model      │  │  • Fallback only   │        │
│  │  • Real-time       │  │  • Post-process    │        │
│  └────────────────────┘  └────────────────────┘        │
└─────────────────────────────────────────────────────────┘
```

## Transcription Modes

| Mode | Offline | Cloud | Use Case |
|------|---------|-------|----------|
| **Offline Only** 🔒 | Primary | Never | Privacy, no internet |
| **Hybrid Auto** ⚡ | Primary | On error | Reliability |
| **Hybrid Enhanced** ✨ | Real-time | Post-process | Best accuracy |
| **Cloud Only** ☁️ | Never | Always | Maximum accuracy |

## Requirements

- Android 9+ (API 28+)
- Microphone permission
- ~60MB storage for Vosk model
- Internet only for cloud modes

## Setup Instructions

### Development Environment

1. **Install Android Studio** (Ladybug or newer)
   - Download: https://developer.android.com/studio
   - Install Kotlin plugin if prompted

2. **Clone/Copy Project**
   ```bash
   # If using git
   git clone <repo-url>
   cd flowvoice
   
   # Or copy the flowvoice directory to your workspace
   ```

3. **Download Vosk Model**
   
   The app needs a Vosk speech model. Download the lightweight English model:
   
   ```bash
   # Create assets directory
   mkdir -p app/src/main/assets
   
   # Download model (50MB)
   wget https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
   
   # Extract to assets
   unzip vosk-model-small-en-us-0.15.zip -d app/src/main/assets/
   mv app/src/main/assets/vosk-model-small-en-us-0.15 app/src/main/assets/model-en-us
   ```
   
   Or download manually from: https://alphacephei.com/vosk/models

4. **Open in Android Studio**
   - File → Open → Select `flowvoice` directory
   - Wait for Gradle sync to complete
   - If prompted about SDK, install recommended versions

5. **Connect Your Device**
   - Enable Developer Options on Samsung Galaxy Z Fold 7
   - Enable USB Debugging
   - Connect via USB cable
   - Allow debugging when prompted on phone

6. **Build and Run**
   - Click green "Run" button (▶️)
   - Select your device
   - Wait for build and installation
   - Grant microphone permission when prompted

### Cloud Setup (Optional)

To enable cloud transcription (Whisper API):

1. Get OpenAI API key: https://platform.openai.com/api-keys
2. Add to app settings (future: settings screen)
3. Currently hardcoded - modify `CloudTranscriber.kt`

## Usage

1. **Launch FlowVoice**
2. **Grant microphone permission** (first launch only)
3. **Tap the big mic button** 🎤
4. **Speak** - words appear in real-time
5. **Tap again** to stop
6. **Tap the text** to copy to clipboard
7. **Paste** anywhere you need the text

### Tips

- Speak clearly at normal pace
- Pause between sentences for natural breaks
- The partial (in-progress) text shows in blue
- Final confirmed text shows in black
- Tap ✕ to clear and start fresh

## Troubleshooting

### "Model initialization failed"
- Ensure the Vosk model is in `app/src/main/assets/model-en-us/`
- Check the model directory contains `am/`, `conf/`, etc.

### No text appearing
- Check microphone permission in Android Settings
- Ensure no other app is using microphone
- Try restarting the app

### Slow/laggy transcription
- Normal for first few seconds while model warms up
- Should stabilize after initial use
- Check device isn't in power saver mode

### Build errors
- Ensure Java 17 is installed and configured
- Run "Invalidate Caches / Restart" in Android Studio
- Check Gradle sync completed successfully

## Future Roadmap

- [ ] Keyboard integration via Accessibility Service
- [ ] Voice activation ("Hey FlowVoice")
- [ ] Punctuation model integration
- [ ] Multiple language support
- [ ] Widget for quick access
- [ ] Wear OS companion

## Technical Notes

### Why Vosk over Whisper?

| Vosk | Whisper (local) |
|------|-----------------|
| True streaming (real-time) | Batch processing |
| 50MB model | 140MB+ model |
| Fast on CPU | Needs GPU for speed |
| Good accuracy | Better accuracy |
| See words as spoken ✓ | Wait for completion ✗ |

Whisper.cpp on Android struggles with real-time streaming (5x slower than real-time per benchmarks). Vosk's streaming API was built for this use case.

### Hybrid Architecture

Best of both:
1. Vosk provides real-time experience
2. Whisper (cloud) post-processes for accuracy when available
3. Fallback ensures reliability

---

Built with accessibility-first design. If it hurts to use, it doesn't exist.
