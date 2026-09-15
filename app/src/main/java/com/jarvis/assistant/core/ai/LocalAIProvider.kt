package com.jarvis.assistant.core.ai

import com.jarvis.assistant.core.planner.RuleBasedPlanner
import com.jarvis.assistant.core.planner.TaskPlan
import javax.inject.Inject
import javax.inject.Singleton

/** Offline planner — zero cost, zero API key, always available. */
@Singleton
class LocalAIProvider @Inject constructor(
    private val rules: RuleBasedPlanner,
) : AIProvider {
    override val name = "local"
    override suspend fun plan(command: String, uiContext: String): TaskPlan = rules.plan(command)
}
