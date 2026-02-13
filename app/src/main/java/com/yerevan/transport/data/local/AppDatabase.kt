package com.yerevan.transport.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.yerevan.transport.data.local.dao.RouteDao
import com.yerevan.transport.data.local.dao.RouteShapePointDao
import com.yerevan.transport.data.local.dao.RouteStopDao
import com.yerevan.transport.data.local.dao.StopDao
import com.yerevan.transport.data.local.dao.SyncMetadataDao
import com.yerevan.transport.data.local.entity.RouteEntity
import com.yerevan.transport.data.local.entity.RouteShapePointEntity
import com.yerevan.transport.data.local.entity.RouteStopEntity
import com.yerevan.transport.data.local.entity.StopEntity
import com.yerevan.transport.data.local.entity.SyncMetadataEntity

@Database(
    entities = [
        StopEntity::class,
        RouteEntity::class,
        RouteShapePointEntity::class,
        RouteStopEntity::class,
        SyncMetadataEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun stopDao(): StopDao
    abstract fun routeDao(): RouteDao
    abstract fun routeShapePointDao(): RouteShapePointDao
    abstract fun routeStopDao(): RouteStopDao
    abstract fun syncMetadataDao(): SyncMetadataDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "yerevan_transport.db"
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
        }
    }
}
