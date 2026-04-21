package com.thalock.app.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.thalock.app.data.database.AppDatabase
import com.thalock.app.data.model.Document
import com.thalock.app.data.model.DocumentCategory
import com.thalock.app.data.repository.DocumentRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class VaultFolder(
    val category: DocumentCategory,
    val count: Int,
    val documents: List<Document>
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DocumentRepository(
        AppDatabase.getInstance(application).documentDao()
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val documents: StateFlow<List<Document>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isNotBlank()) repository.searchDocuments(query)
            else repository.getAllDocuments()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Always emit one folder per category — HomeScreen expects a stable 3-slot grid.
    val vaultFolders: StateFlow<List<VaultFolder>> = documents.map { docs ->
        DocumentCategory.entries.map { category ->
            val folderDocs = docs.filter {
                DocumentCategory.forDocumentType(it.documentType) == category
            }
            VaultFolder(category, folderDocs.size, folderDocs)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalDocCount: StateFlow<Int> = documents.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val recentDocuments: StateFlow<List<Document>> = documents.map { docs ->
        docs.sortedByDescending { it.createdAt }.take(5)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun deleteDocument(document: Document) {
        viewModelScope.launch {
            repository.deleteDocument(document)
        }
    }
}
