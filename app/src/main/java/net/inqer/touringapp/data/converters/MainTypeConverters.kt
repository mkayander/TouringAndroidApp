package net.inqer.touringapp.data.converters

import androidx.room.TypeConverter
import java.util.*

class MainTypeConverters {
    @TypeConverter
    fun toDate(dateLong: Long?): Date? {
        return dateLong?.let { Date(it) }
    }

    @TypeConverter
    fun fromDate(date: Date?): Long? {
        return date?.time
    }
}