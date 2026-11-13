package io.gymtracker.sync.transport

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import dagger.hilt.android.qualifiers.ApplicationContext
import io.gymtracker.data.db.FriendDao
import io.gymtracker.data.db.FriendEntity
import io.gymtracker.domain.model.AuthorId
import io.gymtracker.sync.IdentityManager
import io.gymtracker.sync.PacketCodec
import io.gymtracker.sync.ReceiveResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sincronización por proximidad con Nearby Connections (Bluetooth, BLE y Wi-Fi Direct).
 *
 * Ambos teléfonos anuncian y descubren a la vez. El nombre anunciado es el inicio del AuthorId;
 * solo se conecta con amigos vinculados y pide la conexión el de id menor, para no cruzarse.
 * Conectados, cada uno envía su paquete cifrado y procesa el del otro. Un impostor que
 * anunciara el id de un amigo no podría leer el paquete (cifrado para el amigo real) ni colar
 * eventos (firmados con su clave).
 */
@Singleton
class NearbySync @Inject constructor(
    @ApplicationContext context: Context,
    identityManager: IdentityManager,
    private val friendDao: FriendDao,
    private val packetCodec: PacketCodec,
) {
    data class Status(
        val active: Boolean = false,
        /** Apodos de amigos detectados cerca en esta búsqueda. */
        val nearby: Set<String> = emptySet(),
        /** Apodos con los que se completó el intercambio. */
        val synced: Set<String> = emptySet(),
        val failed: Boolean = false,
    )

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val myShortId = identityManager.identity.authorId.hex.take(SHORT_ID)
    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status

    private class Peer(val friend: FriendEntity) {
        var outgoingPayloadId: Long? = null
        var sent = false
        var received = false
    }

    private val peers = HashMap<String, Peer>()
    private var timeout: Job? = null

    fun start() {
        if (_status.value.active) return
        _status.value = Status(active = true)
        client.startAdvertising(myShortId, SERVICE_ID, lifecycle, AdvertisingOptions.Builder().setStrategy(STRATEGY).build())
            .addOnFailureListener { fail() }
        client.startDiscovery(SERVICE_ID, discovery, DiscoveryOptions.Builder().setStrategy(STRATEGY).build())
            .addOnFailureListener { fail() }
        timeout = scope.launch {
            delay(TIMEOUT_MS)
            stop()
        }
    }

    fun stop() {
        timeout?.cancel()
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        peers.clear()
        _status.update { it.copy(active = false) }
    }

    private fun fail() {
        stop()
        _status.update { it.copy(failed = true) }
    }

    private suspend fun friendFor(shortId: String): FriendEntity? =
        if (shortId.length != SHORT_ID) null else friendDao.all().firstOrNull { it.authorId.startsWith(shortId) }

    private val discovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            scope.launch {
                val friend = friendFor(info.endpointName) ?: return@launch
                _status.update { it.copy(nearby = it.nearby + friend.nickname) }
                if (myShortId < info.endpointName) client.requestConnection(myShortId, endpointId, lifecycle)
            }
        }

        override fun onEndpointLost(endpointId: String) = Unit
    }

    private val lifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            scope.launch {
                val friend = friendFor(info.endpointName)
                if (friend == null) {
                    client.rejectConnection(endpointId)
                } else {
                    peers[endpointId] = Peer(friend)
                    client.acceptConnection(endpointId, payloads)
                }
            }
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) scope.launch { sendTo(endpointId) } else peers.remove(endpointId)
        }

        override fun onDisconnected(endpointId: String) {
            peers.remove(endpointId)
        }
    }

    private suspend fun sendTo(endpointId: String) {
        val peer = peers[endpointId] ?: return
        val id = AuthorId(peer.friend.authorId)
        var bytes = packetCodec.buildFor(id) ?: return
        if (bytes.size > ConnectionsClient.MAX_BYTES_DATA_SIZE) bytes = packetCodec.buildFor(id, maxWeeks = 8) ?: return
        val payload = Payload.fromBytes(bytes)
        peer.outgoingPayloadId = payload.id
        client.sendPayload(endpointId, payload)
    }

    private val payloads = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            scope.launch {
                val peer = peers[endpointId] ?: return@launch
                val result = packetCodec.receive(bytes)
                peer.received = true
                if (result is ReceiveResult.Accepted) _status.update { it.copy(synced = it.synced + result.fromNickname) }
                finishIfDone(endpointId)
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val peer = peers[endpointId] ?: return
            if (update.payloadId == peer.outgoingPayloadId && update.status == PayloadTransferUpdate.Status.SUCCESS) {
                peer.sent = true
                finishIfDone(endpointId)
            }
        }
    }

    private fun finishIfDone(endpointId: String) {
        val peer = peers[endpointId] ?: return
        if (peer.sent && peer.received) {
            client.disconnectFromEndpoint(endpointId)
            peers.remove(endpointId)
        }
    }

    companion object {
        const val SERVICE_ID = "io.gymtracker.sync.v1"
        val STRATEGY: Strategy = Strategy.P2P_CLUSTER
        private const val SHORT_ID = 16
        private const val TIMEOUT_MS = 120_000L
    }
}
