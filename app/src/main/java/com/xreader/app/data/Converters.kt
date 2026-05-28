package com.xreader.app.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun toBookFormat(value: String): BookFormat = BookFormat.valueOf(value)

    @TypeConverter
    fun fromBookFormat(value: BookFormat): String = value.name

    @TypeConverter
    fun toAnnotationKind(value: String): AnnotationKind = AnnotationKind.valueOf(value)

    @TypeConverter
    fun fromAnnotationKind(value: AnnotationKind): String = value.name
}
