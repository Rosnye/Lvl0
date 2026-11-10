package io.gymtracker.domain.event

import io.gymtracker.domain.model.AuthorId
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/** Parte firmada del evento. Su serialización canónica es lo que se firma. */
@Serializable
data class EventBody(
    val schemaVersion: Int = EVENT_SCHEMA_VERSION,
    val authorId: AuthorId,
    /** Secuencia estrictamente creciente por autor, empieza en 1. */
    val seq: Long,
    val createdAtEpochMs: Long,
    val visibility: Visibility,
    val payload: EventPayload,
)

@Serializable
data class SignedEvent(
    val body: EventBody,
    /** Firma Ed25519 (64 bytes) en hex de EventCodec.canonicalBytes(body). */
    val signatureHex: String,
) {
    val authorId: AuthorId get() = body.authorId
    val seq: Long get() = body.seq
    val payload: EventPayload get() = body.payload
}

/**
 * Serialización de eventos. La forma canónica es JSON producido por kotlinx.serialization
 * con valores por defecto incluidos, nulos explícitos y campos en orden de declaración.
 * Cualquier otra implementación debe reproducirla byte a byte para que las firmas coincidan.
 */
object EventCodec {
    val canonical: Json = Json {
        encodeDefaults = true
        explicitNulls = true
        classDiscriminator = "type"
        prettyPrint = false
    }

    /** Lector tolerante para eventos de versiones futuras con campos adicionales. */
    val lenient: Json = Json(canonical) {
        ignoreUnknownKeys = true
    }

    fun canonicalBytes(body: EventBody): ByteArray =
        canonical.encodeToString(body).encodeToByteArray()

    /** Identificador de contenido: SHA-256 de los bytes canónicos del cuerpo, en hex. */
    fun eventId(body: EventBody): String =
        MessageDigest.getInstance("SHA-256").digest(canonicalBytes(body)).toHex()

    fun encode(event: SignedEvent): String = canonical.encodeToString(event)

    fun decode(json: String): SignedEvent = lenient.decodeFromString(SignedEvent.serializer(), json)
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "Hex de longitud impar" }
    return ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
