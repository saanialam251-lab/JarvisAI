package com.jarvis.assistant.core.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.SpeechStreamService

/**
 * Always-listening, on-device wake word engine ("Jarvis").
 * Grammar-restricted recognizer → very low CPU, no audio ever leaves
 * the device for wake-word detection.
 */
class VoskWakeWordEngine(
    private val context: Context,
    private val wakeWord: String = "jarvis",
    private val onWake: () -> Unit,
) {
    private var speechService: SpeechService? = null
    private var model: Model? = null
    @Volatile private var running = false
    @Volatile private var paused = false

    fun start() {
        if (running) return
        val dir = ModelManager.modelDir(context)
        if (!ModelManager(context).isModelReady()) {
            Log.w("WakeWord", "Vosk model missing — download it in Settings")
            return
        }
        try {
            model = Model(dir.absolutePath)
            val recognizer = Recognizer(model, 16000.0f, "[\"$wakeWord\", \"[unk]\"]")
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(listener)
            running = true
        } catch (e: Exception) {
            Log.e("WakeWord", "start failed", e)
        }
    }

    fun pause() { paused = true; try { speechService?.stop() } catch (_: Exception) {} }
    fun resume() {
        paused = false
        try { speechService?.startListening(listener) } catch (_: Exception) {}
    }

    fun stop() {
        running = false
        try { speechService?.stop(); speechService?.shutdown() } catch (_: Exception) {}
        speechService = null
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            if (paused) return
            hypothesis?.let {
                try {
                    val text = JSONObject(it).optString("partial", "")
                    if (text.contains(wakeWord, true)) { paused = true; onWake() }
                } catch (_: Exception) {}
            }
        }
        override fun onResult(hypothesis: String?) {
            if (paused) return
            hypothesis?.let {
                try {
                    val text = JSONObject(it).optString("text", "")
                    if (text.contains(wakeWord, true)) { paused = true; onWake() }
                } catch (_: Exception) {}
            }
        }
        override fun onFinalResult(hypothesis: String?) {}
        override fun onError(e: Exception?) {}
        override fun onTimeout() { if (!paused) try { speechService?.startListening(this) } catch (_: Exception) {} }
    }
}
