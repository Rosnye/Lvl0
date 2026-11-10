package io.gymtracker.crypto

import io.gymtracker.domain.event.SignatureVerifier
import io.gymtracker.domain.event.Signer
import io.gymtracker.domain.event.hexToBytes
import io.gymtracker.domain.event.toHex
import io.gymtracker.domain.model.AuthorId
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/** Identidad local: firma eventos y abre paquetes dirigidos a este usuario. */
class Identity(seed: MasterSeed) : Signer {
    private val signingKey = KeyDerivation.signingKey(seed)
    private val boxKey = KeyDerivation.boxKey(seed)

    override val authorId: AuthorId = AuthorId(signingKey.generatePublicKey().encoded.toHex())

    /** Clave pública X25519 (hex) que los amigos usan para cifrar paquetes hacia nosotros. */
    val boxPublicKeyHex: String = boxKey.generatePublicKey().encoded.toHex()

    override fun sign(message: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, signingKey)
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    fun open(packet: ByteArray): ByteArray = PacketCipher.open(packet, boxKey)

    /** Tarjeta de contacto firmada, lo que se muestra en el QR al vincular. */
    fun card(nickname: String): FriendCard = FriendCard.create(this, boxPublicKeyHex, nickname)
}

object Ed25519Verifier : SignatureVerifier {
    override fun verify(authorId: AuthorId, message: ByteArray, signature: ByteArray): Boolean {
        if (signature.size != 64) return false
        return runCatching {
            val verifier = Ed25519Signer()
            verifier.init(false, Ed25519PublicKeyParameters(authorId.hex.hexToBytes(), 0))
            verifier.update(message, 0, message.size)
            verifier.verifySignature(signature)
        }.getOrDefault(false)
    }
}
