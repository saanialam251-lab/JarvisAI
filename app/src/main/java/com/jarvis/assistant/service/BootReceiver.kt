package com.jarvis.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.jarvis.assistant.data.prefs.JarvisPrefs
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

/** Restarts wake-word listening after reboot (requirement #14). */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var prefs: JarvisPrefs

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val start = runBlocking {
            val s = prefs.settings.first()
            s.wakeWordEnabled && s.backgroundListening
        }
        if (start) {
            ContextCompat.startForegroundService(context, WakeWordService.startIntent(context))
        }
    }
}
