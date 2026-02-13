package com.yerevan.transport.di

import android.content.Context
import com.yerevan.transport.data.local.DatabaseProvider
import com.yerevan.transport.data.local.TransportDatabase
import com.yerevan.transport.data.remote.GisDataFetcher
import com.yerevan.transport.data.repository.TransportRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TransportDatabase =
        DatabaseProvider.getDatabase(context)

    @Provides
    @Singleton
    fun provideGisDataFetcher(): GisDataFetcher = GisDataFetcher()

    @Provides
    @Singleton
    fun provideTransportRepository(
        database: TransportDatabase,
        gisDataFetcher: GisDataFetcher
    ): TransportRepository = TransportRepository(database, gisDataFetcher)
}
