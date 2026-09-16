package com.jarvis.assistant.core.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

class TextToSpeechProvider(context: Context) {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false

    init {
        tts = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) tts?.language = Locale.getDefault()
        }
    }

    fun setLanguage(tag: String) {
        val parts = tag.split("-")
        val locale = if (parts.size == 2) Locale(parts[0], parts[1]) else Locale.getDefault()
        tts?.language = locale
    }

    fun setVoiceName(name: String) {
        if (name == "default" || !ready) return
        tts?.voices?.firstOrNull { it.name == name }?.let { tts?.voice = it }
    }

    fun availableVoices(): List<String> =
        tts?.voices?.map { it.name }?.sorted() ?: emptyList()

    fun speak(text: String) {
        if (ready) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    /** Suspends until speaking finishes (for sequential voice flows). */
    suspend fun speakAndWait(text: String): Unit = suspendCancellableCoroutine { cont ->
        if (!ready) { cont.resume(Unit); return@suspendCancellableCoroutine }
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onError(id: String?) { if (cont.isActive) cont.resume(Unit) }
            override fun onDone(id: String?) { if (cont.isActive) cont.resume(Unit) }
        })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_utt")
    }

    fun shutdown() { tts?.stop(); tts?.shutdown() }
}
