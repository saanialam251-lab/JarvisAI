package com.jarvis.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jarvis.assistant.MainActivity
import com.jarvis.assistant.R
import com.jarvis.assistant.core.command.CommandProcessor
import com.jarvis.assistant.core.voice.VoskWakeWordEngine
import com.jarvis.assistant.data.prefs.JarvisPrefs
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service keeping the microphone alive for the wake word,
 * with a persistent notification, as required for
 * foregroundServiceType="microphone" on Android 10+.
 */
@AndroidEntryPoint
class WakeWordService : Service() {

    companion object {
        const val ACTION_START = "com.jarvis.assistant.action.START"
        const val ACTION_STOP = "com.jarvis.assistant.action.STOP"
        const val ACTION_COMMAND_NOW = "com.jarvis.assistant.action.COMMAND_NOW"
        private const val CHANNEL_ID = "jarvis_wake"
        private const val NOTIF_ID = 1001

        fun startIntent(context: Context) =
            Intent(context, WakeWordService::class.java).setAction(ACTION_START)

        fun stopIntent(context: Context) =
            Intent(context, WakeWordService::class.java).setAction(ACTION_STOP)
    }

    @Inject lateinit var prefs: JarvisPrefs
    @Inject lateinit var processor: CommandProcessor

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeEngine: VoskWakeWordEngine? = null
    @Volatile private var busy = false

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundCompat("Starting Jarvis…")
    }

    private fun startForegroundCompat(text: String) {
        val notif = buildNotification(text)
        val micGranted = ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        try {
            if (Build.VERSION.SDK_INT >= 29 && micGranted) {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIF_ID, notif)
            }
        } catch (e: Exception) {
            // Can't legally run as a microphone foreground service without the
            // permission on Android 14 — stop cleanly instead of crashing.
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_COMMAND_NOW -> scope.launch { runCommandSession() }
            else -> scope.launch { startListening() }
        }
        return START_STICKY // recover when the system kills us
    }

    private suspend fun startListening() {
        val s = prefs.settings.first()
        if (!s.wakeWordEnabled) { updateNotification("Wake word is OFF (Settings)"); return }
        val micGranted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        if (!micGranted) { updateNotification("Microphone permission needed"); return }

        wakeEngine?.stop()
        wakeEngine = VoskWakeWordEngine(this, "jarvis") {
            scope.launch { runCommandSession() }
        }
        wakeEngine?.start()
        updateNotification(if (wakeEngine != null) "Listening for \"Jarvis\"" else "Say the command in the app (model downloading?)")
    }

    private suspend fun runCommandSession() {
        if (busy) return
        busy = true
        try {
            updateNotification("Jarvis is listening to your command…")
            processor.processFromVoice(
                onListening = { updateNotification("Listening…") },
                onThinking = { updateNotification("Thinking…") },
                onExecuting = { step -> updateNotification(step) },
            )
        } finally {
            busy = false
            updateNotification("Listening for \"Jarvis\"")
            wakeEngine?.resume()
        }
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Jarvis wake word", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Jarvis AI")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        wakeEngine?.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
