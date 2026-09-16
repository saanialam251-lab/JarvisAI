package com.jarvis.assistant.core.planner

import com.jarvis.assistant.core.executor.IntentController
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 100% local, offline, zero-API-cost planner.
 *
 * Handles, across ANY known app:
 *   - messaging: "message X saying Y", "chat with X on telegram saying Y" —
 *     uses a real in-app CONTACT SEARCH (not just visible recent chats)
 *   - "him"/"her"/"them" after a name was mentioned earlier in the session
 *   - search: "search youtube for lofi", "open instagram and search for cats"
 *   - system controls: "call NAME", "set volume to 40%", "brightness 70%",
 *     "turn speaker on", "open <website> on chrome", "switch off the phone"
 *     (opens the power menu — see SystemController for why it can't go further)
 *   - chains: "open A, then B"
 *   - plain "open X"
 */
@Singleton
class RuleBasedPlanner @Inject constructor(
    private val intents: IntentController,
) {
    private val messageSeparator = "(?:saying|that says|says|:|,)"

    private val messagingVerbs = listOf(
        "send a message to", "write to", "chat with", "message", "text", "dm",
    )
    private val searchVerbs = listOf("search for", "look up", "find", "search")
    private val openPrefixes = listOf("open", "go to", "launch", "start")
    private val pronounNames = setOf("him", "her", "them", "that person", "the person", "them again")

    /** Remembers the last real contact name used, so "chat with her" works after "message Priya". */
    @Volatile private var lastContactName: String? = null

    fun plan(command: String): TaskPlan {
        val c = command.trim()
            .removePrefix("jarvis").removePrefix("Jarvis")
            .trim().removeSuffix(".").trim()
        val lower = c.lowercase()

        // --- device queries ---
        when {
            lower.matches(Regex(".*(battery|charge).*")) ->
                return TaskPlan(c, listOf(JarvisAction.QueryDevice("battery")))
            lower.matches(Regex(".*(storage|space|memory ram).*")) ->
                return TaskPlan(c, listOf(JarvisAction.QueryDevice("storage")))
            lower.matches(Regex(".*(wifi|network|internet).*status.*")) ->
                return TaskPlan(c, listOf(JarvisAction.QueryDevice("network")))
        }

        // --- volume: "set/increase/turn volume to 40%" ---
        Regex("(?i)volume\\D{0,10}(\\d{1,3})\\s*%?").find(c)?.let { m ->
            val pct = m.groupValues[1].toIntOrNull()?.coerceIn(0, 100)
            if (pct != null) return TaskPlan(c, listOf(JarvisAction.SetVolume(pct)))
        }

        // --- brightness: "set/increase brightness to 70%" ---
        Regex("(?i)brightness\\D{0,10}(\\d{1,3})\\s*%?").find(c)?.let { m ->
            val pct = m.groupValues[1].toIntOrNull()?.coerceIn(0, 100)
            if (pct != null) return TaskPlan(c, listOf(JarvisAction.SetBrightness(pct)))
        }

        // --- speakerphone ---
        if (Regex("(?i)(turn on|put on|enable) speaker(phone)?").containsMatchIn(c))
            return TaskPlan(c, listOf(JarvisAction.ToggleSpeaker(true)))
        if (Regex("(?i)(turn off|disable) speaker(phone)?").containsMatchIn(c))
            return TaskPlan(c, listOf(JarvisAction.ToggleSpeaker(false)))

        // --- power menu (NOT a full shutdown — Android doesn't allow that for normal apps) ---
        if (Regex("(?i)(switch off|turn off|shut ?down|power off) (the )?phone").containsMatchIn(c)) {
            return TaskPlan(
                c,
                listOf(
                    JarvisAction.OpenPowerMenu,
                    JarvisAction.Speak("I've opened the power menu. Android doesn't let apps shut the phone down directly, so please tap Power off yourself to finish."),
                ),
            )
        }

        // --- call a contact ---
        Regex("(?i)^call (.+)$").find(c)?.let { m ->
            val rawName = m.groupValues[1].trim()
            val name = resolveContactName(rawName)
            if (name != null) {
                lastContactName = name
                return TaskPlan(c, listOf(JarvisAction.CallContact(name)), sensitive = true)
            }
            return TaskPlan(c, listOf(JarvisAction.Speak(
                "Who do you want to call? Please say their name once and I'll remember it.")))
        }

        // --- open a website by name in Chrome ---
        Regex("(?i)open (.+?) (?:website|site)(?: on chrome)?$").find(c)?.let { m ->
            return TaskPlan(c, listOf(JarvisAction.OpenWebsite(m.groupValues[1].trim())))
        }
        Regex("(?i)go to ([\\w.-]+\\.\\w{2,})").find(c)?.let { m ->
            return TaskPlan(c, listOf(JarvisAction.OpenWebsite(m.groupValues[1].trim())))
        }

        // --- app mentioned anywhere in the sentence ---
        val appHit = intents.findAppMentioned(c)

        if (appHit != null) {
            val (_, pkg) = appHit

            // messaging intent — real contact-list search inside the app, not just
            // whatever happens to be visible in recent chats
            for (verb in messagingVerbs) {
                val regex = Regex(
                    "(?i)\\b${Regex.escape(verb)}\\b\\s+(.+?)\\s*(?:on \\w+\\s*)?$messageSeparator\\s*(.+)"
                )
                regex.find(c)?.let { m ->
                    val rawName = m.groupValues[1].trim().removeSuffix("on").trim()
                    val text = m.groupValues[2].trim()
                    val name = resolveContactName(rawName)
                    if (name != null && text.isNotBlank()) {
                        lastContactName = name
                        return TaskPlan(
                            c,
                            listOf(
                                JarvisAction.LaunchApp(pkg),
                                JarvisAction.Wait(1200),
                                // real contact search, not "hope it's visible in recent chats"
                                JarvisAction.TapViewDesc("Search"),
                                JarvisAction.Wait(500),
                                JarvisAction.TypeText(name),
                                JarvisAction.Wait(900),
                                JarvisAction.TapText(name, exact = false),
                                JarvisAction.Wait(700),
                                JarvisAction.TypeText(text),
                                JarvisAction.TapViewDesc("Send"),
                            ),
                            sensitive = true,
                        )
                    } else if (name == null) {
                        return TaskPlan(c, listOf(JarvisAction.Speak(
                            "Who do you mean? Please say their name once and I'll remember it for next time.")))
                    }
                }
            }

            // search intent
            for (verb in searchVerbs) {
                val regex = Regex("(?i)\\b${Regex.escape(verb)}\\b\\s+(.+)")
                regex.find(c)?.let { m ->
                    val query = m.groupValues[1].trim()
                    if (query.isNotBlank()) {
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
                }
            }
        }

        // --- multi-step chains: "open A, then B, then C" ---
        if (c.contains(",") || Regex("(?i)\\bthen\\b").containsMatchIn(c)) {
            val parts = c.split(Regex(",|(?i)\\bthen\\b")).map { it.trim() }.filter { it.isNotBlank() }
            if (parts.size > 1) {
                val steps = mutableListOf<JarvisAction>()
                parts.forEach { part ->
                    val partAppHit = intents.findAppMentioned(part)
                    steps += when {
                        openPrefixes.any { Regex("(?i)^$it\\b").containsMatchIn(part) } && partAppHit != null ->
                            JarvisAction.LaunchApp(partAppHit.second)
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

        // --- open settings > wifi ---
        Regex("(?i)open settings.*wi-?fi.*").find(c)?.let {
            intents.pendingSettings = "wifi"
            return TaskPlan(c, listOf(
                JarvisAction.QueryDevice("open_wifi"),
                JarvisAction.Speak("Opening Wi-Fi settings."),
            ))
        }

        // --- "open X" / "go to X" / "launch X" — just open the app ---
        if (appHit != null && openPrefixes.any { Regex("(?i)^$it\\b").containsMatchIn(c) }) {
            return TaskPlan(c, listOf(JarvisAction.LaunchApp(appHit.second)))
        }
        if (appHit != null) {
            return TaskPlan(c, listOf(JarvisAction.LaunchApp(appHit.second)))
        }

        // --- fallback: try to open it as an app by exact name ---
        val appGuess = intents.resolveAppName(c)
        if (appGuess != lower) {
            return TaskPlan(c, listOf(JarvisAction.LaunchApp(appGuess)))
        }
        return TaskPlan(c, listOf(JarvisAction.Speak("I don't know how to do that yet.")))
    }

    /** Resolves "him"/"her"/"them" to the last real name used this session; returns null if unresolvable. */
    private fun resolveContactName(raw: String): String? {
        val cleaned = raw.trim().lowercase()
        return if (cleaned in pronounNames) lastContactName else raw.trim()
    }
}
