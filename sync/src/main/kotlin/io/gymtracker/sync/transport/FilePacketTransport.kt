package io.gymtracker.sync.transport

import android.content.Context
import android.net.Uri
import io.gymtracker.domain.model.AuthorId
import java.io.File

/**
 * Transporte por archivo: el paquete cifrado se guarda como .gympkg y el usuario lo envía
 * por cualquier mensajero. Funciona sin Google Play Services y sin red propia: el
 * mensajero solo ve bytes cifrados para un amigo concreto.
 */
class FilePacketTransport(private val context: Context) {

    /** Escribe el paquete en la caché (servida por FileProvider) y borra los antiguos. */
    fun writePacket(friendNickname: String, friendId: AuthorId, packet: ByteArray): File {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3600_000L }?.forEach { it.delete() }
        val safeName = friendNickname.filter { it.isLetterOrDigit() }.take(20).ifEmpty { friendId.hex.take(8) }
        val file = File(dir, "puntaje-para-$safeName.$EXTENSION")
        file.writeBytes(packet)
        return file
    }

    /** Lee el archivo recibido; null si no se puede abrir o supera [maxBytes]. */
    fun readPacket(uri: Uri, maxBytes: Int): ByteArray? =
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > maxBytes) return null
            }
            out.toByteArray()
        }

    companion object {
        const val DIR = "outgoing"
        const val EXTENSION = "gympkg"
        const val MIME_TYPE = "application/octet-stream"
    }
}
