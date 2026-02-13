package com.yerevan.transport.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.yerevan.transport.data.local.dao.RouteDao
import com.yerevan.transport.data.local.dao.RouteStopDao
import com.yerevan.transport.data.local.dao.StopDao
import com.yerevan.transport.data.local.entity.RouteEntity
import com.yerevan.transport.data.local.entity.RouteStopEntity
import com.yerevan.transport.data.local.entity.StopEntity

@Database(
    entities = [StopEntity::class, RouteEntity::class, RouteStopEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TransportDatabase : RoomDatabase() {
    abstract fun stopDao(): StopDao
    abstract fun routeDao(): RouteDao
    abstract fun routeStopDao(): RouteStopDao
}

object DatabaseProvider {
    @Volatile
    private var INSTANCE: TransportDatabase? = null

    fun getDatabase(context: Context): TransportDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                TransportDatabase::class.java,
                "yerevan_transport_db"
            ).build()
            INSTANCE = instance
            instance
        }
    }
}
