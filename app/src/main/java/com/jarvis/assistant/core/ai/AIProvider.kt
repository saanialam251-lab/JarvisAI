package com.jarvis.assistant.core.ai

import com.jarvis.assistant.core.planner.TaskPlan

/**
 * Requirement #16: pluggable AI. Swap local / cloud / custom freely.
 * The app never hard-codes one vendor, and works offline with the
 * built-in rule-based LocalAIProvider.
 */
interface AIProvider {
    val name: String
    suspend fun plan(command: String, uiContext: String): TaskPlan
}
