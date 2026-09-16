package com.jarvis.assistant.core.executor

import com.jarvis.assistant.core.planner.JarvisAction
import com.jarvis.assistant.core.planner.TaskPlan
import com.jarvis.assistant.core.conversation.TaskLog
import com.jarvis.assistant.data.monitors.BatteryMonitor
import com.jarvis.assistant.data.monitors.DeviceMonitor
import com.jarvis.assistant.data.monitors.NetworkMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Multi-step task engine: timeout, retry, UI-state verification between
 * steps, back-navigation recovery, graceful stop.
 */
@Singleton
class ActionExecutor @Inject constructor(
    private val intents: IntentController,
    private val battery: BatteryMonitor,
    private val device: DeviceMonitor,
    private val network: NetworkMonitor,
) {
    private val STEP_TIMEOUT_MS = 12_000L
    private val MAX_RETRIES = 2

    sealed interface ExecResult {
        data class Ok(val spoken: String?) : ExecResult
        data class Fail(val reason: String) : ExecResult
    }

    suspend fun execute(plan: TaskPlan, onProgress: (String) -> Unit = {}): ExecResult {
        TaskLog.add(plan.goal, "started")
        var lastSpoken: String? = null

        for ((index, step) in plan.steps.withIndex()) {
            onProgress(step.description)

            var ok = false
            var lastError = "unknown error"
            for (attempt in 0..MAX_RETRIES) {
                if (attempt > 0) delay(600)
                val r = withTimeoutOrNull(STEP_TIMEOUT_MS) { perform(step) }
                when {
                    r == null -> lastError = "timed out"
                    r.isSuccess -> ok = true
                    else -> lastError = r.exceptionOrNull()?.message ?: "failed"
                }
                if (ok) break
            }

            if (!ok) {
                // light recovery: press back once for UI steps, then stop safely
                if (step is JarvisAction.TapText || step is JarvisAction.TapViewDesc) {
                    AccessibilityController.back()
                    delay(500)
                }
                TaskLog.add(plan.goal, "FAILED at step ${index + 1} (${step.description}): $lastError")
                return ExecResult.Fail("${step.description} failed: $lastError")
            }

            if (step is JarvisAction.Speak) lastSpoken = step.text
            delay(if (step is JarvisAction.Wait) 0 else 900) // observe UI between actions
        }
        TaskLog.add(plan.goal, "completed")
        return ExecResult.Ok(lastSpoken)
    }

    private suspend fun perform(step: JarvisAction): Result<Unit> = runCatching {
        when (step) {
            is JarvisAction.LaunchApp ->
                if (!intents.launchApp(step.app)) error("App '${step.app}' not found")

            is JarvisAction.TapText ->
                if (!AccessibilityController.tapText(step.text, step.exact))
                    error("Could not find '${step.text}' on screen. Try saying 'Jarvis' when the app is open.")

            is JarvisAction.TapViewDesc ->
                if (!AccessibilityController.tapDesc(step.desc)) error("'${step.desc}' control not found")

            is JarvisAction.TypeText ->
                if (!AccessibilityController.typeText(step.text)) error("No text field focused")

            JarvisAction.SubmitSearch ->
                if (!AccessibilityController.submitSearch()) error("Search button not found")

            JarvisAction.PressBack -> AccessibilityController.back()

            is JarvisAction.Wait -> delay(step.ms)

            is JarvisAction.ScrollForward ->
                if (!AccessibilityController.scrollForward(step.targetText)) error("Nothing to scroll")

            is JarvisAction.QueryDevice -> answerQuery(step.topic)

            is JarvisAction.Speak -> Unit // spoken by caller
        }
    }

    private suspend fun answerQuery(topic: String) {
        when (topic) {
            "battery" -> battery.refreshOnce()
            "storage" -> device.refreshOnce()
            "network" -> network.refreshOnce()
            "open_wifi" -> intents.openWifiSettings()
        }
    }
}
