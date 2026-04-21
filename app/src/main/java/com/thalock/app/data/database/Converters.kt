package com.thalock.app.data.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.thalock.app.data.model.Country
import com.thalock.app.data.model.DocumentCategory
import com.thalock.app.data.model.DocumentField
import com.thalock.app.data.model.DocumentType

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromCountry(country: Country?): String? = country?.code

    @TypeConverter
    fun toCountry(code: String?): Country? =
        if (code.isNullOrBlank()) null else Country.fromCode(code)

    @TypeConverter
    fun fromDocumentType(type: DocumentType): String = type.name

    @TypeConverter
    fun toDocumentType(name: String): DocumentType =
        runCatching { DocumentType.valueOf(name) }.getOrDefault(DocumentType.IDENTITY)

    @TypeConverter
    fun fromDocumentCategory(category: DocumentCategory): String = category.name

    @TypeConverter
    fun toDocumentCategory(name: String): DocumentCategory =
        runCatching { DocumentCategory.valueOf(name) }.getOrDefault(DocumentCategory.IDENTITY)

    @TypeConverter
    fun fromFieldList(fields: List<DocumentField>): String = gson.toJson(fields)

    @TypeConverter
    fun toFieldList(json: String): List<DocumentField> {
        val type = object : TypeToken<List<DocumentField>>() {}.type
        return gson.fromJson(json, type)
    }
}
