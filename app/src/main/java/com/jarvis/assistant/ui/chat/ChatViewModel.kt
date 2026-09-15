package com.jarvis.assistant.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.core.command.CommandProcessor
import com.jarvis.assistant.core.conversation.ConversationManager
import com.jarvis.assistant.core.safety.ConfirmationGate
import com.jarvis.assistant.core.voice.SpeechRecognizerProvider
import com.jarvis.assistant.data.prefs.JarvisPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val listening: Boolean = false,
    val busy: Boolean = false,
    val confirmation: ConfirmationGate.Request? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val processor: CommandProcessor,
    private val conversation: ConversationManager,
    private val stt: SpeechRecognizerProvider,
    private val gate: ConfirmationGate,
    prefs: JarvisPrefs,
) : ViewModel() {

    val messages = conversation.messages.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _ui = MutableStateFlow(ChatUiState())
    val ui: StateFlow<ChatUiState> = _ui

    init {
        viewModelScope.launch { conversation.load() }
        viewModelScope.launch {
            gate.requests.collect { req -> _ui.value = _ui.value.copy(confirmation = req) }
        }
    }

    fun send(text: String) {
        if (text.isBlank()) return
        _ui.value = _ui.value.copy(busy = true)
        viewModelScope.launch {
            processor.processText(text)
            _ui.value = _ui.value.copy(busy = false)
        }
    }

    fun toggleVoice() {
        if (_ui.value.listening || _ui.value.busy) return
        _ui.value = _ui.value.copy(listening = true, busy = true)
        viewModelScope.launch {
            processor.processFromVoice(
                onListening = { _ui.value = _ui.value.copy(listening = true) },
                onThinking = { _ui.value = _ui.value.copy(listening = false) },
            )
            _ui.value = _ui.value.copy(listening = false, busy = false)
        }
    }

    fun resolveConfirmation(approved: Boolean) {
        val req = _ui.value.confirmation ?: return
        _ui.value = _ui.value.copy(confirmation = null)
        req.deferred.complete(approved)
    }
}
