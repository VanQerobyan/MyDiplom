# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.yerevan.transport.data.remote.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepattributes EnclosingMethod
