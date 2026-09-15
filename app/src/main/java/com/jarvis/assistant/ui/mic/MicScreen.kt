package com.jarvis.assistant.ui.mic

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.jarvis.assistant.data.monitors.MicDiagnostics
import com.jarvis.assistant.data.monitors.MicReport
import com.jarvis.assistant.ui.theme.Cyan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MicViewModel @Inject constructor(
    private val diag: MicDiagnostics,
) : ViewModel() {
    private val _report = MutableStateFlow<MicReport?>(null)
    val report = _report
    var testing by mutableStateOf(false); private set
    var recording by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null); private set

    fun runTest() {
        viewModelScope.launch {
            testing = true
            _report.value = diag.testMicrophone(2500)
            testing = false
        }
    }

    fun recordAndPlay(context: android.content.Context) {
        if (recording) return
        viewModelScope.launch {
            recording = true
            message = "Recording 5 seconds… speak now"
            val out = File(context.cacheDir, "jarvis_mic_test.wav")
            val r = diag.recordSample(5, out)
            recording = false
            message = if (r.isSuccess) { diag.playSample(out) { message = "Playback finished" }; "Playing back…" }
                      else "Recording failed: ${r.exceptionOrNull()?.message}"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MicScreen(nav: NavController, vm: MicViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val report by vm.report.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Microphone Diagnostics", color = Cyan) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp)) {
            item {
                Button(onClick = { vm.runTest() }, enabled = !vm.testing,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(if (vm.testing) "Testing…" else "Run microphone test")
                }
            }
            item {
                OutlinedButton(onClick = { vm.recordAndPlay(context) }, enabled = !vm.recording,
                    modifier = Modifier.fillMaxWidth()) {
                    Text(if (vm.recording) "Recording…" else "Record 5s sample & play it back")
                }
            }
            vm.message?.let { item { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } }

            report?.let { r ->
                item { RowItem("Microphone", if (r.micAvailable) "Working" else "Unavailable") }
                item { RowItem("Permission", if (r.permissionGranted) "Granted" else "Denied") }
                item { RowItem("Input detected", if (r.inputDetected) "Yes" else "No") }
                item { RowItem("Audio level", "%.1f dB".format(r.peakLevelDb)) }
                item { RowItem("Audio source", if (r.audioSourceOk) "Available" else "Unavailable") }
                item { RowItem("Supported sample rates",
                    if (r.supportedRates.isEmpty()) "Not available on this device"
                    else r.supportedRates.joinToString(", ") + " Hz") }
                item {
                    Text("Possible issue: " + (r.issue ?: "None detected"),
                        color = if (r.issue == null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error)
                }
                item {
                    Text("Software can detect symptoms of a failing microphone, but not every physical hardware fault.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun RowItem(label: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value)
        }
    }
}
