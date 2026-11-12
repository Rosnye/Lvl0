package io.gymtracker.domain.model

import kotlinx.serialization.Serializable

private val HEX_CHARS = "0123456789abcdef".toSet()

/**
 * Identidad de un usuario: su clave pública Ed25519 en hexadecimal minúscula (32 bytes = 64 chars).
 * No existe ningún otro identificador de usuario en el sistema.
 */
@JvmInline
@Serializable
value class AuthorId(val hex: String) {
    init {
        require(hex.length == 64 && hex.all { it in HEX_CHARS }) {
            "AuthorId debe ser una clave pública Ed25519 en hex minúscula (64 caracteres)"
        }
    }

    /** Huella corta legible para mostrar en pantalla y comparar en persona. */
    val fingerprint: String get() = hex.take(16).chunked(4).joinToString("-")
}

@JvmInline @Serializable value class ExerciseId(val value: String)
@JvmInline @Serializable value class SessionId(val value: String)
@JvmInline @Serializable value class GoalId(val value: String)
@JvmInline @Serializable value class RoutineId(val value: String)
