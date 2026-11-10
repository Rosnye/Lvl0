package io.gymtracker.sync.transport

import android.content.Context
import android.net.Uri
import io.gymtracker.domain.model.AuthorId
import java.io.File

/**
 * Transporte por archivo: escribe el paquete cifrado en un .gympkg que el usuario envía
 * por cualquier mensajero. Funciona sin Google Play Services y sin red propia.
 * La "entrega" la confirma el usuario al importar en el otro teléfono, por lo que
 * [send] nunca confirma por sí mismo; la app marca el envío al compartir el archivo.
 */
class FilePacketTransport(private val context: Context) : PacketTransport {
    override val name = "archivo"

    /** Crea el archivo en caché y devuelve su ruta; la app lo expone con FileProvider (fase 3). */
    fun writePacket(friendId: AuthorId, packet: ByteArray): File {
        val dir = File(context.cacheDir, "outgoing").apply { mkdirs() }
        val file = File(dir, "puntaje-${friendId.fingerprint}-${System.currentTimeMillis()}.$EXTENSION")
        file.writeBytes(packet)
        return file
    }

    fun readPacket(uri: Uri): ByteArray? =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }

    override suspend fun send(friendId: AuthorId, packet: ByteArray): Boolean = false

    companion object {
        const val EXTENSION = "gympkg"
        const val MIME_TYPE = "application/vnd.gymtracker.packet"
    }
}
