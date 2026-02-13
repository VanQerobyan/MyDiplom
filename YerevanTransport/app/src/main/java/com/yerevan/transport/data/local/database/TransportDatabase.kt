package com.yerevan.transport.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.yerevan.transport.data.local.dao.TransportRouteDao
import com.yerevan.transport.data.local.dao.TransportStopDao
import com.yerevan.transport.data.local.entity.RouteStopCrossRef
import com.yerevan.transport.data.local.entity.TransportRoute
import com.yerevan.transport.data.local.entity.TransportStop

@Database(
    entities = [
        TransportStop::class,
        TransportRoute::class,
        RouteStopCrossRef::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class TransportDatabase : RoomDatabase() {

    abstract fun transportStopDao(): TransportStopDao
    abstract fun transportRouteDao(): TransportRouteDao

    companion object {
        const val DATABASE_NAME = "yerevan_transport.db"

        @Volatile
        private var INSTANCE: TransportDatabase? = null

        fun getInstance(context: Context): TransportDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TransportDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
