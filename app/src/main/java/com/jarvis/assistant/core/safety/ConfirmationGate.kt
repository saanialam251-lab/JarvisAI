package com.jarvis.assistant.core.safety

import com.jarvis.assistant.core.planner.JarvisAction
import com.jarvis.assistant.core.planner.TaskPlan
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Requirement #13: dangerous/irreversible actions require confirmation.
 * Low-risk actions (open app / search / media) never prompt.
 */
@Singleton
class ConfirmationGate @Inject constructor() {

    data class Request(val summary: String, val deferred: CompletableDeferred<Boolean>)

    val requests = MutableSharedFlow<Request>(extraBufferCapacity = 4)

    private val sensitiveActions = setOf("send_message", "purchase", "delete", "pay", "transfer")

    fun isSensitive(plan: TaskPlan): Boolean {
        if (plan.sensitive) return true
        return plan.steps.any { s ->
            when (s) {
                is JarvisAction.TapViewDesc -> sensitiveActions.any { s.desc.contains(it, true) }
                is JarvisAction.TapText -> sensitiveActions.any { s.text.contains(it, true) }
                else -> false
            }
        }
    }

    /** If ask-before-sensitive is ON and the plan is sensitive → ask the user. */
    suspend fun confirmIfNeeded(plan: TaskPlan, askEnabled: Boolean): Boolean {
        if (!askEnabled || !isSensitive(plan)) return true
        val d = CompletableDeferred<Boolean>()
        requests.emit(Request("Jarvis wants to: ${plan.goal}", d))
        return d.await()
    }
}
