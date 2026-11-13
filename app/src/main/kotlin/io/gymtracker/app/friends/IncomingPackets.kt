package io.gymtracker.app.friends

import android.net.Uri
import io.gymtracker.crypto.RejectReason
import io.gymtracker.sync.PacketCodec
import io.gymtracker.sync.ReceiveResult
import io.gymtracker.sync.transport.FilePacketTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Procesa archivos de puntaje abiertos desde otras apps o importados a mano. */
@Singleton
class IncomingPackets @Inject constructor(
    private val packetCodec: PacketCodec,
    private val fileTransport: FilePacketTransport,
) {
    private val _result = MutableStateFlow<ReceiveResult?>(null)
    val result: StateFlow<ReceiveResult?> = _result

    suspend fun import(uri: Uri) {
        val bytes = withContext(Dispatchers.IO) {
            runCatching { fileTransport.readPacket(uri, PacketCodec.MAX_PACKET_BYTES) }.getOrNull()
        }
        _result.value = if (bytes == null) ReceiveResult.Rejected(RejectReason.CORRUPT) else packetCodec.receive(bytes)
    }

    fun consume() { _result.value = null }
}
