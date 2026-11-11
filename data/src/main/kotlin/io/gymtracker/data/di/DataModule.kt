package io.gymtracker.data.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.gymtracker.data.db.AppDatabase
import io.gymtracker.data.db.EventDao
import io.gymtracker.data.db.FriendDao
import io.gymtracker.data.db.RoomEventLog
import io.gymtracker.data.draft.WorkoutDraftStore
import io.gymtracker.data.security.KeystoreSealer
import io.gymtracker.data.security.SecretStore
import io.gymtracker.domain.event.EventLog
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.SecureRandom
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun keystoreSealer(): KeystoreSealer = KeystoreSealer()

    @Provides
    @Singleton
    fun secretStore(@ApplicationContext context: Context, sealer: KeystoreSealer): SecretStore =
        SecretStore(context, sealer)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context, secrets: SecretStore): AppDatabase {
        System.loadLibrary("sqlcipher")
        val key = secrets.getOrCreate(SecretStore.Names.DATABASE_KEY) { ByteArray(32).also(SecureRandom()::nextBytes) }
        return Room.databaseBuilder(context, AppDatabase::class.java, "gymtracker.db")
            .openHelperFactory(SupportOpenHelperFactory(key))
            .build()
    }

    @Provides fun eventDao(db: AppDatabase): EventDao = db.eventDao()
    @Provides fun friendDao(db: AppDatabase): FriendDao = db.friendDao()

    @Provides
    @Singleton
    fun eventLog(dao: EventDao): EventLog = RoomEventLog(dao)

    @Provides
    @Singleton
    fun workoutDraftStore(@ApplicationContext context: Context): WorkoutDraftStore = WorkoutDraftStore(context)
}
