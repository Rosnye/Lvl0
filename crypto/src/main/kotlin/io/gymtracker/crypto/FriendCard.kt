package io.gymtracker.crypto

import io.gymtracker.domain.event.Signer
import io.gymtracker.domain.event.hexToBytes
import io.gymtracker.domain.event.toHex
import io.gymtracker.domain.model.AuthorId
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Tarjeta de contacto que viaja en el QR de vinculación. La firma ata la clave de cifrado
 * y el apodo a la identidad (clave de firma), así nadie puede sustituir la clave de cifrado.
 */
@Serializable
data class FriendCard(
    val version: Int = 1,
    val authorId: AuthorId,
    val boxPublicKeyHex: String,
    val nickname: String,
    val signatureHex: String,
) {
    fun isValid(): Boolean =
        boxPublicKeyHex.length == 64 &&
            runCatching { Ed25519Verifier.verify(authorId, signedBytes(authorId, boxPublicKeyHex, nickname), signatureHex.hexToBytes()) }
                .getOrDefault(false)

    fun encode(): String = json.encodeToString(this)

    companion object {
        private val json = Json { encodeDefaults = true }

        fun decode(text: String): FriendCard? = runCatching { json.decodeFromString<FriendCard>(text) }.getOrNull()?.takeIf { it.isValid() }

        internal fun create(signer: Signer, boxPublicKeyHex: String, nickname: String): FriendCard {
            val signature = signer.sign(signedBytes(signer.authorId, boxPublicKeyHex, nickname))
            return FriendCard(authorId = signer.authorId, boxPublicKeyHex = boxPublicKeyHex, nickname = nickname, signatureHex = signature.toHex())
        }

        private fun signedBytes(authorId: AuthorId, boxPublicKeyHex: String, nickname: String): ByteArray =
            "gymtracker/card/v1\n${authorId.hex}\n$boxPublicKeyHex\n$nickname".encodeToByteArray()
    }
}
