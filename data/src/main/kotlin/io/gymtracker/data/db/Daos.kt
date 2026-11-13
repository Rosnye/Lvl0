package io.gymtracker.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(event: EventEntity)

    @Query("SELECT MAX(seq) FROM events WHERE authorId = :authorId")
    suspend fun lastSeq(authorId: String): Long?

    @Query("SELECT * FROM events WHERE authorId = :authorId ORDER BY seq")
    suspend fun all(authorId: String): List<EventEntity>

    @Query("SELECT * FROM events WHERE authorId = :authorId AND seq > :afterSeq ORDER BY seq")
    suspend fun since(authorId: String, afterSeq: Long): List<EventEntity>

    @Query("SELECT * FROM events WHERE authorId = :authorId AND seq > :afterSeq AND visibility = :visibility ORDER BY seq")
    suspend fun since(authorId: String, afterSeq: Long, visibility: String): List<EventEntity>

    @Query("SELECT * FROM events WHERE authorId = :authorId ORDER BY seq")
    fun observe(authorId: String): Flow<List<EventEntity>>

    @Query("SELECT DISTINCT authorId FROM events")
    suspend fun authors(): List<String>
}

@Dao
interface FriendDao {
    @Upsert
    suspend fun upsert(friend: FriendEntity)

    @Delete
    suspend fun delete(friend: FriendEntity)

    @Query("SELECT * FROM friends WHERE authorId = :authorId")
    suspend fun get(authorId: String): FriendEntity?

    @Query("SELECT * FROM friends ORDER BY nickname")
    fun observeAll(): Flow<List<FriendEntity>>

    @Query("SELECT * FROM friends")
    suspend fun all(): List<FriendEntity>

    @Query("UPDATE friends SET lastSyncEpochMs = :at WHERE authorId = :authorId")
    suspend fun markSynced(authorId: String, at: Long)
}
