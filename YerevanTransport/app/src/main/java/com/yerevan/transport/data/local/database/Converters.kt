package com.yerevan.transport.data.local.database

import androidx.room.TypeConverter
import com.yerevan.transport.data.local.entity.StopType

class Converters {
    @TypeConverter
    fun fromStopType(value: StopType): String = value.name

    @TypeConverter
    fun toStopType(value: String): StopType = StopType.valueOf(value)
}
