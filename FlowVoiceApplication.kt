package com.flowvoice

import android.app.Application
import android.util.Log

/**
 * FlowVoice Application
 * 
 * Early initialization for:
 * - Model pre-loading (future optimization)
 * - Crash reporting setup
 * - Dependency injection (if needed)
 */
class FlowVoiceApplication : Application() {
    
    companion object {
        private const val TAG = "FlowVoice"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "FlowVoice starting...")
        
        // Future: Pre-load model in background
        // Future: Initialize crash reporting
        // Future: Setup DI container
    }
}
