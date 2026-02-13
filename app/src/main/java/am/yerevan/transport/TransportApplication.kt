package am.yerevan.transport

import android.app.Application
import android.util.Log

/**
 * Application class for Yerevan Transport app
 */
class TransportApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("TransportApp", "Application started")
    }
}
