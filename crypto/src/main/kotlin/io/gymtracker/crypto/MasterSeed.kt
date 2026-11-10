package io.gymtracker.crypto

import java.security.SecureRandom

/**
 * Semilla maestra de 32 bytes. De ella se derivan la clave de firma (Ed25519) y la de
 * cifrado (X25519). Es el único secreto que hay que respaldar; se guarda sellada con
 * Android Keystore (ver módulo data).
 */
class MasterSeed(val bytes: ByteArray) {
    init {
        require(bytes.size == SIZE) { "La semilla debe tener $SIZE bytes" }
    }

    companion object {
        const val SIZE = 32

        fun random(random: SecureRandom = SecureRandom()): MasterSeed =
            MasterSeed(ByteArray(SIZE).also(random::nextBytes))
    }
}
