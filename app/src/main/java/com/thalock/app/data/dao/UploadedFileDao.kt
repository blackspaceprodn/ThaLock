package com.thalock.app.data.dao

import androidx.room.*
import com.thalock.app.data.model.UploadedFile
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadedFileDao {

    @Query("SELECT * FROM uploaded_files ORDER BY createdAt DESC")
    fun getAllFiles(): Flow<List<UploadedFile>>

    @Query("SELECT * FROM uploaded_files WHERE id = :id")
    suspend fun getFileById(id: Long): UploadedFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: UploadedFile): Long

    @Delete
    suspend fun deleteFile(file: UploadedFile)
}
