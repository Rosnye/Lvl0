package io.gymtracker.sync

import io.gymtracker.crypto.Identity
import io.gymtracker.crypto.MasterSeed
import io.gymtracker.data.security.SecretStore
import javax.inject.Inject
import javax.inject.Singleton

/** Carga la identidad local o la crea la primera vez. La semilla queda sellada con Keystore. */
@Singleton
class IdentityManager @Inject constructor(private val secrets: SecretStore) {
    val identity: Identity by lazy {
        Identity(MasterSeed(secrets.getOrCreate(SecretStore.Names.IDENTITY_SEED) { MasterSeed.random().bytes }))
    }

    /** Semilla en claro, solo para el respaldo cifrado con frase de contraseña (fase 4). */
    fun exportSeed(): MasterSeed = MasterSeed(checkNotNull(secrets.get(SecretStore.Names.IDENTITY_SEED)))
}
