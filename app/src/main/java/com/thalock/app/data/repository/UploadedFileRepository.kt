package com.thalock.app.data.repository

import com.thalock.app.data.dao.UploadedFileDao
import com.thalock.app.data.model.UploadedFile
import kotlinx.coroutines.flow.Flow

class UploadedFileRepository(private val dao: UploadedFileDao) {

    fun getAllFiles(): Flow<List<UploadedFile>> = dao.getAllFiles()

    suspend fun getFileById(id: Long): UploadedFile? = dao.getFileById(id)

    suspend fun insertFile(file: UploadedFile): Long = dao.insertFile(file)

    suspend fun deleteFile(file: UploadedFile) = dao.deleteFile(file)
}
