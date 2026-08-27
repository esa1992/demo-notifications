package com.demo.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class RealtimeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var client: BackendClient? = null
    private var loopJob: Job? = null
    private var running = false
    private var baseUrl = ""
    private var customerId = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            startInForeground("Отключение")
            stopSelfSafely()
            return START_NOT_STICKY
        }
        baseUrl = intent?.getStringExtra(EXTRA_URL).orEmpty().ifBlank {
            NotificationHub.state.value.backendUrl
        }
        customerId = intent?.getStringExtra(EXTRA_CUSTOMER).orEmpty().ifBlank {
            NotificationHub.state.value.customerId
        }
        startInForeground("Подключение к серверу…")
        running = true
        loopJob?.cancel()
        loopJob = scope.launch { runLoop() }
        return START_STICKY
    }

    private suspend fun runLoop() {
        var backoffMs = 2_000L
        while (running) {
            NotificationHub.setConnection(ConnectionStatus.Connecting, "Подключение…")
            updateForeground("Подключение…")
            val disconnected = CompletableDeferred<String>()
            val backend = BackendClient(
                onSnapshot = { items -> NotificationHub.applySnapshot(items) },
                onLive = { items ->
                    val fresh = NotificationHub.addLive(items)
                    val language = NotificationHub.state.value.language
                    fresh.forEach { NotificationHelper.show(applicationContext, it, language) }
                },
                onOpen = {
                    backoffMs = 2_000L
                    NotificationHub.setConnection(ConnectionStatus.Connected, "Онлайн")
                    updateForeground("Онлайн, жду уведомления")
                },
                onClosed = { message ->
                    disconnected.complete(message)
                }
            )
            client = backend
            try {
                backend.connect(baseUrl, customerId)
                val reason = disconnected.await()
                if (!running) return
                NotificationHub.setConnection(ConnectionStatus.Connecting, reason)
                updateForeground("Переподключение: $reason")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!running) return
                NotificationHub.setConnection(
                    ConnectionStatus.Connecting,
                    e.message ?: "ошибка"
                )
            } finally {
                backend.disconnect()
            }
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(15_000L)
        }
    }

    private fun startInForeground(text: String) {
        NotificationHelper.ensureChannel(this)
        ServiceCompat.startForeground(
            this,
            NotificationHelper.CONNECTION_NOTIFICATION_ID,
            connectionNotification(text),
            if (Build.VERSION.SDK_INT >= 34) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
        )
    }

    private fun updateForeground(text: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NotificationHelper.CONNECTION_NOTIFICATION_ID, connectionNotification(text))
    }

    private fun connectionNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, RealtimeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, NotificationHelper.CONNECTION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Демо уведомлений")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp)
            .addAction(0, "Отключить", stop)
            .build()
    }

    private fun stopSelfSafely() {
        running = false
        client?.disconnect()
        loopJob?.cancel()
        NotificationHub.setConnection(ConnectionStatus.Disconnected, "Отключено")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        client?.disconnect()
        scope.cancel()
        if (NotificationHub.state.value.connection != ConnectionStatus.Disconnected) {
            NotificationHub.setConnection(ConnectionStatus.Disconnected, "Отключено")
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.demo.notifications.STOP"
        private const val EXTRA_URL = "url"
        private const val EXTRA_CUSTOMER = "customerId"

        fun start(context: Context, url: String, customerId: String) {
            val intent = Intent(context, RealtimeService::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_CUSTOMER, customerId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RealtimeService::class.java))
        }
    }
}
