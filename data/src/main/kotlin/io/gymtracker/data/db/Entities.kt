package io.gymtracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Un evento firmado tal cual (JSON canónico), indexado por autor y secuencia. */
@Entity(
    tableName = "events",
    primaryKeys = ["authorId", "seq"],
    indices = [Index("authorId", "visibility", "seq")],
)
data class EventEntity(
    val authorId: String,
    val seq: Long,
    val createdAtEpochMs: Long,
    val visibility: String,
    val type: String,
    val eventJson: String,
)

/** Amigo vinculado: su identidad, su clave de cifrado y cuándo recibimos su último paquete. */
@Entity(tableName = "friends")
data class FriendEntity(
    @PrimaryKey val authorId: String,
    val boxPublicKeyHex: String,
    val nickname: String,
    val addedAtEpochMs: Long,
    /** Sin uso desde la v2 (los paquetes llevan siempre una instantánea completa). */
    val lastSentSeq: Long = 0,
    @ColumnInfo(defaultValue = "0")
    val lastSyncEpochMs: Long = 0,
)
