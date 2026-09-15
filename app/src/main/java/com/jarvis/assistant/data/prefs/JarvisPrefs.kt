package com.jarvis.assistant.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "jarvis_settings")

data class JarvisSettings(
    val wakeWordEnabled: Boolean = true,
    val backgroundListening: Boolean = false,
    val sensitivity: Float = 0.5f,
    val language: String = "en-US",
    val assistantVoice: String = "default",
    val askBeforeSensitive: Boolean = true,
    val aiProvider: String = "local",          // local | cloud
    val apiKey: String = "",
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-4o-mini",
    val overlayEnabled: Boolean = true,
)

@Singleton
class JarvisPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object K {
        val WAKE = booleanPreferencesKey("wake_word")
        val BG = booleanPreferencesKey("background")
        val SENS = floatPreferencesKey("sensitivity")
        val LANG = stringPreferencesKey("language")
        val VOICE = stringPreferencesKey("voice")
        val ASK = booleanPreferencesKey("ask_sensitive")
        val PROVIDER = stringPreferencesKey("ai_provider")
        val APIKEY = stringPreferencesKey("api_key")
        val BASEURL = stringPreferencesKey("base_url")
        val MODEL = stringPreferencesKey("model")
        val OVERLAY = booleanPreferencesKey("overlay")
        val HISTORY = stringPreferencesKey("conversation_history")
    }

    val settings: Flow<JarvisSettings> = context.dataStore.data.map { p ->
        JarvisSettings(
            wakeWordEnabled = p[K.WAKE] ?: true,
            backgroundListening = p[K.BG] ?: false,
            sensitivity = p[K.SENS] ?: 0.5f,
            language = p[K.LANG] ?: "en-US",
            assistantVoice = p[K.VOICE] ?: "default",
            askBeforeSensitive = p[K.ASK] ?: true,
            aiProvider = p[K.PROVIDER] ?: "local",
            apiKey = p[K.APIKEY] ?: "",
            baseUrl = p[K.BASEURL] ?: "https://api.openai.com/v1",
            model = p[K.MODEL] ?: "gpt-4o-mini",
            overlayEnabled = p[K.OVERLAY] ?: true,
        )
    }

    val conversationHistory: Flow<String> = context.dataStore.data.map { it[K.HISTORY] ?: "" }

    suspend fun setConversationHistory(json: String) { context.dataStore.edit { it[K.HISTORY] = json } }

    suspend fun update(transform: (JarvisSettings) -> JarvisSettings) {
        val cur = settings.first()
        val n = transform(cur)
        context.dataStore.edit { p ->
            p[K.WAKE] = n.wakeWordEnabled; p[K.BG] = n.backgroundListening
            p[K.SENS] = n.sensitivity; p[K.LANG] = n.language; p[K.VOICE] = n.assistantVoice
            p[K.ASK] = n.askBeforeSensitive; p[K.PROVIDER] = n.aiProvider; p[K.APIKEY] = n.apiKey
            p[K.BASEURL] = n.baseUrl; p[K.MODEL] = n.model; p[K.OVERLAY] = n.overlayEnabled
        }
    }
}
