package com.jarvis.assistant.core.planner

import com.jarvis.assistant.core.executor.IntentController
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 100% local, offline, zero-API-cost planner. Understands the command
 * patterns from the PDF spec and converts them into structured multi-step
 * TaskPlans (up to 5–6+ actions per command).
 */
@Singleton
class RuleBasedPlanner @Inject constructor(
    private val intents: IntentController,
) {
    fun plan(command: String): TaskPlan {
        val c = command.trim()
            .removePrefix("jarvis").removePrefix("Jarvis")
            .trim().removeSuffix(".").trim()

        // --- device queries (answered without any app) ---
        when {
            c.matches(Regex("(?i).*(battery|charge).*")) ->
                return TaskPlan(c, listOf(JarvisAction.QueryDevice("battery")))
            c.matches(Regex("(?i).*(storage|space|memory ram).*")) ->
                return TaskPlan(c, listOf(JarvisAction.QueryDevice("storage")))
            c.matches(Regex("(?i).*(wifi|network|internet).*status.*")) ->
                return TaskPlan(c, listOf(JarvisAction.QueryDevice("network")))
        }

        // --- "send <name> a message saying <text>" (WhatsApp) ---
        Regex("(?i)send (.+?) an? (?:whatsapp )?message (?:saying|that says) (.+)")
            .find(c)?.let { m ->
                val (name, text) = m.destructured
                return TaskPlan(
                    c,
                    listOf(
                        JarvisAction.LaunchApp("whatsapp"),
                        JarvisAction.Wait(1200),
                        JarvisAction.TapText(name, exact = false),
                        JarvisAction.Wait(700),
                        JarvisAction.TypeText(text),
                        JarvisAction.TapViewDesc("Send"),
                    ),
                    sensitive = true, // message sending -> confirmation gate
                )
            }

        // --- "open X and search for Y, play the first result" (YouTube style) ---
        Regex("(?i)open ([\\w ]+?) and search for (.+)")
            .find(c)?.let { m ->
                val (app, query) = m.destructured
                val pkg = intents.resolveAppName(app)
                val steps = mutableListOf<JarvisAction>(
                    JarvisAction.LaunchApp(pkg),
                    JarvisAction.Wait(1500),
                    JarvisAction.TapViewDesc("Search"),
                    JarvisAction.Wait(500),
                    JarvisAction.TypeText(query),
                )
                when {
                    "youtube" in pkg -> {
                        steps += JarvisAction.TapViewDesc("Search YouTube")
                        steps += JarvisAction.Wait(2500)
                        steps += JarvisAction.Speak("Here are the results for $query.")
                    }
                    "chrome" in pkg || "google" in pkg -> {
                        steps += JarvisAction.SubmitSearch
                        steps += JarvisAction.Wait(2000)
                    }
                    else -> steps += JarvisAction.SubmitSearch
                }
                return TaskPlan(c, steps)
            }

        // --- multi-step chains: "open A, then B, then C" ---
        if (c.contains(",") || Regex("(?i)\\bthen\\b").containsMatchIn(c)) {
            val parts = c.split(Regex(",|(?i)\\bthen\\b")).map { it.trim() }.filter { it.isNotBlank() }
            if (parts.size > 1) {
                val steps = mutableListOf<JarvisAction>()
                parts.forEach { part ->
                    steps += when {
                        Regex("(?i)^open .+").matches(part) ->
                            JarvisAction.LaunchApp(intents.resolveAppName(part.removePrefix("open ").trim()))
                        Regex("(?i)^(go to|open) wi-?fi.*").matches(part) -> {
                            intents.pendingSettings = "wifi"
                            JarvisAction.QueryDevice("open_wifi")
                        }
                        else -> JarvisAction.TapText(part)
                    }
                    steps += JarvisAction.Wait(1000)
                }
                return TaskPlan(c, steps)
            }
        }

        // --- single: "open WhatsApp / YouTube / Settings and go to Wi-Fi" ---
        Regex("(?i)^open ([\\w ]+)$").find(c)?.let { m ->
            val app = m.groupValues[1].trim()
            return TaskPlan(c, listOf(JarvisAction.LaunchApp(intents.resolveAppName(app))))
        }
        Regex("(?i)open settings.*wi-?fi.*").find(c)?.let {
            intents.pendingSettings = "wifi"
            return TaskPlan(c, listOf(
                JarvisAction.QueryDevice("open_wifi"),
                JarvisAction.Speak("Opening Wi-Fi settings."),
            ))
        }

        // --- fallback: try to open it as an app ---
        val appGuess = intents.resolveAppName(c)
        if (appGuess != c.lowercase()) {
            return TaskPlan(c, listOf(JarvisAction.LaunchApp(appGuess)))
        }
        return TaskPlan(c, listOf(JarvisAction.Speak("I don't know how to do that yet.")))
    }
}
