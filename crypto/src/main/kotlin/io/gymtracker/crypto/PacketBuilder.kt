package io.gymtracker.crypto

import io.gymtracker.domain.event.EventCodec
import io.gymtracker.domain.event.EventValidator
import io.gymtracker.domain.event.Packet
import io.gymtracker.domain.event.SignedEvent
import io.gymtracker.domain.event.ValidationResult
import io.gymtracker.domain.model.AuthorId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

enum class RejectReason {
    /** No se pudo descifrar: el paquete es para otra persona o se alteró. */
    NOT_FOR_ME,
    /** Descifrado pero ilegible, vacío o demasiado grande. */
    CORRUPT,
    /** Viene de alguien que no es un amigo vinculado. */
    UNKNOWN_SENDER,
    /** Mezcla eventos de varios autores. */
    MULTIPLE_AUTHORS,
    /** Algún evento tiene firma inválida o no es para compartir. */
    INVALID_EVENT,
}

/**
 * Paquete hacia un amigo, independiente del transporte (archivo, Nearby):
 *   texto  = JSON canónico de [Packet] comprimido con gzip
 *   bytes  = PacketCipher.seal(texto, clave de cifrado del amigo)
 */
object PacketBuilder {
    /** Tamaño máximo descomprimido aceptado (protege contra bombas de compresión). */
    private const val MAX_PLAINTEXT = 2 * 1024 * 1024

    sealed interface Opened {
        data class Ok(val author: AuthorId, val events: List<SignedEvent>) : Opened
        data class Error(val reason: RejectReason) : Opened
    }

    fun build(events: List<SignedEvent>, recipientBoxPublicKeyHex: String): ByteArray {
        val json = EventCodec.canonical.encodeToString(Packet.serializer(), Packet(events = events))
        return PacketCipher.seal(gzip(json.encodeToByteArray()), recipientBoxPublicKeyHex)
    }

    fun open(identity: Identity, bytes: ByteArray, isFriend: (AuthorId) -> Boolean): Opened {
        val compressed = try {
            identity.open(bytes)
        } catch (e: PacketFormatException) {
            return Opened.Error(RejectReason.NOT_FOR_ME)
        }
        val plaintext = gunzip(compressed) ?: return Opened.Error(RejectReason.CORRUPT)
        val packet = runCatching { EventCodec.lenient.decodeFromString(Packet.serializer(), plaintext.decodeToString()) }
            .getOrElse { return Opened.Error(RejectReason.CORRUPT) }
        if (packet.events.isEmpty()) return Opened.Error(RejectReason.CORRUPT)
        val author = packet.events.map { it.authorId }.distinct().singleOrNull()
            ?: return Opened.Error(RejectReason.MULTIPLE_AUTHORS)
        if (!isFriend(author)) return Opened.Error(RejectReason.UNKNOWN_SENDER)
        if (packet.events.any { EventValidator.validateIncoming(it, Ed25519Verifier) != ValidationResult.Ok }) {
            return Opened.Error(RejectReason.INVALID_EVENT)
        }
        return Opened.Ok(author, packet.events.sortedBy { it.seq })
    }

    private fun gzip(data: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(data) } }.toByteArray()

    private fun gunzip(data: ByteArray): ByteArray? = runCatching {
        GZIPInputStream(ByteArrayInputStream(data)).use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > MAX_PLAINTEXT) return null
            }
            out.toByteArray()
        }
    }.getOrNull()
}
