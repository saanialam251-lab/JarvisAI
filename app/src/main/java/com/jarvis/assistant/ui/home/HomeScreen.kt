package com.jarvis.assistant.ui.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.jarvis.assistant.service.OverlayService
import com.jarvis.assistant.service.WakeWordService
import com.jarvis.assistant.ui.theme.Cyan
import java.util.Calendar

@Composable
fun HomeScreen(nav: NavController, vm: HomeViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var coreState by remember { mutableStateOf(CoreState.IDLE) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            // header
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("JARVIS", color = Cyan, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Row {
                    IconButton(onClick = { nav.navigate("settings") }) {
                        Icon(Icons.Default.Settings, "Settings", tint = Cyan) }
                    IconButton(onClick = { nav.navigate("permissions") }) {
                        Icon(Icons.Default.Info, "Permissions", tint = Cyan) }
                }
            }
            Text(greeting(), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))

            // animated AI core (listening/thinking/executing/speaking)
            Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
                JarvisCore(coreState)
            }

            // status cards
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusCard("Battery", if (state.batteryPercent >= 0) "${state.batteryPercent}%" else "—",
                    if (state.charging) "Charging" else "On battery", Modifier.weight(1f)) { nav.navigate("charging") }
                StatusCard("Storage", if (state.storageFree >= 0) "${state.storageFree}GB free" else "—",
                    "Internal", Modifier.weight(1f)) { nav.navigate("permissions") }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusCard("Wake word", if (state.wakeWord) "ON" else "OFF", "Say \"Jarvis\"",
                    Modifier.weight(1f)) { nav.navigate("settings") }
                StatusCard("Network", state.networkType, "Connected", Modifier.weight(1f)) { }
            }

            Spacer(Modifier.height(18.dp))
            // talk button
            Button(
                onClick = {
                    coreState = CoreState.LISTENING
                    context.startService(WakeWordService.startIntent(context))
                    nav.navigate("chat")
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Talk to Jarvis", style = MaterialTheme.typography.titleMedium) }

            Spacer(Modifier.height(18.dp))
            Text("Recent commands", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.recentCommands.size) { i ->
                    Text("• " + state.recentCommands[i],
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { nav.navigate("chat") }
                            .padding(horizontal = 12.dp, vertical = 8.dp))
                }
            }
        }
    }
}

private enum class CoreState { IDLE, LISTENING, THINKING, EXECUTING, SPEAKING, ERROR }

@Composable
private fun JarvisCore(state: CoreState) {
    val infinite = rememberInfiniteTransition(label = "core")
    val pulse by infinite.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse")
    val color = when (state) {
        CoreState.IDLE -> Cyan
        CoreState.LISTENING -> Color(0xFF69F0AE)
        CoreState.THINKING -> Color(0xFFFFD740)
        CoreState.EXECUTING -> Color(0xFF2979FF)
        CoreState.SPEAKING -> Color(0xFFE040FB)
        CoreState.ERROR -> Color(0xFFFF5252)
    }
    Canvas(Modifier.size(150.dp)) {
        val r = size.minDimension / 3f * pulse
        drawCircle(color.copy(alpha = 0.15f), radius = r * 1.5f, center = center)
        drawCircle(color.copy(alpha = 0.35f), radius = r * 1.2f, center = center)
        drawCircle(color, radius = r, center = center)
        drawCircle(Color.White.copy(alpha = 0.8f), radius = r * 0.25f,
            center = Offset(center.x - r * 0.3f, center.y - r * 0.3f))
    }
}

@Composable
private fun StatusCard(title: String, value: String, sub: String,
                       modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = Cyan)
            Text(sub, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (h) {
        in 5..11 -> "Good morning."
        in 12..16 -> "Good afternoon."
        in 17..21 -> "Good evening."
        else -> "Hello."
    }
}
