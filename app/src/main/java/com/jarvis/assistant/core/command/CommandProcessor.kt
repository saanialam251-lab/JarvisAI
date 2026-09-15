package com.jarvis.assistant.core.command

import com.jarvis.assistant.core.ai.AiProviderFactory
import com.jarvis.assistant.core.conversation.ConversationManager
import com.jarvis.assistant.core.executor.AccessibilityController
import com.jarvis.assistant.core.executor.ActionExecutor
import com.jarvis.assistant.core.safety.ConfirmationGate
import com.jarvis.assistant.core.voice.SpeechRecognizerProvider
import com.jarvis.assistant.core.voice.TextToSpeechProvider
import com.jarvis.assistant.data.monitors.BatteryMonitor
import com.jarvis.assistant.data.prefs.JarvisPrefs
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared brain used by BOTH the wake-word service and the chat screen:
 *   listen → understand (AI provider) → optional confirmation →
 *   execute steps → observe UI between steps → speak result.
 */
@Singleton
class CommandProcessor @Inject constructor(
    private val providers: AiProviderFactory,
    private val executor: ActionExecutor,
    private val conversation: ConversationManager,
    private val prefs: JarvisPrefs,
    private val tts: TextToSpeechProvider,
    private val stt: SpeechRecognizerProvider,
    private val gate: ConfirmationGate,
    private val battery: BatteryMonitor,
) {
    enum class UiState { IDLE, LISTENING, THINKING, EXECUTING, SPEAKING, ERROR }

    /** Full voice session: speak greeting → listen → plan → execute. */
    suspend fun processFromVoice(
        onListening: () -> Unit = {},
        onThinking: () -> Unit = {},
        onExecuting: (String) -> Unit = {},
    ) {
        val s = prefs.settings.first()
        tts.speak("Yes. How can I help you?")
        onListening()
        val command = stt.listenOnce(s.language) ?: run {
            tts.speak("I didn't catch that.")
            return
        }
        processText(command, onThinking, onExecuting)
    }

    /** Process an already-typed/spoken command. Returns spoken result. */
    suspend fun processText(
        command: String,
        onThinking: () -> Unit = {},
        onExecuting: (String) -> Unit = {},
    ): String {
        conversation.addUser(command)
        conversation.persist()
        onThinking()

        val s = prefs.settings.first()
        val uiContext = AccessibilityController.uiSummary()
        val plan = providers.current().plan(command, uiContext)

        val approved = gate.confirmIfNeeded(plan, s.askBeforeSensitive)
        if (!approved) {
            conversation.addAssistant("Cancelled.")
            return "Cancelled."
        }

        // Answer pure device queries conversationally
        if (plan.steps.size == 1 && plan.steps[0] is com.jarvis.assistant.core.planner.JarvisAction.QueryDevice) {
            val topic = (plan.steps[0] as com.jarvis.assistant.core.planner.JarvisAction.QueryDevice).topic
            val answer = deviceAnswer(topic)
            conversation.addAssistant(answer)
            conversation.persist()
            tts.speak(answer)
            return answer
        }

        val result = executor.execute(plan) { step -> onExecuting(step) }
        val reply = when (result) {
            is ActionExecutor.ExecResult.Ok -> result.spoken ?: "Done."
            is ActionExecutor.ExecResult.Fail -> "I couldn't complete that. ${result.reason}."
        }
        conversation.addAssistant(reply)
        conversation.persist()
        tts.speak(reply)
        return reply
    }

    private suspend fun deviceAnswer(topic: String): String = when (topic) {
        "battery" -> {
            battery.refreshOnce()
            val b = battery.state.value
            if (b == null) "I can't read the battery right now."
            else "Your battery is ${b.percent} percent" +
                (if (b.charging) ", charging" + (if (b.chargeCurrentMa != null) " at ${b.chargeCurrentMa} mA" else "") else ", discharging") +
                "."
        }
        else -> "Here's what I found for $topic."
    }
}
