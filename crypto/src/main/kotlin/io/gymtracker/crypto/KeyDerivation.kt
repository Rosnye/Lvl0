package io.gymtracker.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters

internal object KeyDerivation {
    private const val SIGN_INFO = "gymtracker/sign/v1"
    private const val BOX_INFO = "gymtracker/box/v1"

    fun signingKey(seed: MasterSeed): Ed25519PrivateKeyParameters =
        Ed25519PrivateKeyParameters(hkdf(seed.bytes, salt = null, info = SIGN_INFO.encodeToByteArray(), length = 32), 0)

    fun boxKey(seed: MasterSeed): X25519PrivateKeyParameters =
        X25519PrivateKeyParameters(hkdf(seed.bytes, salt = null, info = BOX_INFO.encodeToByteArray(), length = 32), 0)

    fun hkdf(ikm: ByteArray, salt: ByteArray?, info: ByteArray, length: Int): ByteArray {
        val generator = HKDFBytesGenerator(SHA256Digest())
        generator.init(HKDFParameters(ikm, salt, info))
        return ByteArray(length).also { generator.generateBytes(it, 0, length) }
    }
}
