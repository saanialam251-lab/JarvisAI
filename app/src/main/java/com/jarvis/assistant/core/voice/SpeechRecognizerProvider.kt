package com.jarvis.assistant.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class SpeechRecognizerProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var recognizer: SpeechRecognizer? = null

    /**
     * Listen for one utterance and return the transcript, or null on error.
     * Uses Android's on-device recognizer where available — no cloud cost.
     */
    suspend fun listenOnce(language: String = "en-US", timeoutMs: Int = 6000): String? =
        suspendCancellableCoroutine { cont ->
            try {
                if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                    if (cont.isActive) cont.resume(null); return@suspendCancellableCoroutine
                }
                recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onResults(results: Bundle?) {
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (cont.isActive) cont.resume(matches?.firstOrNull())
                            destroy()
                        }
                        override fun onError(error: Int) {
                            if (cont.isActive) cont.resume(null)
                            destroy()
                        }
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {}
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500)
                        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, timeoutMs.toLong())
                    startListening(intent)
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }

    fun cancel() { try { recognizer?.cancel(); recognizer?.destroy() } catch (_: Exception) {} }
}
