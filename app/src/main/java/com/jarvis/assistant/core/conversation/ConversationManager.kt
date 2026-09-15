package com.jarvis.assistant.core.conversation

import com.jarvis.assistant.data.prefs.JarvisPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class ChatMessage(val role: String, val text: String, val time: Long = System.currentTimeMillis())

@Singleton
class ConversationManager @Inject constructor(
    private val prefs: JarvisPrefs,
) {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    fun addUser(text: String) { _messages.value = _messages.value + ChatMessage("user", text) }
    fun addAssistant(text: String) { _messages.value = _messages.value + ChatMessage("jarvis", text) }

    fun recentCommands(limit: Int = 5): List<String> =
        _messages.value.filter { it.role == "user" }.takeLast(limit).map { it.text }

    suspend fun load() {
        try {
            val json = prefs.conversationHistory.first()
            if (json.isBlank()) return
            val arr = JSONArray(json)
            val list = mutableListOf<ChatMessage>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list += ChatMessage(o.getString("role"), o.getString("text"), o.optLong("time"))
            }
            _messages.value = list
        } catch (_: Exception) { }
    }

    suspend fun persist() {
        val arr = JSONArray()
        _messages.value.takeLast(200).forEach { m ->
            arr.put(JSONObject().put("role", m.role).put("text", m.text).put("time", m.time))
        }
        prefs.setConversationHistory(arr.toString())
    }

    /** Privacy control #17: delete conversation history. */
    suspend fun clear() {
        _messages.value = emptyList()
        prefs.setConversationHistory("")
    }
}
