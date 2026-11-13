package io.gymtracker.sync.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.gymtracker.domain.scoring.ScoringEngine
import io.gymtracker.sync.transport.FilePacketTransport
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {
    @Provides @Singleton fun scoringEngine(): ScoringEngine = ScoringEngine()
    @Provides @Singleton fun fileTransport(@ApplicationContext context: Context) = FilePacketTransport(context)
}
