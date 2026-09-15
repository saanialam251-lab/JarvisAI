package com.jarvis.assistant.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.jarvis.assistant.core.conversation.ConversationManager
import com.jarvis.assistant.core.conversation.TaskLog
import com.jarvis.assistant.core.voice.ModelManager
import com.jarvis.assistant.data.prefs.JarvisPrefs
import com.jarvis.assistant.data.prefs.JarvisSettings
import com.jarvis.assistant.service.OverlayService
import com.jarvis.assistant.ui.theme.Cyan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val prefs: JarvisPrefs,
    private val conversation: ConversationManager,
) : ViewModel() {
    val settings = prefs.settings.stateIn(viewModelScope, SharingStarted.Eagerly, JarvisSettings())
    var modelProgress by mutableStateOf(-1); private set
    var modelReady by mutableStateOf(false); private set

    fun checkModel(ctx: android.content.Context) { modelReady = ModelManager(ctx).isModelReady() }

    fun downloadModel(ctx: android.content.Context) {
        viewModelScope.launch {
            modelProgress = 0
            ModelManager(ctx).downloadModel { p -> modelProgress = p }
            modelReady = ModelManager(ctx).isModelReady()
            modelProgress = -1
        }
    }

    fun update(t: (JarvisSettings) -> JarvisSettings) { viewModelScope.launch { prefs.update(t) } }
    fun clearHistory() { viewModelScope.launch { conversation.clear() } }
    fun clearLogs() { TaskLog.clear() }
    fun showOverlay(ctx: android.content.Context) {
        ctx.startService(Intent(ctx, OverlayService::class.java).setAction(OverlayService.ACTION_SHOW))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val logs by TaskLog.records.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.checkModel(context) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings", color = Cyan) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 16.dp)) {

            item { Section("Voice") }
            item { SwitchRow("Wake word \"Jarvis\"", "Always listen for the wake word", s.wakeWordEnabled, onChange = {
                v -> vm.update { it.copy(wakeWordEnabled = v) } }) }
            item { SwitchRow("Background listening", "Restart after reboot (uses foreground service)", s.backgroundListening) { v ->
                vm.update { it.copy(backgroundListening = v) } } }
            item {
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text("Wake word sensitivity: ${(s.sensitivity * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium)
                    Slider(s.sensitivity, { v -> vm.update { it.copy(sensitivity = v) } })
                }
            }
            item { EditRow("Language", s.language) { v -> vm.update { it.copy(language = v) } } }
            item { EditRow("Assistant voice", s.assistantVoice) { v -> vm.update { it.copy(assistantVoice = v) } } }
            item { SwitchRow("Floating Jarvis bubble", "Like Gemini — drag it, long-press to open chat", s.overlayEnabled) { v ->
                vm.update { it.copy(overlayEnabled = v) } } }
            item {
                TextButton(onClick = { vm.showOverlay(context) }) { Text("Show floating bubble now") }
            }

            item { Section("On-device speech model (free — no API credits)") }
            item {
                if (vm.modelReady) Text("✓ Model ready — speech & wake word work fully offline",
                    color = MaterialTheme.colorScheme.primary)
                else {
                    Column {
                        Text("Model not installed (~40 MB, one-time download)")
                        if (vm.modelProgress >= 0) LinearProgressIndicator(
                            progress = { vm.modelProgress / 100f },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                        Button(onClick = { vm.downloadModel(context) },
                            enabled = vm.modelProgress < 0) { Text("Download model") }
                    }
                }
            }

            item { Section("AI provider") }
            item {
                Row {
                    FilterChip(s.aiProvider == "local", { vm.update { it.copy(aiProvider = "local") } },
                        modifier = Modifier.padding(end = 8.dp)) { Text("Local (free, offline)") }
                    FilterChip(s.aiProvider == "cloud", { vm.update { it.copy(aiProvider = "cloud") } }) {
                        Text("Cloud (optional)") }
                }
            }
            item { EditRow("API key (optional)", s.apiKey, hidden = true) { v -> vm.update { it.copy(apiKey = v) } } }
            item { EditRow("Base URL", s.baseUrl) { v -> vm.update { it.copy(baseUrl = v) } } }
            item { EditRow("Model", s.model) { v -> vm.update { it.copy(model = v) } } }

            item { Section("Safety & privacy") }
            item { SwitchRow("Ask before sensitive actions", "Confirm before sending messages, purchases, deletes…",
                s.askBeforeSensitive) { v -> vm.update { it.copy(askBeforeSensitive = v) } } }
            item { TextButton(onClick = { vm.clearHistory() }) { Text("Delete conversation history") } }
            item { TextButton(onClick = { vm.clearLogs() }) { Text("Clear task logs") } }
            item {
                Text("Speech is processed on-device (Vosk). Cloud AI only receives your typed/spoken command " +
                    "text if you enable it — never microphone audio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            item { Section("Task log") }
            logs.takeLast(15).forEach { rec ->
                item { Text("• ${rec.goal} — ${rec.status}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable private fun Section(t: String) {
    Text(t, color = Cyan, style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
}

@Composable private fun SwitchRow(title: String, sub: String, checked: Boolean,
                                  onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable private fun EditRow(label: String, value: String, hidden: Boolean = false,
                                onDone: (String) -> Unit) {
    var v by remember(value) { mutableStateOf(value) }
    OutlinedTextField(v, { v = it }, Modifier.fillMaxWidth(),
        label = { Text(label) },
        visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (hidden) KeyboardType.Password else KeyboardType.Text),
        trailingIcon = { TextButton(onClick = { onDone(v) }) { Text("Save") } })
}
