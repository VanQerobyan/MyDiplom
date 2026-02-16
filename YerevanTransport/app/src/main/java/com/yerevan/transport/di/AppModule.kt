package com.yerevan.transport.di

import android.content.Context
import com.yerevan.transport.data.local.dao.TransportRouteDao
import com.yerevan.transport.data.local.dao.TransportStopDao
import com.yerevan.transport.data.local.database.DatabaseSeeder
import com.yerevan.transport.data.local.database.TransportDatabase
import com.yerevan.transport.data.remote.GisApiService
import com.yerevan.transport.data.remote.GisDataFetcher
import com.yerevan.transport.util.RouteCalculator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private const val GIS_BASE_URL = "https://gis.yerevan.am/server/rest/services/Hosted/"

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TransportDatabase {
        return TransportDatabase.getInstance(context)
    }

    @Provides
    fun provideTransportStopDao(database: TransportDatabase): TransportStopDao {
        return database.transportStopDao()
    }

    @Provides
    fun provideTransportRouteDao(database: TransportDatabase): TransportRouteDao {
        return database.transportRouteDao()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideGisApiService(client: OkHttpClient): GisApiService {
        return Retrofit.Builder()
            .baseUrl(GIS_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GisApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideRouteCalculator(
        stopDao: TransportStopDao,
        routeDao: TransportRouteDao
    ): RouteCalculator {
        return RouteCalculator(stopDao, routeDao)
    }

    @Provides
    @Singleton
    fun provideDatabaseSeeder(
        @ApplicationContext context: Context,
        database: TransportDatabase
    ): DatabaseSeeder {
        return DatabaseSeeder(context, database)
    }

    @Provides
    @Singleton
    fun provideGisDataFetcher(
        apiService: GisApiService,
        database: TransportDatabase
    ): GisDataFetcher {
        return GisDataFetcher(apiService, database)
    }
}
