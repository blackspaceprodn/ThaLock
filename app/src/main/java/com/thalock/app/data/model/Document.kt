package com.thalock.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.thalock.app.data.database.Converters

@Entity(tableName = "documents")
@TypeConverters(Converters::class)
data class Document(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val country: Country? = null,
    val documentType: DocumentType,
    val title: String,
    val fields: List<DocumentField>,
    val imagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
