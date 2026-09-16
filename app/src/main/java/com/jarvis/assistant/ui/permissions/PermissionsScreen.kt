package com.jarvis.assistant.ui.permissions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.jarvis.assistant.ui.theme.Cyan

private data class PermItem(
    val title: String,
    val why: String,
    val granted: (android.content.Context) -> Boolean,
    val fix: (android.content.Context) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(nav: NavController) {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { refresh++ }

    val permItems = remember {
        listOf(
            PermItem("Microphone", "Required for wake word, voice commands and mic diagnostics.",
                { ctx -> ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED },
                { permLauncher.launch(Manifest.permission.RECORD_AUDIO) }),
            PermItem("Accessibility Service", "Lets Jarvis tap, type and navigate inside apps for you.",
                { ctx ->
                    val enabled = Settings.Secure.getString(ctx.contentResolver,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                    enabled.contains(ctx.packageName)
                },
                { ctx -> ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }),
            PermItem("Display over other apps", "Shows the floating Jarvis bubble (like Gemini) on any screen.",
                { ctx -> Settings.canDrawOverlays(ctx) },
                { ctx -> ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }),
            PermItem("Notifications", "Shows the persistent 'Jarvis is listening' notification.",
                { ctx ->
                    if (Build.VERSION.SDK_INT >= 33)
                        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    else true
                },
                { ctx ->
                    if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }),
            PermItem("Battery optimization", "Some manufacturers kill background apps. Exempt Jarvis so wake word survives.",
                { ctx ->
                    val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                    pm.isIgnoringBatteryOptimizations(ctx.packageName)
                },
                { ctx -> ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }),
            PermItem("Contacts", "Lets Jarvis look up a contact's number to call or message them by name.",
                { ctx -> ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED },
                { permLauncher.launch(Manifest.permission.READ_CONTACTS) }),
            PermItem("Phone calls", "Lets Jarvis place a call directly. Without this, it will open the dialer pre-filled and you tap Call yourself.",
                { ctx -> ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED },
                { permLauncher.launch(Manifest.permission.CALL_PHONE) }),
            PermItem("Modify system settings", "Needed for voice commands like 'set brightness to 50%'.",
                { ctx -> Settings.System.canWrite(ctx) },
                { ctx -> ctx.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }),
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Permissions", color = Cyan) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)) {

            items(permItems) { perm ->
                @Suppress("UNUSED_EXPRESSION") refresh   // re-read granted state when it changes
                val ok = perm.granted(context)
                Card(colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(perm.title, style = MaterialTheme.typography.titleMedium)
                            Text(if (ok) "✓ Granted" else "✗ Needed",
                                color = if (ok) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(perm.why, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!ok) {
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { perm.fix(context); refresh++ }) { Text("Grant") }
                        }
                    }
                }
            }

            item {
                Text("Tip: On Xiaomi/Oppo/Vivo/Samsung also enable 'Allow background activity' " +
                    "and 'Allow autostart' in system App settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
