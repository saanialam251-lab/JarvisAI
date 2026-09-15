package com.jarvis.assistant.core.executor

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntentController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** set by the planner when a command needs a settings page */
    var pendingSettings: String? = null

    private val knownApps = mapOf(
        "whatsapp" to "com.whatsapp",
        "youtube" to "com.google.android.youtube",
        "chrome" to "com.android.chrome",
        "browser" to "com.android.chrome",
        "instagram" to "com.instagram.android",
        "gmail" to "com.google.android.gmail",
        "email" to "com.google.android.gmail",
        "settings" to "com.android.settings",
        "google" to "com.google.android.googlequicksearchbox",
        "maps" to "com.google.android.apps.maps",
        "spotify" to "com.spotify.music",
        "facebook" to "com.facebook.katana",
        "twitter" to "com.twitter.android",
        "x" to "com.twitter.android",
        "camera" to "com.android.camera2",
        "calculator" to "com.google.android.calculator",
        "clock" to "com.google.android.deskclock",
        "photos" to "com.google.android.apps.photos",
        "play store" to "com.android.vending",
        "contacts" to "com.google.android.contacts",
        "phone" to "com.google.android.dialer",
        "messages" to "com.google.android.apps.messaging",
      "phonepay" to "com.google.android.apps.phonepay" ,
      
    )

    fun resolveAppName(name: String): String =
        knownApps[name.lowercase().trim()] ?: name.lowercase().trim()

    fun launchApp(appNameOrPackage: String): Boolean {
        val pkg = resolveAppName(appNameOrPackage)
        val i = context.packageManager.getLaunchIntentForPackage(pkg)
        i?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (i != null) { context.startActivity(i); true } else false
    }

    fun openWifiSettings() {
        context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openDeepLink(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun searchYouTube(query: String) =
        openDeepLink("https://www.youtube.com/results?search_query=" + Uri.encode(query))

    fun searchChrome(query: String) =
        openDeepLink("https://www.google.com/search?q=" + Uri.encode(query))
}
