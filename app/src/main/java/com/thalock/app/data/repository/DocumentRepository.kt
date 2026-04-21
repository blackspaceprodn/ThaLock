package com.thalock.app.data.repository

import com.thalock.app.data.dao.DocumentDao
import com.thalock.app.data.model.Document
import kotlinx.coroutines.flow.Flow

class DocumentRepository(private val dao: DocumentDao) {

    fun getAllDocuments(): Flow<List<Document>> = dao.getAllDocuments()

    suspend fun getDocumentById(id: Long): Document? = dao.getDocumentById(id)

    suspend fun insertDocument(document: Document): Long = dao.insertDocument(document)

    suspend fun updateDocument(document: Document) = dao.updateDocument(document)

    suspend fun deleteDocument(document: Document) = dao.deleteDocument(document)

    fun searchDocuments(query: String): Flow<List<Document>> = dao.searchDocuments(query)
}
