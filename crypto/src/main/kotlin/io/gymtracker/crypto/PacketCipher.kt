package io.gymtracker.crypto

import io.gymtracker.domain.event.hexToBytes
import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.SecureRandom

class PacketFormatException(message: String) : IllegalArgumentException(message)

/**
 * Cifrado de paquetes hacia un amigo, estilo HPKE simplificado:
 *
 *   eph      = par X25519 efímero (uno por paquete)
 *   shared   = X25519(eph.priv, destinatario.pub)
 *   key      = HKDF-SHA256(shared, salt = eph.pub || destinatario.pub, info = "gymtracker/packet/v1")
 *   paquete  = 0x01 || eph.pub (32) || nonce (12) || ChaCha20-Poly1305(key, nonce, aad = eph.pub, texto)
 *
 * La autenticidad del contenido la dan las firmas Ed25519 de cada evento, no el cifrado.
 */
object PacketCipher {
    private const val VERSION: Byte = 1
    private const val PUB_SIZE = 32
    private const val NONCE_SIZE = 12
    private const val TAG_BITS = 128
    private val INFO = "gymtracker/packet/v1".encodeToByteArray()

    fun seal(plaintext: ByteArray, recipientBoxPublicKeyHex: String, random: SecureRandom = SecureRandom()): ByteArray {
        val recipient = X25519PublicKeyParameters(recipientBoxPublicKeyHex.hexToBytes(), 0)
        val ephemeral = X25519PrivateKeyParameters(random)
        val ephemeralPub = ephemeral.generatePublicKey().encoded
        val key = deriveKey(ephemeral, recipient, ephemeralPub, recipient.encoded)
        val nonce = ByteArray(NONCE_SIZE).also(random::nextBytes)

        val cipher = ChaCha20Poly1305()
        cipher.init(true, AEADParameters(KeyParameter(key), TAG_BITS, nonce, ephemeralPub))
        val out = ByteArray(cipher.getOutputSize(plaintext.size))
        val n = cipher.processBytes(plaintext, 0, plaintext.size, out, 0)
        cipher.doFinal(out, n)

        return byteArrayOf(VERSION) + ephemeralPub + nonce + out
    }

    fun open(packet: ByteArray, recipientKey: X25519PrivateKeyParameters): ByteArray {
        if (packet.size < 1 + PUB_SIZE + NONCE_SIZE + TAG_BITS / 8) throw PacketFormatException("paquete demasiado corto")
        if (packet[0] != VERSION) throw PacketFormatException("versión de paquete ${packet[0]} no soportada")
        val ephemeralPub = packet.copyOfRange(1, 1 + PUB_SIZE)
        val nonce = packet.copyOfRange(1 + PUB_SIZE, 1 + PUB_SIZE + NONCE_SIZE)
        val ciphertext = packet.copyOfRange(1 + PUB_SIZE + NONCE_SIZE, packet.size)
        val key = deriveKey(recipientKey, X25519PublicKeyParameters(ephemeralPub, 0), ephemeralPub, recipientKey.generatePublicKey().encoded)

        val cipher = ChaCha20Poly1305()
        cipher.init(false, AEADParameters(KeyParameter(key), TAG_BITS, nonce, ephemeralPub))
        val out = ByteArray(cipher.getOutputSize(ciphertext.size))
        val n = cipher.processBytes(ciphertext, 0, ciphertext.size, out, 0)
        try {
            cipher.doFinal(out, n)
        } catch (e: InvalidCipherTextException) {
            throw PacketFormatException("el paquete no es para esta identidad o fue alterado")
        }
        return out
    }

    private fun deriveKey(
        privateKey: X25519PrivateKeyParameters,
        publicKey: X25519PublicKeyParameters,
        ephemeralPub: ByteArray,
        recipientPub: ByteArray,
    ): ByteArray {
        val agreement = X25519Agreement()
        agreement.init(privateKey)
        val shared = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(publicKey, shared, 0)
        return KeyDerivation.hkdf(shared, salt = ephemeralPub + recipientPub, info = INFO, length = 32)
    }
}
