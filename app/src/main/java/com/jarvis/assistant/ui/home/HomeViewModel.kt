package com.jarvis.assistant.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jarvis.assistant.core.conversation.ConversationManager
import com.jarvis.assistant.data.monitors.BatteryMonitor
import com.jarvis.assistant.data.monitors.DeviceMonitor
import com.jarvis.assistant.data.monitors.NetworkMonitor
import com.jarvis.assistant.data.prefs.JarvisPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val batteryPercent: Int = -1,
    val charging: Boolean = false,
    val storageFree: Long = -1,
    val wakeWord: Boolean = true,
    val micOn: Boolean = true,
    val networkType: String = "—",
    val recentCommands: List<String> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    battery: BatteryMonitor,
    device: DeviceMonitor,
    network: NetworkMonitor,
    prefs: JarvisPrefs,
    conversation: ConversationManager,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        battery.state, device.state, network.state, prefs.settings, conversation.messages,
    ) { b, d, n, s, msgs ->
        HomeUiState(
            batteryPercent = b?.percent ?: -1,
            charging = b?.charging ?: false,
            storageFree = d?.availStorageGb ?: -1,
            wakeWord = s.wakeWordEnabled,
            micOn = s.backgroundListening,
            networkType = n.type,
            recentCommands = msgs.filter { it.role == "user" }.takeLast(5).map { it.text },
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    init { viewModelScope.launch { conversation.load() } }
}
