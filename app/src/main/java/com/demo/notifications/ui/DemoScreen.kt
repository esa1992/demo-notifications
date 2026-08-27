package com.demo.notifications.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.demo.notifications.ConnectionStatus
import com.demo.notifications.Language
import com.demo.notifications.NotificationDto
import com.demo.notifications.UiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DemoScreen(
    state: UiState,
    onBackendUrl: (String) -> Unit,
    onCustomerId: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onClear: () -> Unit,
    onLanguage: (Language) -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val connected = state.connection == ConnectionStatus.Connected
    val connecting = state.connection == ConnectionStatus.Connecting

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Демо уведомлений") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(16.dp)
        ) {
            StatusCard(state)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.backendUrl,
                onValueChange = onBackendUrl,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !connected && !connecting,
                label = { Text("Адрес сервера Render") },
                placeholder = { Text("https://demo-notifications.onrender.com") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                )
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.customerId,
                onValueChange = onCustomerId,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !connected && !connecting,
                label = { Text("customerId (необязательно)") },
                placeholder = { Text("пусто = все уведомления") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                )
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (connected || connecting) {
                    OutlinedButton(onClick = onDisconnect) { Text("Отключить") }
                } else {
                    Button(
                        onClick = onConnect,
                        enabled = state.backendUrl.isNotBlank()
                    ) { Text("Подключиться") }
                }
            }
            if (state.postUrl.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                EndpointCard(
                    endpoint = state.postUrl,
                    onCopy = {
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard.setPrimaryClip(ClipData.newPlainText("url", state.postUrl))
                        scope.launch { snackbarHostState.showSnackbar("Адрес скопирован") }
                    }
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Language.entries.forEach { language ->
                    FilterChip(
                        selected = state.language == language,
                        onClick = { onLanguage(language) },
                        label = { Text(language.label) }
                    )
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onClear, enabled = state.notifications.isNotEmpty()) {
                    Text("Очистить")
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Получено: ${state.notifications.size}",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            if (state.notifications.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (connected) "Ожидание уведомлений" else "Подключитесь к серверу",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(state.notifications, key = { it.localId }) { received ->
                        NotificationCard(item = received.item, language = state.language)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(state: UiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val color = when (state.connection) {
                ConnectionStatus.Connected -> Color(0xFF2E7D32)
                ConnectionStatus.Connecting -> Color(0xFFEF6C00)
                ConnectionStatus.Disconnected -> Color(0xFFC62828)
            }
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Column {
                Text(
                    text = when (state.connection) {
                        ConnectionStatus.Connected -> "Подключено"
                        ConnectionStatus.Connecting -> "Подключение…"
                        ConnectionStatus.Disconnected -> "Нет соединения"
                    },
                    fontWeight = FontWeight.SemiBold
                )
                val subtitle = state.connectionMessage.ifBlank {
                    "Сервис уведомлений шлёт POST на бэкенд — сообщения приходят сами"
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun EndpointCard(endpoint: String, onCopy: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Сервис должен слать POST сюда:", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = endpoint,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onCopy) {
                Text("Скопировать адрес")
            }
        }
    }
}

@Composable
private fun NotificationCard(item: NotificationDto, language: Language) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = item.notificationType.ifBlank { "NOTIFICATION" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.text(language).ifBlank { "—" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            Text("id: ${item.id}  ·  customerId: ${item.customerId}")
            if (item.createdAt.isNotBlank()) {
                Text(item.createdAt, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
