package com.thalock.app.ui.screens.document

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thalock.app.data.database.AppDatabase
import com.thalock.app.data.model.Document
import com.thalock.app.data.model.DocumentField
import com.thalock.app.data.repository.DocumentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DocumentDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DocumentRepository(
        AppDatabase.getInstance(application).documentDao()
    )

    private val _document = MutableStateFlow<Document?>(null)
    val document: StateFlow<Document?> = _document

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing

    private val _editedFields = MutableStateFlow<List<DocumentField>>(emptyList())
    val editedFields: StateFlow<List<DocumentField>> = _editedFields

    fun loadDocument(id: Long) {
        viewModelScope.launch {
            val doc = repository.getDocumentById(id)
            _document.value = doc
            _editedFields.value = doc?.fields ?: emptyList()
        }
    }

    fun startEditing() {
        _isEditing.value = true
        _editedFields.value = _document.value?.fields ?: emptyList()
    }

    fun cancelEditing() {
        _isEditing.value = false
        _editedFields.value = _document.value?.fields ?: emptyList()
    }

    fun updateField(index: Int, newValue: String) {
        val current = _editedFields.value.toMutableList()
        if (index in current.indices) {
            current[index] = current[index].copy(value = newValue)
            _editedFields.value = current
        }
    }

    fun addCustomField(label: String, isSensitive: Boolean) {
        val key = label.lowercase().replace(" ", "_")
        val current = _editedFields.value.toMutableList()
        current.add(DocumentField(key, label, "", isSensitive))
        _editedFields.value = current
    }

    fun removeField(index: Int) {
        val current = _editedFields.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _editedFields.value = current
        }
    }

    fun saveEdits() {
        viewModelScope.launch {
            val doc = _document.value ?: return@launch
            val updated = doc.copy(
                fields = _editedFields.value,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateDocument(updated)
            _document.value = updated
            _isEditing.value = false
        }
    }

    fun deleteDocument(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val doc = _document.value ?: return@launch
            repository.deleteDocument(doc)
            onDeleted()
        }
    }

}
