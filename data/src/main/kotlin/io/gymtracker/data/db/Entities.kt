package io.gymtracker.data.db

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

/** Amigo vinculado: su identidad, su clave de cifrado y hasta qué seq propia ya le enviamos. */
@Entity(tableName = "friends")
data class FriendEntity(
    @PrimaryKey val authorId: String,
    val boxPublicKeyHex: String,
    val nickname: String,
    val addedAtEpochMs: Long,
    val lastSentSeq: Long = 0,
)
