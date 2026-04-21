package com.thalock.app.ui.screens.files

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thalock.app.data.database.AppDatabase
import com.thalock.app.data.model.DocumentCategory
import com.thalock.app.data.model.UploadedFile
import com.thalock.app.data.repository.UploadedFileRepository
import com.thalock.app.security.FileCryptoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class FilesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UploadedFileRepository(
        AppDatabase.getInstance(application).uploadedFileDao()
    )

    // Pending upload — we hold the copied file and wait for the user to pick a category.
    private val _pendingUpload = MutableStateFlow<PendingUpload?>(null)
    val pendingUpload: StateFlow<PendingUpload?> = _pendingUpload

    val files: StateFlow<List<UploadedFile>> = repository.getAllFiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groupedFiles: StateFlow<Map<DocumentCategory, List<UploadedFile>>> = files
        .map { list -> list.groupBy { it.category } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Copy the content at [uri] into app-private storage, then surface a pending upload so
     * the UI can ask the user which category to file it under. The file is only persisted to
     * Room once the user confirms via [confirmCategory].
     */
    fun stageUpload(uri: Uri, displayName: String, mimeType: String?) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val resolver = app.contentResolver
            val filesDir = File(app.filesDir, "uploads").apply { mkdirs() }
            val safeName = displayName.ifBlank { "upload_${System.currentTimeMillis()}" }
            // .enc suffix is a cue; the file is AES-GCM encrypted regardless of name.
            val target = File(filesDir, "${System.currentTimeMillis()}_$safeName.enc")

            try {
                var plaintextSize = 0L
                resolver.openInputStream(uri)?.use { input ->
                    // We wrap input with a counting stream so the original size (not the
                    // on-disk ciphertext size, which includes IV + GCM tag overhead) is
                    // what we surface to the user.
                    val counting = object : java.io.FilterInputStream(input) {
                        override fun read(): Int {
                            val b = super.read()
                            if (b >= 0) plaintextSize++
                            return b
                        }
                        override fun read(b: ByteArray, off: Int, len: Int): Int {
                            val n = super.read(b, off, len)
                            if (n > 0) plaintextSize += n
                            return n
                        }
                    }
                    FileCryptoManager.encryptTo(counting, target)
                }
                _pendingUpload.value = PendingUpload(
                    filePath = target.absolutePath,
                    displayName = safeName,
                    mimeType = mimeType,
                    sizeBytes = plaintextSize
                )
            } catch (e: Exception) {
                target.delete()
            }
        }
    }

    fun confirmCategory(category: DocumentCategory) {
        viewModelScope.launch {
            val pending = _pendingUpload.value ?: return@launch
            repository.insertFile(
                UploadedFile(
                    name = pending.displayName,
                    category = category,
                    filePath = pending.filePath,
                    mimeType = pending.mimeType,
                    sizeBytes = pending.sizeBytes
                )
            )
            _pendingUpload.value = null
        }
    }

    fun cancelPendingUpload() {
        val pending = _pendingUpload.value ?: return
        // Discard the staged file since it was never filed.
        File(pending.filePath).delete()
        _pendingUpload.value = null
    }

    fun deleteFile(file: UploadedFile) {
        viewModelScope.launch {
            File(file.filePath).delete()
            repository.deleteFile(file)
        }
    }
}

data class PendingUpload(
    val filePath: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long
)
