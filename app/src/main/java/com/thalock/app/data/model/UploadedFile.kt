package com.thalock.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A file the user has uploaded to ThaLock (e.g. a scan or document image kept for
 * reference). Every upload is tagged with a [DocumentCategory] at the moment it's
 * saved — we always ask the user which category it belongs to.
 */
@Entity(tableName = "uploaded_files")
data class UploadedFile(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val category: DocumentCategory,
    val filePath: String,
    val mimeType: String? = null,
    val sizeBytes: Long = 0,
    val linkedDocumentId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
