package com.jarvis.assistant.ui.charging

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.jarvis.assistant.data.monitors.BatteryMonitor
import com.jarvis.assistant.data.monitors.BatteryInfo
import com.jarvis.assistant.ui.theme.Cyan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChargingViewModel @Inject constructor(
    private val battery: BatteryMonitor,
) : ViewModel() {
    val state = battery.state
    init { viewModelScope.launch { battery.refreshOnce() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChargingScreen(nav: NavController, vm: ChargingViewModel = hiltViewModel()) {
    val b by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Charging Monitor", color = Cyan) },
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp)) {
            item { BigPercent(b) }
            b?.let { info ->
                item { RowItem("Status", info.status) }
                item { RowItem("Charger", info.plugged) }
                item { RowItem("Health", info.health) }
                item { RowItem("Battery temperature",
                    if (info.temperatureC >= 0) "%.1f °C".format(info.temperatureC)
                    else "Not available on this device") }
                item { RowItem("Voltage",
                    if (info.voltageMv > 0) "${info.voltageMv} mV" else "Not available on this device") }
                item { RowItem("Charging current",
                    info.chargeCurrentMa?.let { "$it mA" } ?: "Not available on this device") }
                item { RowItem("Power",
                    if (info.chargeCurrentMa != null && info.voltageMv > 0)
                        "%.2f W".format(info.chargeCurrentMa * info.voltageMv / 1_000_000.0)
                    else "Not available on this device") }
                item { RowItem("Charge counter",
                    info.capacityMah?.let { "%.1f mAh".format(it / 1000.0) }
                        ?: "Not available on this device") }
                item {
                    Text("If Android does not expose a metric, this screen says so — Jarvis never invents values (requirement #9).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } ?: item { Text("Reading battery…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun BigPercent(b: BatteryInfo?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
        Text(if (b != null && b.percent >= 0) "${b.percent}%" else "—",
            style = MaterialTheme.typography.displayLarge, color = Cyan)
        Text(b?.status ?: "", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun RowItem(label: String, value: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
