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
    val title: String, val why: String,
    val granted: () -> Boolean, val fix: (android.content.Context) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(nav: NavController) {
    val context = LocalContext.current
    var refresh by remember { mutableStateOf(0) }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()) { refresh++ }

    val items = remember(refresh) {
        listOf(
            PermItem("Microphone", "Required for wake word, voice commands and mic diagnostics.",
                { ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED },
                { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }),
            PermItem("Accessibility Service", "Lets Jarvis tap, type and navigate inside apps for you.",
                {
                    val enabled = Settings.Secure.getString(context.contentResolver,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                    enabled.contains(context.packageName)
                },
                { it.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }),
            PermItem("Display over other apps", "Shows the floating Jarvis bubble (like Gemini) on any screen.",
                { Settings.canDrawOverlays(context) },
                { it.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${it.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }),
            PermItem("Notifications", "Shows the persistent 'Jarvis is listening' notification.",
                { if (Build.VERSION.SDK_INT >= 33)
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                  else true },
                { if (Build.VERSION.SDK_INT >= 33) micLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                  else it.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                      .putExtra(Settings.EXTRA_APP_PACKAGE, it.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }),
            PermItem("Battery optimization", "Some manufacturers kill background apps. Exempt Jarvis so wake word survives.",
                {
                    val pm = it.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                    pm.isIgnoringBatteryOptimizations(it.packageName)
                },
                { it.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${it.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }),
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
            items.forEach { item ->
                item {
                    val ok = item.granted()
                    Card(colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium)
                                Text(if (ok) "✓ Granted" else "✗ Needed",
                                    color = if (ok) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(item.why, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!ok) {
                                Spacer(Modifier.height(10.dp))
                                Button(onClick = { item.fix(context); refresh++ }) { Text("Grant") }
                            }
                        }
                    }
                }
            }
            item {
                Text("Tip: On Xiaomi/Oppo/Vivo/Samsung also enable 'Allow background activity' " +
                    "and 'Allow autostart' in system App settings (requirement #14 setup guide).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
