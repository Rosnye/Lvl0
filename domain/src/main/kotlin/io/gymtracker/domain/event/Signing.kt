package io.gymtracker.domain.event

import io.gymtracker.domain.model.AuthorId

/** Firma con la clave privada del autor local. La implementación vive en el módulo crypto. */
interface Signer {
    val authorId: AuthorId
    fun sign(message: ByteArray): ByteArray
}

interface SignatureVerifier {
    fun verify(authorId: AuthorId, message: ByteArray, signature: ByteArray): Boolean
}

/** Construye eventos firmados del autor local. */
class EventFactory(
    private val signer: Signer,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun create(seq: Long, payload: EventPayload): SignedEvent {
        require(seq >= 1) { "seq empieza en 1" }
        val body = EventBody(
            authorId = signer.authorId,
            seq = seq,
            createdAtEpochMs = clock(),
            visibility = payload.requiredVisibility,
            payload = payload,
        )
        val signature = signer.sign(EventCodec.canonicalBytes(body))
        return SignedEvent(body, signature.toHex())
    }
}

sealed interface ValidationResult {
    data object Ok : ValidationResult
    data class Rejected(val reason: String) : ValidationResult
}

object EventValidator {
    /** Reglas que debe cumplir cualquier evento, propio o ajeno. */
    fun validate(event: SignedEvent, verifier: SignatureVerifier): ValidationResult {
        val body = event.body
        if (body.schemaVersion > EVENT_SCHEMA_VERSION) {
            return ValidationResult.Rejected("schemaVersion ${body.schemaVersion} no soportada")
        }
        if (body.seq < 1) return ValidationResult.Rejected("seq inválida")
        if (body.visibility != body.payload.requiredVisibility) {
            return ValidationResult.Rejected("visibilidad ${body.visibility} no permitida para ${body.payload.kind}")
        }
        val signature = runCatching { event.signatureHex.hexToBytes() }.getOrNull()
            ?: return ValidationResult.Rejected("firma mal formada")
        if (!verifier.verify(body.authorId, EventCodec.canonicalBytes(body), signature)) {
            return ValidationResult.Rejected("firma inválida")
        }
        return ValidationResult.Ok
    }

    /**
     * Reglas adicionales para eventos recibidos de un amigo: solo se aceptan eventos
     * FRIENDS. Así un receptor jamás almacena datos privados de otra persona, aunque
     * un cliente defectuoso los enviara.
     */
    fun validateIncoming(event: SignedEvent, verifier: SignatureVerifier): ValidationResult {
        val base = validate(event, verifier)
        if (base is ValidationResult.Rejected) return base
        if (event.body.visibility != Visibility.FRIENDS) {
            return ValidationResult.Rejected("un evento recibido debe ser FRIENDS")
        }
        return ValidationResult.Ok
    }
}
