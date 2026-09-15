package com.jarvis.assistant.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.jarvis.assistant.core.conversation.ChatMessage
import com.jarvis.assistant.ui.theme.Cyan

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(nav: NavController, vm: ChatViewModel = hiltViewModel()) {
    val messages by vm.messages.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Jarvis", color = Cyan) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background))
        },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a command…") },
                    shape = RoundedCornerShape(24.dp),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = { vm.toggleVoice() },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (ui.listening) MaterialTheme.colorScheme.error else Cyan)) {
                    Icon(Icons.Default.Mic, "Voice")
                }
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = { vm.send(input); input = "" },
                    modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(messages, key = { it.time }) { msg -> MessageBubble(msg) }
            if (ui.busy && messages.isNotEmpty()) {
                item { Text("Jarvis is working…", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall) }
            }
        }
    }

    // sensitive-action confirmation dialog (requirement #13)
    ui.confirmation?.let { req ->
        AlertDialog(
            onDismissRequest = { vm.resolveConfirmation(false) },
            title = { Text("Confirm action") },
            text = { Text(req.summary) },
            confirmButton = {
                TextButton(onClick = { vm.resolveConfirmation(true) }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = { vm.resolveConfirmation(false) }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    Box(Modifier.fillMaxWidth()) {
        Column(Modifier
            .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
            .widthIn(max = 300.dp)
            .background(
                if (isUser) MaterialTheme.colorScheme.surfaceVariant
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(if (isUser) "YOU" else "JARVIS",
                style = MaterialTheme.typography.labelSmall,
                color = if (isUser) MaterialTheme.colorScheme.onSurfaceVariant else Cyan)
            Text(msg.text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
