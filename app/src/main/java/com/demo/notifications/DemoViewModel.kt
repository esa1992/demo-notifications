package com.demo.notifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class DemoViewModel(application: Application) : AndroidViewModel(application) {

    val uiState = NotificationHub.state

    init {
        NotificationHub.load(application)
    }

    fun setBackendUrl(url: String) {
        NotificationHub.setBackendUrl(getApplication(), url)
    }

    fun setCustomerId(customerId: String) {
        NotificationHub.setCustomerId(getApplication(), customerId)
    }

    fun setLanguage(language: Language) {
        NotificationHub.setLanguage(getApplication(), language)
    }

    fun connect() {
        val state = uiState.value
        val url = state.backendUrl.trim()
        if (url.isEmpty()) {
            NotificationHub.setConnection(ConnectionStatus.Disconnected, "Укажите адрес сервера")
            return
        }
        RealtimeService.start(getApplication(), url, state.customerId.trim())
    }

    fun disconnect() {
        RealtimeService.stop(getApplication())
        NotificationHub.setConnection(ConnectionStatus.Disconnected, "Отключено")
    }

    fun clear() {
        NotificationHub.clearList()
    }
}
