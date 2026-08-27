package com.demo.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import java.util.concurrent.atomic.AtomicInteger

object NotificationHelper {
    const val INCOMING_CHANNEL_ID = "incoming_notifications"
    const val CONNECTION_CHANNEL_ID = "connection_status"
    const val CONNECTION_NOTIFICATION_ID = 1001
    private val nextId = AtomicInteger(1)

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                INCOMING_CHANNEL_ID,
                "Входящие уведомления",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Сообщения от сервиса уведомлений" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CONNECTION_CHANNEL_ID,
                "Подключение",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Статус соединения с сервером" }
        )
    }

    fun show(context: Context, item: NotificationDto, language: Language) {
        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val text = item.text(language).ifBlank { "Новое уведомление" }
        val notification = NotificationCompat.Builder(context, INCOMING_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(item.notificationType.ifBlank { "Уведомление" })
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(nextId.getAndIncrement(), notification)
    }
}
