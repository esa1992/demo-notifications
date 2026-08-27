package com.demo.notifications

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class BackendClient(
    private val onSnapshot: (List<NotificationDto>) -> Unit,
    private val onLive: (List<NotificationDto>) -> Unit,
    private val onOpen: () -> Unit,
    private val onClosed: (String) -> Unit
) {
    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var socket: WebSocket? = null
    private val closedOnce = AtomicBoolean(false)

    fun connect(baseUrl: String, customerId: String) {
        disconnect()
        closedOnce.set(false)
        val request = Request.Builder().url(toWebSocketUrl(baseUrl, customerId)).build()
        socket = http.newWebSocket(request, Listener())
    }

    fun disconnect() {
        socket?.close(1000, "client disconnect")
        socket = null
    }

    private inner class Listener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            onOpen()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val envelope = runCatching { AppJson.decodeFromString(ServerEnvelope.serializer(), text) }
                .getOrNull() ?: return
            when (envelope.type) {
                "snapshot" -> onSnapshot(envelope.items)
                "notifications" -> onLive(envelope.items)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(code, reason)
            notifyClosed(reason.ifBlank { "соединение закрыто" })
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            notifyClosed(t.message ?: "ошибка соединения")
        }
    }

    private fun notifyClosed(message: String) {
        if (closedOnce.compareAndSet(false, true)) {
            onClosed(message)
        }
    }

    companion object {
        fun toWebSocketUrl(baseUrl: String, customerId: String): String {
            var url = baseUrl.trim().trimEnd('/')
            url = url.removeSuffix("/notifications").trimEnd('/')
            url = when {
                url.startsWith("https://") -> "wss://" + url.removePrefix("https://")
                url.startsWith("http://") -> "ws://" + url.removePrefix("http://")
                url.startsWith("wss://") || url.startsWith("ws://") -> url
                else -> "wss://$url"
            }
            val ws = if (url.endsWith("/ws")) url else "$url/ws"
            val id = customerId.trim()
            return if (id.isEmpty()) {
                ws
            } else {
                "$ws?customerId=${java.net.URLEncoder.encode(id, "UTF-8")}"
            }
        }
    }
}
