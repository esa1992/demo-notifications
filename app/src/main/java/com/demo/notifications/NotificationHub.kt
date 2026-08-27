package com.demo.notifications

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

data class ReceivedNotification(
    val localId: Long,
    val item: NotificationDto
)

data class UiState(
    val backendUrl: String = "",
    val customerId: String = "",
    val connection: ConnectionStatus = ConnectionStatus.Disconnected,
    val connectionMessage: String = "",
    val notifications: List<ReceivedNotification> = emptyList(),
    val language: Language = Language.RU
) {
    val postUrl: String
        get() {
            val base = backendUrl.trim().trimEnd('/')
            return if (base.isEmpty()) "" else "$base/notifications"
        }
}

object NotificationHub {
    private const val PREFS = "demo_notifications"
    private val localIds = AtomicLong(0)
    private val seen = LinkedHashSet<String>()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load(context: Context) {
        val prefs = prefs(context)
        val languageName = prefs.getString("language", Language.RU.name) ?: Language.RU.name
        _state.update {
            it.copy(
                backendUrl = prefs.getString("backendUrl", "") ?: "",
                customerId = prefs.getString("customerId", "") ?: "",
                language = runCatching { Language.valueOf(languageName) }.getOrDefault(Language.RU)
            )
        }
    }

    fun setBackendUrl(context: Context, url: String) {
        prefs(context).edit().putString("backendUrl", url).apply()
        _state.update { it.copy(backendUrl = url) }
    }

    fun setCustomerId(context: Context, customerId: String) {
        prefs(context).edit().putString("customerId", customerId).apply()
        _state.update { it.copy(customerId = customerId) }
    }

    fun setLanguage(context: Context, language: Language) {
        prefs(context).edit().putString("language", language.name).apply()
        _state.update { it.copy(language = language) }
    }

    fun setConnection(status: ConnectionStatus, message: String = "") {
        _state.update { it.copy(connection = status, connectionMessage = message) }
    }

    fun applySnapshot(items: List<NotificationDto>) {
        seen.clear()
        val received = items.asReversed().mapNotNull { item ->
            val key = item.dedupKey()
            if (!seen.add(key)) null
            else ReceivedNotification(localIds.incrementAndGet(), item)
        }
        _state.update { it.copy(notifications = received) }
    }

    fun addLive(items: List<NotificationDto>): List<NotificationDto> {
        val fresh = mutableListOf<NotificationDto>()
        val received = mutableListOf<ReceivedNotification>()
        items.asReversed().forEach { item ->
            val key = item.dedupKey()
            if (seen.add(key)) {
                fresh += item
                received += ReceivedNotification(localIds.incrementAndGet(), item)
            }
        }
        if (received.isNotEmpty()) {
            _state.update { it.copy(notifications = received + it.notifications) }
        }
        return fresh
    }

    fun clearList() {
        seen.clear()
        _state.update { it.copy(notifications = emptyList()) }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
