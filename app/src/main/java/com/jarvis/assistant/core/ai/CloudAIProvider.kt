package com.jarvis.assistant.core.ai

import com.jarvis.assistant.core.planner.TaskPlan
import com.jarvis.assistant.data.prefs.JarvisPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional OpenAI-compatible provider. If the network call or parsing
 * fails for ANY reason, it falls back to the local planner — so Jarvis
 * keeps working with no internet and no API credits (your request).
 */
@Singleton
class CloudAIProvider @Inject constructor(
    private val prefs: JarvisPrefs,
    private val fallback: LocalAIProvider,
) : AIProvider {
    override val name = "cloud"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun plan(command: String, uiContext: String): TaskPlan = withContext(Dispatchers.IO) {
        try {
            val s = prefs.settings.first()
            if (s.apiKey.isBlank()) return@withContext fallback.plan(command, uiContext)

            val system = "You are Jarvis, an Android assistant. Convert the user command into a " +
                "JSON plan with fields: goal (string), sensitive (boolean), steps (array of objects " +
                "with an 'action' field). Allowed actions: launch_app{app}, tap_text{text,exact}, " +
                "tap_desc{desc}, type_text{text}, submit_search, back, wait{ms}, scroll{target}, " +
                "query_device{topic: battery|storage|network}, speak{text}. " +
                "Known app names: whatsapp, youtube, chrome, instagram, gmail, settings, spotify, maps. " +
                "Maximum 6 steps. Current screen (may be empty): " + uiContext.take(1500)

            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", command))

            val body = JSONObject()
                .put("model", s.model)
                .put("messages", messages)
                .put("response_format", JSONObject().put("type", "json_object"))
                .toString()

            val req = Request.Builder()
                .url(s.baseUrl.trimEnd('/') + "/chat/completions")
                .header("Authorization", "Bearer ${s.apiKey}")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext fallback.plan(command, uiContext)
                val root = JSONObject(resp.body!!.string())
                val content = root.getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content")
                TaskPlan.fromJson(content)
            }
        } catch (e: Exception) {
            fallback.plan(command, uiContext)
        }
    }
}
