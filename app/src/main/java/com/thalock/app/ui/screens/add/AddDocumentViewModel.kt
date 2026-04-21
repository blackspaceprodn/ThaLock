package com.thalock.app.ui.screens.add

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thalock.app.data.database.AppDatabase
import com.thalock.app.data.model.*
import com.thalock.app.data.repository.DocumentRepository
import com.thalock.app.util.OcrHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AddDocumentViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DocumentRepository(
        AppDatabase.getInstance(application).documentDao()
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

    fun processOcr(uri: Uri) {
        viewModelScope.launch {
            _isProcessingOcr.value = true
            _ocrError.value = null
            try {
                val text = OcrHelper.extractTextFromUri(getApplication(), uri)
                val parsed = OcrHelper.parseExtractedFields(text)
                autoFillFields(parsed, text)
                _pendingOcrReview.value = true
            } catch (e: Exception) {
                _ocrError.value = "Could not read document: ${e.localizedMessage}"
            } finally {
                _isProcessingOcr.value = false
            }
        }
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
            repository.insertDocument(document)
            onSaved()
        }
    }
}
