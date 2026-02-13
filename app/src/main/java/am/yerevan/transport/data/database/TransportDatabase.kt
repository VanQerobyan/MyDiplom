package am.yerevan.transport.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import am.yerevan.transport.data.model.Route
import am.yerevan.transport.data.model.RouteStop
import am.yerevan.transport.data.model.Stop

@Database(
    entities = [Stop::class, Route::class, RouteStop::class],
    version = 1,
    exportSchema = false
)
abstract class TransportDatabase : RoomDatabase() {
    abstract fun stopDao(): StopDao
    abstract fun routeDao(): RouteDao
    abstract fun routeStopDao(): RouteStopDao

    companion object {
        @Volatile
        private var INSTANCE: TransportDatabase? = null

        fun getDatabase(context: Context): TransportDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TransportDatabase::class.java,
                    "transport.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
