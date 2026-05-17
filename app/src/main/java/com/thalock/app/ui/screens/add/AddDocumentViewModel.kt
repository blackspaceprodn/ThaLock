package com.thalock.app.ui.screens.add

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thalock.app.data.database.AppDatabase
import com.thalock.app.data.model.*
import com.thalock.app.data.repository.DocumentRepository
import com.thalock.app.data.repository.UploadedFileRepository
import com.thalock.app.security.FileCryptoManager
import com.thalock.app.util.OcrHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class AddDocumentViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DocumentRepository(
        AppDatabase.getInstance(application).documentDao()
    )

    private val uploadedFileRepository = UploadedFileRepository(
        AppDatabase.getInstance(application).uploadedFileDao()
    )

    private val _selectedCountry = MutableStateFlow<Country?>(null)
    val selectedCountry: StateFlow<Country?> = _selectedCountry

    private val _selectedType = MutableStateFlow<DocumentType?>(null)
    val selectedType: StateFlow<DocumentType?> = _selectedType

    private val _fields = MutableStateFlow<List<DocumentField>>(emptyList())
    val fields: StateFlow<List<DocumentField>> = _fields

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title

    private val _isProcessingOcr = MutableStateFlow(false)
    val isProcessingOcr: StateFlow<Boolean> = _isProcessingOcr

    private val _ocrError = MutableStateFlow<String?>(null)
    val ocrError: StateFlow<String?> = _ocrError

    private val _pendingOcrReview = MutableStateFlow(false)
    val pendingOcrReview: StateFlow<Boolean> = _pendingOcrReview

    /**
     * A file the user supplied (camera capture or upload) during this Add
     * Document session. It's encrypted to app-private storage up front so
     * the raw bytes never linger in the cache, and then linked to the
     * saved [Document] on [saveDocument]. If the user discards/cancels,
     * [cancelPendingAttachment] wipes it.
     */
    private val _pendingAttachment = MutableStateFlow<PendingAttachment?>(null)
    val pendingAttachment: StateFlow<PendingAttachment?> = _pendingAttachment

    fun selectCountry(country: Country?) {
        _selectedCountry.value = country
    }

    fun selectDocumentType(type: DocumentType) {
        if (_selectedType.value == type && _fields.value.isNotEmpty()) return
        _selectedType.value = type
        _fields.value = DocumentTemplate.fieldsFor(type)
        if (_title.value.isBlank()) {
            _title.value = type.displayName
        }
    }

    fun setTitle(title: String) {
        _title.value = title
    }

    fun updateField(index: Int, value: String) {
        val current = _fields.value.toMutableList()
        if (index in current.indices) {
            current[index] = current[index].copy(value = value)
            _fields.value = current
        }
    }

    fun addCustomField(label: String, isSensitive: Boolean) {
        val key = label.lowercase().replace(" ", "_")
        val current = _fields.value.toMutableList()
        current.add(DocumentField(key, label, "", isSensitive))
        _fields.value = current
    }

    fun removeField(index: Int) {
        val current = _fields.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _fields.value = current
        }
    }

    /**
     * Handle a file the user picked or captured. If the MIME is recognisable
     * (image or PDF), run OCR + autofill the form. Either way, stage the raw
     * bytes so that — when the user saves the Document — we file them as an
     * [UploadedFile] linked to that document.
     *
     * For PDFs we rasterize each page via [PdfRenderer] and feed the bitmaps
     * to ML Kit; there's no reliable public text-layer API in stock Android,
     * so OCR is the unified extraction path. Other MIMEs (Word, etc.) still
     * skip extraction and fall through to a file-only upload.
     */
    fun processOcr(
        uri: Uri,
        displayName: String? = null,
        mimeType: String? = null,
        source: AttachmentSource = AttachmentSource.UPLOAD
    ) {
        viewModelScope.launch {
            _isProcessingOcr.value = true
            _ocrError.value = null
            try {
                val resolvedMime = mimeType
                    ?: getApplication<Application>().contentResolver.getType(uri)
                val isImage = resolvedMime?.startsWith("image/") == true
                val isPdf = resolvedMime == "application/pdf"

                val extractedText: String? = when {
                    isImage -> OcrHelper.extractTextFromUri(getApplication(), uri)
                    isPdf -> OcrHelper.extractTextFromPdfUri(getApplication(), uri)
                    else -> null
                }

                if (extractedText != null && extractedText.isNotBlank()) {
                    val parsed = OcrHelper.parseExtractedFields(extractedText)
                    autoFillFields(parsed, extractedText)
                }

                stageAttachment(uri, displayName, mimeType, source)

                // Surface the review card whenever we actually ran OCR and
                // got text back — for both images and PDFs. If extraction
                // produced nothing (empty PDF, blurry scan), skip it so the
                // user isn't asked to review an empty form.
                if (extractedText != null && extractedText.isNotBlank()) {
                    _pendingOcrReview.value = true
                }
            } catch (e: Exception) {
                _ocrError.value = "Could not read document: ${e.localizedMessage}"
            } finally {
                _isProcessingOcr.value = false
            }
        }
    }

    /**
     * Encrypt the bytes behind [uri] into app-private storage and remember
     * the staged file so `saveDocument` can file it as an UploadedFile.
     *
     * If a previous attachment was staged in this session, discard it first —
     * the user only gets one active scan per document.
     */
    private fun stageAttachment(
        uri: Uri,
        displayName: String?,
        mimeType: String?,
        source: AttachmentSource
    ) {
        val app = getApplication<Application>()
        val resolver = app.contentResolver

        // Wipe any previously staged file so we never leave orphaned ciphertext.
        _pendingAttachment.value?.let { File(it.filePath).delete() }

        val resolvedMime = mimeType
            ?: resolver.getType(uri)
            ?: if (source == AttachmentSource.CAMERA) "image/jpeg" else "application/octet-stream"
        val fallbackName = when (source) {
            AttachmentSource.CAMERA -> "scan_${System.currentTimeMillis()}.jpg"
            AttachmentSource.UPLOAD -> "upload_${System.currentTimeMillis()}"
        }
        val name = displayName?.takeIf { it.isNotBlank() } ?: fallbackName

        val dir = File(app.filesDir, "uploads").apply { mkdirs() }
        val target = File(dir, "${System.currentTimeMillis()}_$name.enc")

        try {
            var plaintextSize = 0L
            resolver.openInputStream(uri)?.use { input ->
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
            } ?: return
            _pendingAttachment.value = PendingAttachment(
                filePath = target.absolutePath,
                displayName = name,
                mimeType = resolvedMime,
                sizeBytes = plaintextSize,
                source = source
            )
        } catch (e: Exception) {
            target.delete()
        }
    }

    fun cancelPendingAttachment() {
        _pendingAttachment.value?.let { File(it.filePath).delete() }
        _pendingAttachment.value = null
    }

    private fun autoFillFields(parsed: Map<String, String>, rawText: String) {
        val current = _fields.value.toMutableList()
        for (i in current.indices) {
            val field = current[i]
            val matchedValue = parsed[field.key]
                ?: parsed.entries.find { it.key.contains(field.key) || field.key.contains(it.key) }?.value
            if (matchedValue != null) {
                current[i] = field.copy(value = matchedValue)
            }
        }

        val nameIndex = current.indexOfFirst { it.key == "name" && it.value.isBlank() }
        if (nameIndex >= 0) {
            val lines = rawText.lines().filter { it.isNotBlank() }
            if (lines.isNotEmpty()) {
                val nameLine = lines.firstOrNull { line ->
                    line.all { it.isLetter() || it.isWhitespace() } && line.length > 3
                }
                if (nameLine != null) {
                    current[nameIndex] = current[nameIndex].copy(value = nameLine.trim())
                }
            }
        }

        _fields.value = current
    }

    fun confirmOcrReview() {
        _pendingOcrReview.value = false
    }

    fun discardOcrReview() {
        _pendingOcrReview.value = false
        val type = _selectedType.value ?: return
        _fields.value = DocumentTemplate.fieldsFor(type)
        // If the scan is discarded, also discard the staged raw file — it
        // only exists because we expected to link it to a saved Document.
        cancelPendingAttachment()
    }

    fun saveDocument(onSaved: () -> Unit) {
        viewModelScope.launch {
            val type = _selectedType.value ?: return@launch
            val document = Document(
                country = _selectedCountry.value,
                documentType = type,
                title = _title.value.ifBlank { type.displayName },
                fields = _fields.value
            )
            val newDocId = repository.insertDocument(document)

            // If the user scanned or uploaded a source file while filling
            // this form, file the encrypted blob as an UploadedFile in the
            // Files tab and link it back to the saved Document. That way the
            // raw artifact is recoverable and shareable, not discarded.
            _pendingAttachment.value?.let { pending ->
                uploadedFileRepository.insertFile(
                    UploadedFile(
                        name = pending.displayName,
                        category = DocumentCategory.forDocumentType(type),
                        filePath = pending.filePath,
                        mimeType = pending.mimeType,
                        sizeBytes = pending.sizeBytes,
                        linkedDocumentId = newDocId
                    )
                )
                _pendingAttachment.value = null
            }

            onSaved()
        }
    }

    override fun onCleared() {
        // If the ViewModel is torn down with a staged attachment that was
        // never persisted (user navigated away mid-flow), delete the
        // orphaned ciphertext.
        _pendingAttachment.value?.let { File(it.filePath).delete() }
        super.onCleared()
    }
}

enum class AttachmentSource { CAMERA, UPLOAD }

data class PendingAttachment(
    val filePath: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val source: AttachmentSource
)
