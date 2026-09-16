package com.jarvis.assistant.core.planner

import org.json.JSONArray
import org.json.JSONObject

/**
 * Every action Jarvis can execute. The AI layer produces THESE structured
 * actions — never arbitrary executable code (requirement #15/#20).
 */
sealed interface JarvisAction {
    data class LaunchApp(val app: String) : JarvisAction
    data class TapText(val text: String, val exact: Boolean = false) : JarvisAction
    data class TapViewDesc(val desc: String) : JarvisAction
    data class TypeText(val text: String) : JarvisAction
    data object SubmitSearch : JarvisAction
    data object PressBack : JarvisAction
    data class Wait(val ms: Long) : JarvisAction
    data class ScrollForward(val targetText: String? = null) : JarvisAction
    data class QueryDevice(val topic: String) : JarvisAction
    data class Speak(val text: String) : JarvisAction
    data class SetVolume(val percent: Int) : JarvisAction
    data class SetBrightness(val percent: Int) : JarvisAction
    data class CallContact(val name: String) : JarvisAction
    data class ToggleSpeaker(val on: Boolean) : JarvisAction
    data class OpenWebsite(val query: String) : JarvisAction
    data object OpenPowerMenu : JarvisAction

    val description: String
        get() = when (this) {
            is LaunchApp -> "Opening $app"
            is TapText -> "Tapping '$text'"
            is TapViewDesc -> "Tapping $desc button"
            is TypeText -> "Typing '${text.take(40)}'"
            SubmitSearch -> "Submitting search"
            PressBack -> "Going back"
            is Wait -> "Waiting ${ms}ms"
            is ScrollForward -> if (targetText != null) "Scrolling to find '$targetText'" else "Scrolling"
            is QueryDevice -> "Checking $topic"
            is Speak -> text
            is SetVolume -> "Setting volume to $percent%"
            is SetBrightness -> "Setting brightness to $percent%"
            is CallContact -> "Calling $name"
            is ToggleSpeaker -> if (on) "Turning speaker on" else "Turning speaker off"
            is OpenWebsite -> "Opening $query"
            OpenPowerMenu -> "Opening power menu"
        }
}

data class TaskPlan(
    val goal: String,
    val steps: List<JarvisAction>,
    val sensitive: Boolean = false,
) {
    fun toJson(): String {
        val arr = JSONArray()
        steps.forEach { s ->
            val o = JSONObject()
            when (s) {
                is JarvisAction.LaunchApp -> o.put("action", "launch_app").put("app", s.app)
                is JarvisAction.TapText -> o.put("action", "tap_text").put("text", s.text).put("exact", s.exact)
                is JarvisAction.TapViewDesc -> o.put("action", "tap_desc").put("desc", s.desc)
                is JarvisAction.TypeText -> o.put("action", "type_text").put("text", s.text)
                JarvisAction.SubmitSearch -> o.put("action", "submit_search")
                JarvisAction.PressBack -> o.put("action", "back")
                is JarvisAction.Wait -> o.put("action", "wait").put("ms", s.ms)
                is JarvisAction.ScrollForward -> o.put("action", "scroll").put("target", s.targetText ?: "")
                is JarvisAction.QueryDevice -> o.put("action", "query_device").put("topic", s.topic)
                is JarvisAction.Speak -> o.put("action", "speak").put("text", s.text)
                is JarvisAction.SetVolume -> o.put("action", "set_volume").put("percent", s.percent)
                is JarvisAction.SetBrightness -> o.put("action", "set_brightness").put("percent", s.percent)
                is JarvisAction.CallContact -> o.put("action", "call_contact").put("name", s.name)
                is JarvisAction.ToggleSpeaker -> o.put("action", "toggle_speaker").put("on", s.on)
                is JarvisAction.OpenWebsite -> o.put("action", "open_website").put("query", s.query)
                JarvisAction.OpenPowerMenu -> o.put("action", "open_power_menu")
            }
            arr.put(o)
        }
        return JSONObject().put("goal", goal).put("sensitive", sensitive).put("steps", arr).toString()
    }

    companion object {
        fun fromJson(json: String): TaskPlan {
            val root = JSONObject(json)
            val steps = mutableListOf<JarvisAction>()
            val arr = root.optJSONArray("steps") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                steps += when (o.getString("action")) {
                    "launch_app" -> JarvisAction.LaunchApp(o.getString("app"))
                    "tap_text" -> JarvisAction.TapText(o.getString("text"), o.optBoolean("exact", false))
                    "tap_desc" -> JarvisAction.TapViewDesc(o.getString("desc"))
                    "type_text" -> JarvisAction.TypeText(o.getString("text"))
                    "submit_search" -> JarvisAction.SubmitSearch
                    "back" -> JarvisAction.PressBack
                    "wait" -> JarvisAction.Wait(o.optLong("ms", 500))
                    "scroll" -> JarvisAction.ScrollForward(o.optString("target").ifBlank { null })
                    "query_device" -> JarvisAction.QueryDevice(o.getString("topic"))
                    "speak" -> JarvisAction.Speak(o.getString("text"))
                    "set_volume" -> JarvisAction.SetVolume(o.optInt("percent", 50))
                    "set_brightness" -> JarvisAction.SetBrightness(o.optInt("percent", 50))
                    "call_contact" -> JarvisAction.CallContact(o.getString("name"))
                    "toggle_speaker" -> JarvisAction.ToggleSpeaker(o.optBoolean("on", true))
                    "open_website" -> JarvisAction.OpenWebsite(o.getString("query"))
                    "open_power_menu" -> JarvisAction.OpenPowerMenu
                    else -> continue
                }
            }
            return TaskPlan(root.optString("goal", "task"), steps, root.optBoolean("sensitive", false))
        }
    }
}
