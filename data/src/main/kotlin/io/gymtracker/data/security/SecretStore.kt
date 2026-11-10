package io.gymtracker.data.security

import android.content.Context
import android.util.Base64

/** Guarda secretos pequeños (clave de BD, semilla de identidad) sellados con Keystore. */
class SecretStore(context: Context, private val sealer: KeystoreSealer) {
    private val prefs = context.getSharedPreferences("io.gymtracker.secrets", Context.MODE_PRIVATE)

    fun get(name: String): ByteArray? =
        prefs.getString(name, null)?.let { sealer.unseal(Base64.decode(it, Base64.NO_WRAP)) }

    fun put(name: String, value: ByteArray) {
        prefs.edit().putString(name, Base64.encodeToString(sealer.seal(value), Base64.NO_WRAP)).apply()
    }

    @Synchronized
    fun getOrCreate(name: String, create: () -> ByteArray): ByteArray =
        get(name) ?: create().also { put(name, it) }

    object Names {
        const val DATABASE_KEY = "database_key"
        const val IDENTITY_SEED = "identity_seed"
    }
}
