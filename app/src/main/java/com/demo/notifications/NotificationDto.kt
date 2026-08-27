package com.demo.notifications

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

val AppJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

@Serializable
data class NotificationDto(
    val notificationType: String = "",
    @Serializable(with = FlexibleLongSerializer::class)
    val customerId: Long = 0,
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String = "",
    val noteRu: String = "",
    val noteKg: String = "",
    val noteEn: String = "",
    val createdAt: String = ""
) {
    fun text(language: Language): String = when (language) {
        Language.RU -> noteRu.ifBlank { noteEn.ifBlank { noteKg } }
        Language.KG -> noteKg.ifBlank { noteRu.ifBlank { noteEn } }
        Language.EN -> noteEn.ifBlank { noteRu.ifBlank { noteKg } }
    }

    fun dedupKey(): String = "$id|$customerId|$createdAt"
}

@Serializable
data class ServerEnvelope(
    val type: String = "",
    val items: List<NotificationDto> = emptyList()
)

enum class Language(val label: String) {
    RU("RU"),
    KG("KG"),
    EN("EN")
}

enum class ConnectionStatus {
    Disconnected,
    Connecting,
    Connected
}

object FlexibleStringSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        val element = jsonDecoder.decodeJsonElement()
        val primitive = element as? JsonPrimitive ?: return ""
        return primitive.content
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

object FlexibleLongSerializer : KSerializer<Long> {
    override val descriptor = PrimitiveSerialDescriptor("FlexibleLong", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Long {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeLong()
        val element = jsonDecoder.decodeJsonElement()
        val primitive = element as? JsonPrimitive ?: return 0L
        return primitive.longOrNull ?: primitive.content.toLongOrNull() ?: 0L
    }

    override fun serialize(encoder: Encoder, value: Long) = encoder.encodeLong(value)
}
