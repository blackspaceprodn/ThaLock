package com.thalock.app.data.dao

import androidx.room.*
import com.thalock.app.data.model.Document
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun getAllDocuments(): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: Long): Document?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: Document): Long

    @Update
    suspend fun updateDocument(document: Document)

    @Delete
    suspend fun deleteDocument(document: Document)

    @Query("SELECT * FROM documents WHERE title LIKE '%' || :query || '%'")
    fun searchDocuments(query: String): Flow<List<Document>>

    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun getAllDocumentsSync(): List<Document>

    @Query("SELECT * FROM documents WHERE id = :id")
    fun getDocumentByIdSync(id: Long): Document?
}
