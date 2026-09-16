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

    // NOTE: A handful of "system" apps (Camera, Gallery, Files, Clock, Compass,
    // FM Radio, Device Care) ship as different apps on different phone brands
    // (Samsung/Xiaomi/Oppo vs stock/Pixel Android). The IDs below are the
    // Google/Pixel defaults. If one of these says "app not found" on your
    // phone, tell me the brand and I'll add the correct package for it.
    private val knownApps = mapOf(
        // ---------- System / Google ----------
        "phone" to "com.google.android.dialer",
        "dialer" to "com.google.android.dialer",
        "contacts" to "com.google.android.contacts",
        "messages" to "com.google.android.apps.messaging",
        "sms" to "com.google.android.apps.messaging",
        "camera" to "com.android.camera2",
        "photos" to "com.google.android.apps.photos",
        "gallery" to "com.google.android.apps.photos",
        "clock" to "com.google.android.deskclock",
        "calculator" to "com.google.android.calculator",
        "calendar" to "com.google.android.calendar",
        "files" to "com.google.android.apps.nbu.files",
        "recorder" to "com.google.android.apps.recorder",
        "compass" to "com.google.android.apps.maps", // no universal compass app; falls back to Maps
        "fm radio" to "com.google.android.radio",
        "sim toolkit" to "com.android.stk",
        "downloads" to "com.android.providers.downloads.ui",
        "settings" to "com.android.settings",
        "chrome" to "com.android.chrome",
        "browser" to "com.android.chrome",
        "gmail" to "com.google.android.gm",
        "email" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps",
        "youtube" to "com.google.android.youtube",
        "youtube music" to "com.google.android.apps.youtube.music",
        "youtube kids" to "com.google.android.apps.youtube.kids",
        "drive" to "com.google.android.apps.docs",
        "docs" to "com.google.android.apps.docs.editors.docs",
        "sheets" to "com.google.android.apps.docs.editors.sheets",
        "slides" to "com.google.android.apps.docs.editors.slides",
        "meet" to "com.google.android.apps.tachyon",
        "duo" to "com.google.android.apps.tachyon", // Duo merged into Meet
        "play store" to "com.android.vending",
        "play games" to "com.google.android.play.games",
        "play movies" to "com.google.android.videos",
        "gboard" to "com.google.android.inputmethod.latin",
        "google" to "com.google.android.googlequicksearchbox",
        "assistant" to "com.google.android.googlequicksearchbox",
        "gemini" to "com.google.android.apps.bard",
        "lens" to "com.google.ar.lens",
        "translate" to "com.google.android.apps.translate",
        "keep notes" to "com.google.android.keep",
        "keep" to "com.google.android.keep",
        "news" to "com.google.android.apps.magazines",
        "android auto" to "com.google.android.projection.gearhead",
        "digital wellbeing" to "com.google.android.apps.wellbeing",
        "safety" to "com.google.android.apps.safetyhub",

        // ---------- Lite Apps ----------
        "facebook lite" to "com.facebook.lite",
        "instagram lite" to "com.instagram.lite",
        "messenger lite" to "com.facebook.mlite",
        "tiktok lite" to "com.zhiliaoapp.musically.go",
        "spotify lite" to "com.spotify.lite",
        "google go" to "com.google.android.apps.searchlite",
        "maps go" to "com.google.android.apps.mapslite",
        "youtube go" to "com.google.android.apps.youtube.mango",

        // ---------- Useful Apps ----------
        "whatsapp" to "com.whatsapp",
        "telegram" to "org.telegram.messenger",
        "snapchat" to "com.snapchat.android",
        "facebook" to "com.facebook.katana",
        "fb" to "com.facebook.katana",
        "instagram" to "com.instagram.android",
        "insta" to "com.instagram.android",
        "ig" to "com.instagram.android",
        "twitter" to "com.twitter.android",
        "x" to "com.twitter.android",
        "linkedin" to "com.linkedin.android",
        "phonepe" to "com.phonepe.app",
        "google pay" to "com.google.android.apps.nbu.paisa.user",
        "gpay" to "com.google.android.apps.nbu.paisa.user",
        "paytm" to "net.one97.paytm",
        "amazon" to "in.amazon.mShop.android.shopping",
        "flipkart" to "com.flipkart.android",
        "truecaller" to "com.truecaller",
        "mx player" to "com.mxtech.videoplayer.ad",
        "vlc" to "org.videolan.vlc",
        "shareit" to "com.lenovo.anyshare.gps",
        "shareme" to "com.xiaomi.midrop",
        "zomato" to "com.application.zomato",
        "swiggy" to "in.swiggy.android",
        "irctc" to "cris.org.in.prs.ima",
        "ola" to "com.olacabs.customer",
        "uber" to "com.ubercab",
        "spotify" to "com.spotify.music",
        "messenger" to "com.facebook.orca",

        // ---------- Games ----------
        "bgmi" to "com.pubg.imobile",
        "pubg mobile" to "com.tencent.ig",
        "pubg" to "com.tencent.ig",
        "free fire max" to "com.dts.freefiremax",
        "free fire lite" to "com.dts.freefireth",
        "free fire" to "com.dts.freefireth",
        "call of duty mobile" to "com.activision.callofduty.shooter",
        "cod mobile" to "com.activision.callofduty.shooter",
        "cod" to "com.activision.callofduty.shooter",
        "fortnite" to "com.epicgames.fortnite",
        "clash of clans" to "com.supercell.clashofclans",
        "coc" to "com.supercell.clashofclans",
        "clash royale" to "com.supercell.clashroyale",
        "ludo king" to "com.ludo.king",
        "ludo supreme" to "com.zapak.ludosupreme",
        "candy crush" to "com.king.candycrushsaga",
        "subway surfers" to "com.kiloo.subwaysurf",
        "temple run 2" to "com.imangi.templerun2",
        "temple run" to "com.imangi.templerun",
        "hill climb racing" to "com.fingersoft.hillclimb",
        "8 ball pool" to "com.miniclip.eightballpool",
        "carrom pool" to "com.miniclip.carrom",
        "mini militia" to "com.appsomniacs.dp",
        "among us" to "com.innersloth.spacemafia",
        "minecraft" to "com.mojang.minecraftpe",
        "gta vice city" to "com.rockstargames.gtavc",
        "gta san andreas" to "com.rockstargames.gtasa",
    )

    fun resolveAppName(name: String): String =
        knownApps[name.lowercase().trim()] ?: name.lowercase().trim()

    /**
     * Finds the FIRST known app name mentioned anywhere in a free-form sentence
     * (not just at the start), e.g. "go to whatsapp and message John" or
     * "open instagram and search for cats". Longer keys are checked first so
     * multi-word names like "play store" or "clash of clans" match correctly
     * instead of a shorter partial word.
     */
    fun findAppMentioned(sentence: String): Pair<String, String>? {
        val lower = sentence.lowercase()
        return knownApps.entries
            .sortedByDescending { it.key.length }
            .firstOrNull { (name, _) -> Regex("\\b${Regex.escape(name)}\\b").containsMatchIn(lower) }
            ?.let { it.key to it.value }
    }

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
