package com.jarvis.assistant.core.executor

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real system-level actions that don't need the accessibility "tap the
 * screen" approach — volume, brightness, phone calls, speakerphone,
 * opening a website directly in Chrome, and the power menu.
 *
 * IMPORTANT — Android platform limits, not bugs:
 *  - No regular app (this one included) can fully power OFF or reboot a
 *    phone without root or device-owner access. openPowerMenu() opens the
 *    same menu you'd get by long-pressing the power button; the user still
 *    has to tap "Power off" themselves.
 *  - Changing brightness/system-wide settings needs the special
 *    "Modify system settings" permission, granted the same way as the
 *    overlay permission (a settings page, not a normal popup).
 *  - Calling a contact by name needs Contacts read access; actually
 *    placing the call (vs. just opening the dialer pre-filled) needs the
 *    CALL_PHONE permission on top of that.
 */
@Singleton
class SystemController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    // ---------- volume ----------
    fun setVolumePercent(percent: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val stream = AudioManager.STREAM_MUSIC
        val max = am.getStreamMaxVolume(stream)
        val target = (max * percent.coerceIn(0, 100) / 100)
        am.setStreamVolume(stream, target, AudioManager.FLAG_SHOW_UI)
    }

    // ---------- brightness ----------
    fun canWriteSettings(): Boolean = Settings.System.canWrite(context)

    fun requestWriteSettingsPermission() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Returns false if the "Modify system settings" permission isn't granted yet. */
    fun setBrightnessPercent(percent: Int): Boolean {
        if (!canWriteSettings()) return false
        val value = (255 * percent.coerceIn(0, 100) / 100)
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
        return true
    }

    // ---------- speakerphone ----------
    fun setSpeakerOn(on: Boolean) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = on
    }

    // ---------- calling ----------
    private fun hasContactsPermission() = ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun hasCallPermission() = ContextCompat.checkSelfPermission(
        context, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    private fun lookupNumber(name: String): String? {
        if (!hasContactsPermission()) return null
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        context.contentResolver.query(uri, projection, selection, arrayOf("%$name%"), null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (idx >= 0) return c.getString(idx)
            }
        }
        return null
    }

    /** Returns a result describing what actually happened, since calling has several fallback tiers. */
    sealed interface CallResult {
        data object Called : CallResult                 // call placed directly
        data object DialerOpened : CallResult            // number filled in, user must tap call
        data object NoContactsPermission : CallResult
        data object ContactNotFound : CallResult
    }

    fun callContact(name: String): CallResult {
        if (!hasContactsPermission()) return CallResult.NoContactsPermission
        val number = lookupNumber(name) ?: return CallResult.ContactNotFound
        return if (hasCallPermission()) {
            context.startActivity(
                Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            CallResult.Called
        } else {
            context.startActivity(
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            CallResult.DialerOpened
        }
    }

    // ---------- websites ----------
    fun openWebsiteInChrome(input: String) {
        val cleaned = input.trim()
        val url = when {
            cleaned.startsWith("http://") || cleaned.startsWith("https://") -> cleaned
            cleaned.contains(".") -> "https://$cleaned"
            else -> "https://${cleaned.replace(" ", "")}.com"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage("com.android.chrome")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Chrome not installed — fall back to whatever browser is default
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    // ---------- power menu (NOT a real shutdown — see class doc) ----------
    fun openPowerMenu(): Boolean =
        AccessibilityController.service?.performGlobalAction(
            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG) ?: false
}
