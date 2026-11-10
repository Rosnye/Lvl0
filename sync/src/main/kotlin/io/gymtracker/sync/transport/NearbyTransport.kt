package io.gymtracker.sync.transport

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.Strategy
import io.gymtracker.domain.model.AuthorId

/**
 * Transporte por proximidad con Nearby Connections (Bluetooth, BLE y Wi-Fi Direct).
 * Fase 3: anuncia y descubre con el SERVICE_ID, autentica el endpoint comparando el
 * AuthorId anunciado con la lista de amigos, y entrega paquetes como Payload de bytes.
 * Depende de Google Play Services (decisión aceptada para la v1).
 */
class NearbyTransport(context: Context) : PacketTransport {
    override val name = "cercano"

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)

    override suspend fun send(friendId: AuthorId, packet: ByteArray): Boolean {
        // TODO(fase 3): startAdvertising/startDiscovery con STRATEGY, requestConnection,
        // verificación del amigo y sendPayload(Payload.fromBytes(packet)).
        return false
    }

    fun stop() {
        client.stopAllEndpoints()
        client.stopAdvertising()
        client.stopDiscovery()
    }

    companion object {
        const val SERVICE_ID = "io.gymtracker.sync.v1"
        val STRATEGY: Strategy = Strategy.P2P_CLUSTER
    }
}
