package com.jarvis.assistant.core.ai

import com.jarvis.assistant.data.prefs.JarvisPrefs
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiProviderFactory @Inject constructor(
    private val local: LocalAIProvider,
    private val cloud: CloudAIProvider,
    private val prefs: JarvisPrefs,
) {
    /** Picks the configured provider; silently falls back to local. */
    suspend fun current(): AIProvider =
        if (prefs.settings.first().aiProvider == "cloud" &&
            prefs.settings.first().apiKey.isNotBlank()) cloud else local
}
